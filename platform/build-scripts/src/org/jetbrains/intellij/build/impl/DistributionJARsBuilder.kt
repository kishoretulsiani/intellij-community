// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceNegatedIsEmptyWithIsNotEmpty", "PrivatePropertyName")

package org.jetbrains.intellij.build.impl

import com.fasterxml.jackson.jr.ob.JSON
import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.diagnostic.telemetry.useWithScope2
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.io.Compressor
import com.jetbrains.plugin.blockmap.core.BlockMap
import com.jetbrains.plugin.blockmap.core.FileHash
import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.*
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.jetbrains.annotations.TestOnly
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.fus.createStatisticsRecorderBundledMetadataProviderTask
import org.jetbrains.intellij.build.impl.logging.reportBuildProblem
import org.jetbrains.intellij.build.impl.projectStructureMapping.*
import org.jetbrains.intellij.build.io.*
import org.jetbrains.intellij.build.tasks.*
import org.jetbrains.jps.model.artifact.JpsArtifact
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.artifact.elements.JpsLibraryFilesPackagingElement
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsProductionModuleOutputPackagingElement
import org.jetbrains.jps.model.java.JpsTestModuleOutputPackagingElement
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Predicate
import kotlin.streams.asSequence

/**
 * Assembles output of modules to platform JARs (in [BuildPaths.distAllDir]/lib directory),
 * bundled plugins' JARs (in [distAll][BuildPaths.distAllDir]/plugins directory) and zip archives with
 * non-bundled plugins (in [artifacts][BuildPaths.artifactDir]/plugins directory).
 */
internal suspend fun buildDistribution(state: DistributionBuilderState,
                                       context: BuildContext,
                                       isUpdateFromSources: Boolean = false): List<DistributionFileEntry> = coroutineScope {
  validateModuleStructure(state.platform, context)
  createPrebuildSvgIconsJob(context)
  createBuildBrokenPluginListJob(context)

  val flatIdeClassPath = createIdeClassPath(state.platform, context)
  if (context.productProperties.buildDocAuthoringAssets) {
    launch {
      buildAdditionalAuthoringArtifacts(ideClassPath = flatIdeClassPath, context = context)
    }
  }

  val traceContext = Context.current().asContextElement()
  val entries = coroutineScope {
    // must be completed before plugin building
    context.executeStep(spanBuilder("build searchable options index"), BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP) {
      buildSearchableOptions(ideClassPath = flatIdeClassPath, context = context)
    }

    val pluginLayouts = getPluginLayoutsByJpsModuleNames(modules = context.productProperties.productLayout.bundledPluginModules,
                                                         productLayout = context.productProperties.productLayout)
    val antDir = if (context.productProperties.isAntRequired) context.paths.distAllDir.resolve("lib/ant") else null
    val antTargetFile = antDir?.resolve("lib/ant.jar")
    val moduleOutputPatcher = ModuleOutputPatcher()
    val buildPlatformJob: Deferred<List<DistributionFileEntry>> = async(traceContext) {
      spanBuilder("build platform lib").useWithScope2 {
        coroutineScope {
          createStatisticsRecorderBundledMetadataProviderTask(moduleOutputPatcher, context)
          launch {
            spanBuilder("write patched app info").useWithScope {
              val moduleOutDir = context.getModuleOutputDir(context.findRequiredModule("intellij.platform.core"))
              val relativePath = "com/intellij/openapi/application/ApplicationNamesInfo.class"
              val result = injectAppInfo(moduleOutDir.resolve(relativePath), context.applicationInfo.appInfoXml)
              moduleOutputPatcher.patchModuleOutput("intellij.platform.core", relativePath, result)
            }
          }
        }

        val result = buildLib(moduleOutputPatcher = moduleOutputPatcher, platform = state.platform, context = context)
        if (!isUpdateFromSources && context.productProperties.scrambleMainJar) {
          scramble(state.platform, context)
        }

        val distAllDir = context.paths.distAllDir
        val libDir = distAllDir.resolve("lib")
        val appFile = libDir.resolve("app.jar")
        mergeProductJar(appFile, libDir)
        if (!context.isStepSkipped(BuildOptions.GENERATE_JAR_ORDER_STEP)) {
          reorderJar("lib/app.jar", appFile)
        }
        context.bootClassPathJarNames = generateClasspath(homeDir = distAllDir, libDir = libDir, antTargetFile = antTargetFile)
        result
      }
    }

    listOfNotNull(
      buildPlatformJob,
      async {
        buildBundledPlugins(state = state, plugins = pluginLayouts, buildPlatformJob = buildPlatformJob, context = context)
      },
      async {
        buildOsSpecificBundledPlugins(state = state,
                                      pluginLayouts = pluginLayouts,
                                      isUpdateFromSources = isUpdateFromSources,
                                      buildPlatformJob = buildPlatformJob,
                                      context = context)
      },
      async {
        buildNonBundledPlugins(pluginsToPublish = state.pluginsToPublish,
                               compressPluginArchive = !isUpdateFromSources && context.options.compressZipFiles,
                               buildPlatformLibJob = buildPlatformJob,
                               state = state,
                               context = context)
      },
      if (antDir == null) null else async(Dispatchers.IO) { copyAnt(antDir, antTargetFile!!, context) }
    )
  }.flatMap { it.getCompleted() }

  // must be before reorderJars as these additional plugins maybe required for IDE start-up
  val additionalPluginPaths = context.productProperties.getAdditionalPluginPaths(context)
  if (!additionalPluginPaths.isEmpty()) {
    val pluginDir = context.paths.distAllDir.resolve("plugins")
    withContext(Dispatchers.IO) {
      for (sourceDir in additionalPluginPaths) {
        copyDir(sourceDir, pluginDir.resolve(sourceDir.fileName))
      }
    }
  }

  coroutineScope {
    launch(Dispatchers.IO) {
      spanBuilder("generate content report").useWithScope2 {
        Files.createDirectories(context.paths.artifactDir)
        writeProjectStructureReport(entries = entries,
                                    file = context.paths.artifactDir.resolve("content-mapping.json"),
                                    buildPaths = context.paths)
        Files.newOutputStream(context.paths.artifactDir.resolve("content.json")).use {
          buildJarContentReport(entries = entries, out = it, buildPaths = context.paths)
        }
      }
    }
    createBuildThirdPartyLibraryListJob(entries, context)
  }
  entries
}

/**
 * Validates module structure to be ensure all module dependencies are included
 */
fun validateModuleStructure(platform: PlatformLayout, context: BuildContext) {
  if (context.options.validateModuleStructure) {
    ModuleStructureValidator(context, platform.includedModules).validate()
  }
}

fun getProductModules(state: DistributionBuilderState): List<String> {
  // filter out jars with relative paths in name
  return state.platform.includedModules.asSequence()
    .filter { !it.relativeOutputFile.contains('\\') && !it.relativeOutputFile.contains('/') }
    .map { it.moduleName }
    .distinct()
    .toList()
}

suspend fun buildBundledPlugins(state: DistributionBuilderState,
                                plugins: Collection<PluginLayout>,
                                buildPlatformJob: Job?,
                                context: BuildContext): List<DistributionFileEntry> {
  val pluginDirectoriesToSkip = context.options.bundledPluginDirectoriesToSkip
  return spanBuilder("build bundled plugins")
    .setAttribute(AttributeKey.stringArrayKey("pluginDirectoriesToSkip"), pluginDirectoriesToSkip.toList())
    .setAttribute("count", plugins.size.toLong())
    .useWithScope2 { span ->
      val pluginsToBundle = ArrayList<PluginLayout>(plugins.size)
      plugins.filterTo(pluginsToBundle) {
        satisfiesBundlingRequirements(plugin = it, osFamily = null, arch = null, withEphemeral = false, context = context) &&
        !pluginDirectoriesToSkip.contains(it.directoryName)
      }

      // Doesn't make sense to require passing here a list with a stable order, unnecessary complication. Just sort by main module.
      pluginsToBundle.sortWith(PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE)
      span.setAttribute("satisfiableCount", pluginsToBundle.size.toLong())
      val targetDir = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY)
      buildPlugins(moduleOutputPatcher = ModuleOutputPatcher(),
                   plugins = pluginsToBundle,
                   targetDir = targetDir,
                   state = state,
                   context = context,
                   buildPlatformJob = buildPlatformJob)
    }
}

private suspend fun buildOsSpecificBundledPlugins(state: DistributionBuilderState,
                                                  pluginLayouts: Set<PluginLayout>,
                                                  isUpdateFromSources: Boolean,
                                                  buildPlatformJob: Job?,
                                                  context: BuildContext): List<DistributionFileEntry> {
  return spanBuilder("build os-specific bundled plugins")
    .setAttribute("isUpdateFromSources", isUpdateFromSources)
    .useWithScope2 {
      val platforms = if (isUpdateFromSources) {
        listOf(SupportedDistribution(os = OsFamily.currentOs, arch = JvmArchitecture.currentJvmArch))
      }
      else {
        SUPPORTED_DISTRIBUTIONS
      }

      coroutineScope {
        platforms.mapNotNull { (osFamily, arch) ->
          if (!context.shouldBuildDistributionForOS(osFamily, arch)) {
            return@mapNotNull null
          }

          val osSpecificPlugins = pluginLayouts.filter {
            satisfiesBundlingRequirements(it, osFamily, arch, withEphemeral = false, context)
          }
          if (osSpecificPlugins.isEmpty()) {
            return@mapNotNull null
          }

          val outDir = if (isUpdateFromSources) {
            context.paths.distAllDir.resolve("plugins")
          }
          else {
            getOsAndArchSpecificDistDirectory(osFamily, arch, context).resolve("plugins")
          }

          async(Dispatchers.IO) {
            spanBuilder("build bundled plugins")
              .setAttribute("os", osFamily.osName)
              .setAttribute("arch", arch.name)
              .setAttribute("count", osSpecificPlugins.size.toLong())
              .setAttribute("outDir", outDir.toString())
              .useWithScope2 {
                buildPlugins(moduleOutputPatcher = ModuleOutputPatcher(),
                             plugins = osSpecificPlugins, targetDir = outDir,
                             state = state,
                             context = context,
                             buildPlatformJob = buildPlatformJob)
              }
          }
        }
      }
    }.flatMap { it.getCompleted() }
}

suspend fun buildNonBundledPlugins(pluginsToPublish: Set<PluginLayout>,
                                   compressPluginArchive: Boolean,
                                   buildPlatformLibJob: Job?,
                                   state: DistributionBuilderState,
                                   context: BuildContext): List<DistributionFileEntry> {
  if (pluginsToPublish.isEmpty()) {
    return emptyList()
  }

  return spanBuilder("build non-bundled plugins").setAttribute("count", pluginsToPublish.size.toLong()).useWithScope2 { span ->
    if (context.options.buildStepsToSkip.contains(BuildOptions.NON_BUNDLED_PLUGINS_STEP)) {
      span.addEvent("skip")
      return@useWithScope2 emptyList<DistributionFileEntry>()
    }

    val nonBundledPluginsArtifacts = context.paths.artifactDir.resolve("${context.applicationInfo.productCode}-plugins")
    val autoUploadingDir = nonBundledPluginsArtifacts.resolve("auto-uploading")
    coroutineScope {
      val buildKeymapPluginsTask = async { buildKeymapPlugins(autoUploadingDir, context) }
      val moduleOutputPatcher = ModuleOutputPatcher()
      val stageDir = context.paths.tempDir.resolve("non-bundled-plugins-${context.applicationInfo.productCode}")
      NioFiles.deleteRecursively(stageDir)
      val dirToJar = ConcurrentLinkedQueue<NonBundledPlugin>()
      val defaultPluginVersion = if (context.buildNumber.endsWith(".SNAPSHOT")) {
        "${context.buildNumber}.${pluginDateFormat.format(ZonedDateTime.now())}"
      }
      else {
        context.buildNumber
      }

      // buildPlugins pluginBuilt listener is called concurrently
      val pluginSpecs = ConcurrentLinkedQueue<PluginRepositorySpec>()
      val autoPublishPluginChecker = loadPluginAutoPublishList(context)
      val prepareCustomPluginRepository = context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins &&
                                          !context.isStepSkipped(BuildOptions.ARCHIVE_PLUGINS)
      // we don't simplify the layout for non-bundled plugins, because PluginInstaller not ready for this (see rootEntryName)
      val mappings = buildPlugins(moduleOutputPatcher = moduleOutputPatcher,
                                  plugins = pluginsToPublish.sortedWith(PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE),
                                  targetDir = stageDir,
                                  state = state,
                                  context = context,
                                  simplify = false,
                                  buildPlatformJob = buildPlatformLibJob) { plugin, pluginDirOrFile ->
        val targetDirectory = if (autoPublishPluginChecker.test(plugin)) autoUploadingDir else nonBundledPluginsArtifacts
        val moduleOutput = context.getModuleOutputDir(context.findRequiredModule(plugin.mainModule))
        val pluginXmlPath = moduleOutput.resolve("META-INF/plugin.xml")
        val pluginVersion = if (Files.exists(pluginXmlPath)) {
          plugin.versionEvaluator.evaluate(pluginXmlPath, defaultPluginVersion, context)
        }
        else {
          defaultPluginVersion
        }
        val destFile = targetDirectory.resolve("${plugin.directoryName}-$pluginVersion.zip")
        val pluginXml = moduleOutputPatcher.getPatchedPluginXml(plugin.mainModule)
        pluginSpecs.add(PluginRepositorySpec(destFile, pluginXml))
        dirToJar.add(NonBundledPlugin(pluginDirOrFile, destFile, !plugin.enableSymlinksAndExecutableResources))
      }

      archivePlugins(items = dirToJar, compress = compressPluginArchive, withBlockMap = compressPluginArchive, context = context)

      val helpPlugin = buildHelpPlugin(pluginVersion = defaultPluginVersion, context = context)
      if (helpPlugin != null) {
        val spec = buildHelpPlugin(helpPlugin = helpPlugin,
                                   pluginsToPublishDir = stageDir,
                                   targetDir = autoUploadingDir,
                                   moduleOutputPatcher = moduleOutputPatcher,
                                   state = state,
                                   context = context)
        pluginSpecs.add(spec)
      }

      for (item in buildKeymapPluginsTask.await()) {
        pluginSpecs.add(PluginRepositorySpec(pluginZip = item.first, pluginXml = item.second))
      }
      if (prepareCustomPluginRepository) {
        val list = pluginSpecs.sortedBy { it.pluginZip }
        generatePluginRepositoryMetaFile(list, nonBundledPluginsArtifacts, context)
        generatePluginRepositoryMetaFile(list.filter { it.pluginZip.startsWith(autoUploadingDir) }, autoUploadingDir, context)
      }
      pluginSpecs.forEach {
        if (it.pluginZip.startsWith(autoUploadingDir)) {
          launch {
            validatePlugin(it.pluginZip, context)
          }
        }
      }
      mappings
    }
  }
}

private suspend fun validatePlugin(path: Path, context: BuildContext) {
  context.executeStep(spanBuilder("plugin validation").setAttribute("path", "$path"), BuildOptions.VALIDATE_PLUGINS_TO_BE_PUBLISHED) {
    if (!Files.exists(path)) {
      it.addEvent("path doesn't exist, skipped")
      return@executeStep
    }

    var id: String? = null
    val problems = when (val result = IdePluginManager.createManager().createPlugin(path)) {
      is PluginCreationSuccess -> {
        id = result.plugin.pluginId
        result.unacceptableWarnings
      }
      is PluginCreationFail -> {
        result.errorsAndWarnings
      }
    }
    if (problems.isNotEmpty()) {
      reportBuildProblem(problems.joinToString(
        prefix = "${id ?: path}: ",
        separator = ". ",
        transform = PluginProblem::message
      ), identity = "${id ?: path}")
    }
  }
}

private suspend fun buildHelpPlugin(helpPlugin: PluginLayout,
                                    pluginsToPublishDir: Path,
                                    targetDir: Path,
                                    moduleOutputPatcher: ModuleOutputPatcher,
                                    state: DistributionBuilderState,
                                    context: BuildContext): PluginRepositorySpec {
  val directory = helpPlugin.directoryName
  val destFile = targetDir.resolve("$directory.zip")
  spanBuilder("build help plugin").setAttribute("dir", directory).useWithScope2 {
    buildPlugins(moduleOutputPatcher = moduleOutputPatcher,
                 plugins = listOf(helpPlugin),
                 targetDir = pluginsToPublishDir.resolve(directory),
                 state = state,
                 context = context,
                 buildPlatformJob = null)
    zipWithCompression(targetFile = destFile, dirs = mapOf(pluginsToPublishDir.resolve(directory) to ""))
    null
  }
  return PluginRepositorySpec(destFile, moduleOutputPatcher.getPatchedPluginXml(helpPlugin.mainModule))
}

internal suspend fun generateProjectStructureMapping(context: BuildContext, platform: PlatformLayout): List<DistributionFileEntry> {
  val moduleOutputPatcher = ModuleOutputPatcher()
  return coroutineScope {
    val libDirLayout = async {
      layoutPlatformDistribution(moduleOutputPatcher = moduleOutputPatcher,
                                 targetDirectory = context.paths.distAllDir,
                                 platform = platform,
                                 context = context,
                                 copyFiles = false)
    }
    val allPlugins = getPluginLayoutsByJpsModuleNames(modules = context.productProperties.productLayout.bundledPluginModules,
                                                      productLayout = context.productProperties.productLayout)
    val entries = ArrayList<DistributionFileEntry>()
    for (plugin in allPlugins) {
      if (satisfiesBundlingRequirements(plugin = plugin, osFamily = null, arch = null, withEphemeral = true, context = context)) {
        val targetDirectory = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY).resolve(plugin.directoryName)
        entries.addAll(layoutDistribution(layout = plugin,
                                          targetDirectory = targetDirectory,
                                          copyFiles = false,
                                          simplify = false,
                                          moduleOutputPatcher = moduleOutputPatcher,
                                          includedModules = plugin.includedModules,
                                          context = context).first)
      }
    }
    entries.addAll(libDirLayout.await())
    entries
  }
}

private suspend fun buildPlugins(moduleOutputPatcher: ModuleOutputPatcher,
                                 plugins: Collection<PluginLayout>,
                                 targetDir: Path,
                                 state: DistributionBuilderState,
                                 context: BuildContext,
                                 buildPlatformJob: Job?,
                                 simplify: Boolean = false,
                                 pluginBuilt: ((PluginLayout, pluginDirOrFile: Path) -> Unit)? = null): List<DistributionFileEntry> {
  val scrambleTool = context.proprietaryBuildTools.scrambleTool
  val isScramblingSkipped = context.options.buildStepsToSkip.contains(BuildOptions.SCRAMBLING_STEP)

  class ScrambleTask(@JvmField val plugin: PluginLayout, @JvmField val pluginDir: Path, @JvmField val targetDir: Path)

  val scrambleTasks = mutableListOf<ScrambleTask>()

  val entries: List<DistributionFileEntry> = coroutineScope {
    plugins.map { plugin ->
      if (plugin.mainModule != "intellij.platform.builtInHelp") {
        checkOutputOfPluginModules(mainPluginModule = plugin.mainModule,
                                   includedModules = plugin.includedModules,
                                   moduleExcludes = plugin.moduleExcludes,
                                   context = context)
        patchPluginXml(moduleOutputPatcher = moduleOutputPatcher,
                       plugin = plugin,
                       releaseDate = context.applicationInfo.majorReleaseDate,
                       releaseVersion = context.applicationInfo.releaseVersionForLicensing,
                       pluginsToPublish = state.pluginsToPublish,
                       context = context)
      }

      val directoryName = plugin.directoryName
      val pluginDir = targetDir.resolve(directoryName)
      val task = async {
        spanBuilder("plugin").setAttribute("path", context.paths.buildOutputDir.relativize(pluginDir).toString()).useWithScope2 {
          val (entries, file) = layoutDistribution(layout = plugin,
                                                   targetDirectory = pluginDir,
                                                   copyFiles = true,
                                                   simplify = simplify,
                                                   moduleOutputPatcher = moduleOutputPatcher,
                                                   includedModules = plugin.includedModules,
                                                   context = context)
          pluginBuilt?.invoke(plugin, file)
          entries
        }
      }

      if (!plugin.pathsToScramble.isEmpty()) {
        val attributes = Attributes.of(AttributeKey.stringKey("plugin"), directoryName)
        if (scrambleTool == null) {
          Span.current().addEvent("skip scrambling plugin because scrambleTool isn't defined, but plugin defines paths to be scrambled",
                                  attributes)
        }
        else if (isScramblingSkipped) {
          Span.current().addEvent("skip scrambling plugin because step is disabled", attributes)
        }
        else {
          // we can not start executing right now because the plugin can use other plugins in a scramble classpath
          scrambleTasks.add(ScrambleTask(plugin, pluginDir, targetDir))
        }
      }

      task
    }
  }.flatMap { it.getCompleted() }

  if (scrambleTasks.isNotEmpty()) {
    checkNotNull(scrambleTool)

    // scrambling can require classes from the platform
    buildPlatformJob?.let { task ->
      spanBuilder("wait for platform lib for scrambling").useWithScope2 { task.join() }
    }
    coroutineScope {
      for (scrambleTask in scrambleTasks) {
        launch {
          scrambleTool.scramblePlugin(context = context,
                                      pluginLayout = scrambleTask.plugin,
                                      targetDir = scrambleTask.pluginDir,
                                      additionalPluginsDir = scrambleTask.targetDir)
        }
      }
    }
  }
  return entries
}

private fun getAtMostTwoNonHiddenChildren(outDir: Path): List<Path> {
  return Files.newDirectoryStream(outDir).use { stream ->
    stream.asSequence().filter { !Files.isHidden(it) && !it.fileName.startsWith(".") }.take(2).toList()
  }
}

private const val PLUGINS_DIRECTORY = "plugins"
private val PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE: Comparator<PluginLayout> = compareBy { it.mainModule }

internal class PluginRepositorySpec(@JvmField val pluginZip: Path, @JvmField val pluginXml: ByteArray /* content of plugin.xml */)

fun getPluginLayoutsByJpsModuleNames(modules: Collection<String>, productLayout: ProductModulesLayout): MutableSet<PluginLayout> {
  if (modules.isEmpty()) {
    return createPluginLayoutSet(0)
  }

  val pluginLayouts = productLayout.pluginLayouts
  val pluginLayoutsByMainModule = pluginLayouts.groupBy { it.mainModule }
  val result = createPluginLayoutSet(modules.size)
  for (moduleName in modules) {
    val customLayouts = pluginLayoutsByMainModule.get(moduleName)
    if (customLayouts == null) {
      check(moduleName == "kotlin-ultimate.kmm-plugin" || result.add(PluginLayout.plugin(moduleName))) {
        "Plugin layout for module $moduleName is already added (duplicated module name?)"
      }
    }
    else {
      for (layout in customLayouts) {
        check(layout.mainModule == "kotlin-ultimate.kmm-plugin" || result.add(layout)) {
          "Plugin layout for module $moduleName is already added (duplicated module name?)"
        }
      }
    }
  }
  return result
}

@TestOnly
fun collectProjectLibrariesWhichShouldBeProvidedByPlatform(plugin: BaseLayout,
                                                           result: MultiMap<JpsLibrary, JpsModule>,
                                                           context: BuildContext) {
  for (moduleName in plugin.includedModules.map { it.moduleName }.distinct()) {
    val module = context.findRequiredModule((moduleName))
    val dependencies = JpsJavaExtensionService.dependencies(module)
    for (library in dependencies.includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).libraries) {
      if (isProjectLibraryUsedByPlugin(library, plugin)) {
        result.putValue(library, module)
      }
    }
  }
}

private fun basePath(buildContext: BuildContext, moduleName: String): Path {
  return Path.of(JpsPathUtil.urlToPath(buildContext.findRequiredModule(moduleName).contentRootsList.urls.first()))
}

suspend fun buildLib(moduleOutputPatcher: ModuleOutputPatcher,
                     platform: PlatformLayout,
                     context: BuildContext): List<DistributionFileEntry> {
  patchKeyMapWithAltClickReassignedToMultipleCarets(moduleOutputPatcher, context)
  val libDirMappings = layoutPlatformDistribution(moduleOutputPatcher = moduleOutputPatcher,
                                                  targetDirectory = context.paths.distAllDir,
                                                  platform = platform,
                                                  context = context,
                                                  copyFiles = true)

  context.proprietaryBuildTools.scrambleTool?.validatePlatformLayout(platform.includedModules, context)
  return libDirMappings
}

suspend fun layoutPlatformDistribution(moduleOutputPatcher: ModuleOutputPatcher,
                                       targetDirectory: Path,
                                       platform: PlatformLayout,
                                       context: BuildContext,
                                       copyFiles: Boolean): List<DistributionFileEntry> {
  return spanBuilder("layout lib")
    .setAttribute("path", targetDirectory.toString())
    .useWithScope2 {
      layoutDistribution(layout = platform,
                         targetDirectory = targetDirectory,
                         copyFiles = copyFiles,
                         moduleOutputPatcher = moduleOutputPatcher,
                         includedModules = platform.includedModules,
                         context = context).first
    }
}

private fun patchKeyMapWithAltClickReassignedToMultipleCarets(moduleOutputPatcher: ModuleOutputPatcher, context: BuildContext) {
  if (!context.productProperties.reassignAltClickToMultipleCarets) {
    return
  }

  val moduleName = "intellij.platform.resources"
  val sourceFile = context.getModuleOutputDir((context.findModule(moduleName))!!).resolve("keymaps/\$default.xml")
  var text = Files.readString(sourceFile)
  text = text.replace("<mouse-shortcut keystroke=\"alt button1\"/>", "<mouse-shortcut keystroke=\"to be alt shift button1\"/>")
  text = text.replace("<mouse-shortcut keystroke=\"alt shift button1\"/>", "<mouse-shortcut keystroke=\"alt button1\"/>")
  text = text.replace("<mouse-shortcut keystroke=\"to be alt shift button1\"/>", "<mouse-shortcut keystroke=\"alt shift button1\"/>")
  moduleOutputPatcher.patchModuleOutput(moduleName, "keymaps/\$default.xml", text)
}

internal fun getOsAndArchSpecificDistDirectory(osFamily: OsFamily, arch: JvmArchitecture, context: BuildContext): Path {
  return context.paths.buildOutputDir.resolve("dist.${osFamily.distSuffix}.${arch.name}")
}

fun checkOutputOfPluginModules(mainPluginModule: String,
                               includedModules: Collection<ModuleItem>,
                               moduleExcludes: Map<String, List<String>>,
                               context: BuildContext) {
  // don't check modules which are not direct children of lib/ directory
  val modulesWithPluginXml = mutableListOf<String>()
  for (item in includedModules) {
    if (!item.relativeOutputFile.contains('/')) {
      val moduleName = item.moduleName
      if (containsFileInOutput(moduleName = moduleName,
                               filePath = "META-INF/plugin.xml",
                               excludes = moduleExcludes.get(moduleName) ?: emptyList(),
                               context = context)) {
        modulesWithPluginXml.add(moduleName)
      }
    }
  }

  check(!modulesWithPluginXml.isEmpty()) {
    "No module from \'$mainPluginModule\' plugin contains plugin.xml"
  }
  check(modulesWithPluginXml.size == 1) {
      "Multiple modules (${modulesWithPluginXml.joinToString()}) from \'$mainPluginModule\' plugin " +
      "contain plugin.xml files so the plugin won\'t work properly"
  }
  for (module in includedModules.asSequence().map { it.moduleName }.distinct()) {
    if (module == "intellij.java.guiForms.rt" ||
        !containsFileInOutput(moduleName = module,
                              filePath = "com/intellij/uiDesigner/core/GridLayoutManager.class",
                              excludes = moduleExcludes.get(module) ?: emptyList(),
                              context = context)) {
      "Runtime classes of GUI designer must not be packaged to \'$module\' module in \'$mainPluginModule\' plugin, " +
      "because they are included into a platform JAR. Make sure that 'Automatically copy form runtime classes " +
      "to the output directory' is disabled in Settings | Editor | GUI Designer."
    }
  }
}

private fun containsFileInOutput(moduleName: String,
                                 filePath: String,
                                 excludes: Collection<String>,
                                 context: BuildContext): Boolean {
  val moduleOutput = context.getModuleOutputDir(context.findRequiredModule(moduleName))
  val fileInOutput = moduleOutput.resolve(filePath)
  if (Files.notExists(fileInOutput)) {
    return false
  }

  val set = FileSet(moduleOutput).include(filePath)
  excludes.forEach(set::exclude)
  return !set.isEmpty()
}

fun getPluginAutoUploadFile(communityRoot: BuildDependenciesCommunityRoot): Path {
  val autoUploadFile = communityRoot.communityRoot.resolve("../build/plugins-autoupload.txt")
  require(Files.isRegularFile(autoUploadFile)) {
    "File '$autoUploadFile' must exist"
  }
  return autoUploadFile
}

fun readPluginAutoUploadFile(autoUploadFile: Path): Collection<String> {
  val config = Files.lines(autoUploadFile).use { lines ->
    lines
      .asSequence()
      .map { StringUtil.split(it, "//", true, false)[0] }
      .map { StringUtil.split(it, "#", true, false)[0].trim() }
      .filter { !it.isEmpty() }
      .toCollection(TreeSet(String.CASE_INSENSITIVE_ORDER))
  }

  return config
}

private suspend fun scramble(platform: PlatformLayout, context: BuildContext) {
  val tool = context.proprietaryBuildTools.scrambleTool
  if (tool == null) {
    Span.current().addEvent("skip scrambling because `scrambleTool` isn't defined")
    return
  }

  tool.scramble(platform, context)
}

private suspend fun copyAnt(antDir: Path, antTargetFile: Path, context: BuildContext): List<DistributionFileEntry> {
  return spanBuilder("copy Ant lib").setAttribute("antDir", antDir.toString()).useWithScope2 {
    val sources = ArrayList<ZipSource>()
    val result = ArrayList<DistributionFileEntry>()
    val libraryData = ProjectLibraryData("Ant", LibraryPackMode.MERGED)
    copyDir(sourceDir = context.paths.communityHomeDir.resolve("lib/ant"),
            targetDir = antDir,
            dirFilter = { !it.endsWith("src") },
            fileFilter = { file ->
              if (file.toString().endsWith(".jar")) {
                sources.add(ZipSource(file = file) { result.add(ProjectLibraryEntry(antTargetFile, libraryData, file, it)) })
                false
              }
              else {
                true
              }
            })
    sources.sort()
    buildJar(targetFile = antTargetFile, sources = sources)
    result
  }
}

private fun CoroutineScope.createBuildBrokenPluginListJob(context: BuildContext): Job? {
  val buildString = context.fullBuildNumber
  val fileName = "brokenPlugins.db"
  val targetFile = context.paths.tempDir.resolve(fileName)
  return createSkippableJob(spanBuilder("build broken plugin list")
                              .setAttribute("buildNumber", buildString)
                              .setAttribute("path", targetFile.toString()), BuildOptions.BROKEN_PLUGINS_LIST_STEP, context) {
    buildBrokenPlugins(targetFile, buildString, context.options.isInDevelopmentMode)
    if (Files.exists(targetFile)) {
      context.addDistFile(DistFile(file = targetFile, relativePath = "bin/$fileName"))
    }
  }
}

private fun CoroutineScope.createBuildThirdPartyLibraryListJob(entries: List<DistributionFileEntry>, context: BuildContext): Job? {
  return createSkippableJob(spanBuilder("generate table of licenses for used third-party libraries"),
                            BuildOptions.THIRD_PARTY_LIBRARIES_LIST_STEP, context) {
    val generator = LibraryLicensesListGenerator.create(project = context.project,
                                                        licensesList = context.productProperties.allLibraryLicenses,
                                                        usedModulesNames = entries.includedModules.toHashSet())
    val distAllDir = context.paths.distAllDir
    withContext(Dispatchers.IO) {
      Files.createDirectories(distAllDir)

      val htmlFilePath = distAllDir.resolve("license/third-party-libraries.html")
      val jsonFilePath = distAllDir.resolve("license/third-party-libraries.json")

      generator.generateHtml(htmlFilePath)
      generator.generateJson(jsonFilePath)

      if (context.productProperties.generateLibraryLicensesTable) {
        val artifactNamePrefix = context.productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber)
        val htmlArtifact = context.paths.artifactDir.resolve("$artifactNamePrefix-third-party-libraries.html")
        val jsonArtifact = context.paths.artifactDir.resolve("$artifactNamePrefix-third-party-libraries.json")
        Files.createDirectories(context.paths.artifactDir)
        Files.copy(htmlFilePath, htmlArtifact)
        Files.copy(jsonFilePath, jsonArtifact)
        context.notifyArtifactBuilt(htmlArtifact)
        context.notifyArtifactBuilt(jsonArtifact)
      }
    }
  }
}

fun satisfiesBundlingRequirements(plugin: PluginLayout,
                                  osFamily: OsFamily?,
                                  arch: JvmArchitecture?,
                                  withEphemeral: Boolean,
                                  context: BuildContext): Boolean {
  val bundlingRestrictions = plugin.bundlingRestrictions
  if (bundlingRestrictions.includeInEapOnly && !context.applicationInfo.isEAP) {
    return false
  }

  if (bundlingRestrictions == PluginBundlingRestrictions.EPHEMERAL) {
    return if (withEphemeral) osFamily == null && arch == null else false
  }

  if (bundlingRestrictions == PluginBundlingRestrictions.MARKETPLACE) {
    return false
  }

  return when {
    osFamily == null && bundlingRestrictions.supportedOs != OsFamily.ALL -> false
    osFamily != null && (bundlingRestrictions.supportedOs == OsFamily.ALL || !bundlingRestrictions.supportedOs.contains(osFamily)) -> false
    arch == null && bundlingRestrictions.supportedArch != JvmArchitecture.ALL -> false
    else -> arch == null || bundlingRestrictions.supportedArch.contains(arch)
  }
}

/**
 * @see [[build/plugins-autoupload.txt]] for the specification.
 *
 * @return predicate to test if the given plugin should be auto-published
 */
private fun loadPluginAutoPublishList(context: BuildContext): Predicate<PluginLayout> {
  val file = getPluginAutoUploadFile(context.paths.communityHomeDirRoot)
  val config = readPluginAutoUploadFile(file)

  val productCode = context.applicationInfo.productCode
  return Predicate<PluginLayout> { plugin ->
    val mainModuleName = plugin.mainModule

    val includeInAllProducts = config.contains(mainModuleName)
    val includeInProduct = config.contains("+$productCode:$mainModuleName")
    val excludedFromProduct = config.contains("-$productCode:$mainModuleName")

    if (includeInProduct && (excludedFromProduct || includeInAllProducts)) {
      context.messages.error("Unsupported rules combination: " + config.filter {
        it == mainModuleName || it.endsWith(":$mainModuleName")
      })
    }

    !excludedFromProduct && (includeInAllProducts || includeInProduct)
  }
}

private suspend fun buildKeymapPlugins(targetDir: Path, context: BuildContext): List<Pair<Path, ByteArray>> {
  val keymapDir = context.paths.communityHomeDir.resolve("platform/platform-resources/src/keymaps")
  Files.createDirectories(targetDir)
  return spanBuilder("build keymap plugins").useWithScope2 {
    withContext(Dispatchers.IO) {
      listOf(
        arrayOf("Mac OS X", "Mac OS X 10.5+"),
        arrayOf("Default for GNOME"),
        arrayOf("Default for KDE"),
        arrayOf("Default for XWin"),
        arrayOf("Emacs"),
        arrayOf("Sublime Text", "Sublime Text (Mac OS X)"),
      ).map {
        async { buildKeymapPlugin(it, context.buildNumber, targetDir, keymapDir) }
      }
    }.map { it.getCompleted() }
  }
}

suspend fun layoutDistribution(layout: BaseLayout,
                               targetDirectory: Path,
                               copyFiles: Boolean = true,
                               simplify: Boolean = false,
                               moduleOutputPatcher: ModuleOutputPatcher,
                               includedModules: Collection<ModuleItem>,
                               context: BuildContext): Pair<List<DistributionFileEntry>, Path> {
  if (copyFiles) {
    withContext(Dispatchers.IO) {
      if (!layout.moduleExcludes.isEmpty()) {
        launch {
          checkModuleExcludes(layout.moduleExcludes, context)
        }
      }

      // patchers must be executed _before_ pack because patcher patches module output
      if (layout is PluginLayout && !layout.patchers.isEmpty()) {
        val patchers = layout.patchers
        spanBuilder("execute custom patchers").setAttribute("count", patchers.size.toLong()).useWithScope2 {
          for (patcher in patchers) {
            patcher(moduleOutputPatcher, context)
          }
        }
      }
    }
  }

  val entries = withContext(Dispatchers.IO) {
    val tasks = ArrayList<Deferred<Collection<DistributionFileEntry>>>(3)
    tasks.add(async {
      val outputDir = targetDirectory.resolve("lib")
      spanBuilder("pack").setAttribute("outputDir", outputDir.toString()).useWithScope2 {
        JarPackager.pack(includedModules = includedModules,
                         outputDir = outputDir,
                         isRootDir = layout is PlatformLayout,
                         layout = layout,
                         moduleOutputPatcher = moduleOutputPatcher,
                         dryRun = !copyFiles,
                         context = context)
      }
    })

    if (!context.options.skipCustomResourceGenerators && copyFiles &&
        (!layout.resourcePaths.isEmpty() || (layout is PluginLayout && !layout.resourceGenerators.isEmpty()))) {
      tasks.add(async {
        spanBuilder("pack additional resources").useWithScope2 {
          layoutAdditionalResources(layout = layout, context = context, targetDirectory = targetDirectory)
          emptyList()
        }
      })
    }

    if (!layout.includedArtifacts.isEmpty()) {
      tasks.add(async {
        spanBuilder("pack artifacts").useWithScope2 {
          layoutArtifacts(layout = layout, context = context, copyFiles = copyFiles, targetDirectory = targetDirectory)
        }
      })
    }
    tasks
  }.flatMap { it.getCompleted() }

  if (!simplify) {
    return entries to targetDirectory
  }

  return withContext(Dispatchers.IO) {
    val dirs = getAtMostTwoNonHiddenChildren(targetDirectory)
    if (dirs.size == 1 && dirs.first().endsWith("lib")) {
      val jarFiles = getAtMostTwoNonHiddenChildren(dirs.first())
      if (jarFiles.size == 1) {
        val jarFile = jarFiles.first()
        val newFile = targetDirectory.parent.resolve("${targetDirectory.fileName}.jar")
        Files.move(jarFile, newFile)
        NioFiles.deleteRecursively(targetDirectory)
        return@withContext entries.map {
          check(it.path == jarFile)
          it.changePath(newFile)
        } to newFile
      }
    }
    entries to targetDirectory
  }
}

private suspend fun layoutAdditionalResources(layout: BaseLayout, context: BuildContext, targetDirectory: Path) {
  for (resourceData in layout.resourcePaths) {
    val source = basePath(context, resourceData.moduleName).resolve(resourceData.resourcePath).normalize()
    var target = targetDirectory.resolve(resourceData.relativeOutputPath).normalize()
    if (resourceData.packToZip) {
      if (Files.isDirectory(source)) {
        // do not compress - doesn't make sense as it is a part of distribution
        zip(target, mapOf(source to ""))
      }
      else {
        target = target.resolve(source.fileName)
        Compressor.Zip(target).use { it.addFile(target.fileName.toString(), source) }
      }
    }
    else {
      if (Files.isRegularFile(source)) {
        copyFileToDir(source, target)
      }
      else {
        copyDir(source, target)
      }
    }
  }

  if (layout !is PluginLayout) {
    return
  }

  val resourceGenerators = layout.resourceGenerators
  if (!resourceGenerators.isEmpty()) {
    spanBuilder("generate and pack resources").useWithScope2 {
      for (item in resourceGenerators) {
        item(targetDirectory, context)
      }
    }
  }
}

private fun layoutArtifacts(layout: BaseLayout,
                            context: BuildContext,
                            copyFiles: Boolean,
                            targetDirectory: Path): Collection<DistributionFileEntry> {
  val span = Span.current()
  val entries = ArrayList<DistributionFileEntry>()
  for (entry in layout.includedArtifacts.entries) {
    val artifactName = entry.key
    val relativePath = entry.value
    span.addEvent("include artifact", Attributes.of(AttributeKey.stringKey("artifactName"), artifactName))
    val artifact = JpsArtifactService.getInstance().getArtifacts(context.project).find { it.name == artifactName }
                   ?: error("Cannot find artifact '$artifactName' in the project")
    var artifactPath = targetDirectory.resolve("lib").resolve(relativePath)
    val sourcePath = artifact.outputFilePath?.let(Path::of) ?: error("Missing output path for '$artifactName' artifact")
    if (copyFiles) {
      require(Files.exists(sourcePath)) {
        "Missing output file for '$artifactName' artifact: outputFilePath=${artifact.outputFilePath}, outputPath=${artifact.outputPath}"
      }
    }
    if (artifact.outputFilePath == artifact.outputPath) {
      if (copyFiles) {
        copyDir(sourcePath, artifactPath)
      }
    }
    else {
      artifactPath = artifactPath.resolve(sourcePath.fileName)
      if (copyFiles) {
        copyFile(sourcePath, artifactPath)
      }
    }
    addArtifactMapping(artifact, entries, artifactPath)
  }
  return entries
}

private fun addArtifactMapping(artifact: JpsArtifact, entries: MutableCollection<DistributionFileEntry>, artifactFile: Path) {
  val rootElement = artifact.rootElement
  for (element in rootElement.children) {
    if (element is JpsProductionModuleOutputPackagingElement) {
      entries.add(ModuleOutputEntry(path = artifactFile,
                                    moduleName = element.moduleReference.moduleName,
                                    size = 0,
                                    reason = "artifact: ${artifact.name}"))
    }
    else if (element is JpsTestModuleOutputPackagingElement) {
      entries.add(ModuleTestOutputEntry(path = artifactFile, moduleName = element.moduleReference.moduleName))
    }
    else if (element is JpsLibraryFilesPackagingElement) {
      val library = element.libraryReference.resolve()
      val parentReference = library!!.createReference().parentReference
      if (parentReference is JpsModuleReference) {
        entries.add(ModuleLibraryFileEntry(path = artifactFile, moduleName = parentReference.moduleName, libraryFile = null, size = 0))
      }
      else {
        val libraryData = ProjectLibraryData(library.name, LibraryPackMode.MERGED)
        entries.add(ProjectLibraryEntry(path = artifactFile, data = libraryData, libraryFile = null, size = 0))
      }
    }
  }
}

private fun checkModuleExcludes(moduleExcludes: Map<String, List<String>>, context: CompilationContext) {
  for ((module, value) in moduleExcludes) {
    for (pattern in value) {
      check(Files.exists(context.getModuleOutputDir(context.findRequiredModule(module)))) {
        "There are excludes defined for module `$module`, but the module wasn't compiled;" +
        " most probably it means that \'$module\' isn\'t include into the product distribution " +
        "so it makes no sense to define excludes for it."
      }
    }
  }
}

private data class NonBundledPlugin(val sourceDir: Path, val targetZip: Path, val optimizedZip: Boolean)

private suspend fun archivePlugins(items: Collection<NonBundledPlugin>, compress: Boolean, withBlockMap: Boolean, context: BuildContext) {
  context.executeStep(
    spanBuilder = spanBuilder("archive plugins").setAttribute(AttributeKey.longKey("count"), items.size.toLong()),
    stepId = BuildOptions.ARCHIVE_PLUGINS
  ) {
    val json by lazy { JSON.std.without(JSON.Feature.USE_FIELDS) }
    withContext(Dispatchers.IO) {
      for ((source, target, optimized) in items) {
        launch {
          spanBuilder("archive plugin")
            .setAttribute("input", source.toString())
            .setAttribute("outputFile", target.toString())
            .setAttribute("optimizedZip", optimized)
            .useWithScope2 {
              if (optimized) {
                writeNewZip(target, compress = compress, withOptimizedMetadataEnabled = false) { zipCreator ->
                  ZipArchiver(zipCreator).use { archiver ->
                    if (Files.isDirectory(source)) {
                      archiver.setRootDir(source, source.fileName.toString())
                      archiveDir(startDir = source, archiver = archiver, excludes = null)
                    }
                    else {
                      archiver.setRootDir(source.parent)
                      archiver.addFile(source)
                    }
                  }
                }
              }
              else {
                writeNewFile(target) { outFileChannel ->
                  NoDuplicateZipArchiveOutputStream(outFileChannel, compress = context.options.compressZipFiles).use { out ->
                    out.setUseZip64(Zip64Mode.Never)
                    out.dir(source, "${source.fileName}/", entryCustomizer = { entry, file, _ ->
                      if (Files.isExecutable(file)) {
                        entry.unixMode = executableFileUnixMode
                      }
                    })
                  }
                }
              }
            }
          if (withBlockMap) {
            spanBuilder("build plugin blockmap").setAttribute("file", target.toString()).useWithScope2 {
              buildBlockMap(target, json)
            }
          }
        }
      }
    }
  }
}

/**
 * Builds a blockmap and hash files for plugin to provide downloading plugins via incremental downloading algorithm Blockmap.
 */
private fun buildBlockMap(file: Path, json: JSON) {
  val algorithm = "SHA-256"
  val bytes = Files.newInputStream(file).use { input ->
    json.asBytes(BlockMap(input, algorithm))
  }

  val fileParent = file.parent
  val fileName = file.fileName.toString()
  writeNewZip(fileParent.resolve("$fileName.blockmap.zip"), compress = true) {
    it.compressedData("blockmap.json", bytes)
  }

  val hashFile = fileParent.resolve("$fileName.hash.json")
  Files.newInputStream(file).use { input ->
    Files.newOutputStream(hashFile, *W_CREATE_NEW.toTypedArray()).use { output ->
      json.write(FileHash(input, algorithm), output)
    }
  }
}

suspend fun createIdeClassPath(platform: PlatformLayout, context: BuildContext): Set<String> {
  // for some reasons, maybe duplicated paths - use set
  val classPath = LinkedHashSet<String>()
  val pluginLayouts = context.productProperties.productLayout.pluginLayouts
  val nonPluginEntries = mutableListOf<DistributionFileEntry>()
  val pluginEntries = mutableListOf<DistributionFileEntry>()
  val pluginDir = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY)
  for (e in generateProjectStructureMapping(context = context, platform = platform)) {
    if (e.path.startsWith(pluginDir)) {
      val relativePath = pluginDir.relativize(e.path)
      // for plugins our classloader load jars only from lib folder
      if (relativePath.nameCount == 3 && relativePath.getName(1).toString() == "lib") {
        pluginEntries.add(e)
      }
    }
    else {
      nonPluginEntries.add(e)
    }
  }

  for (entry in (nonPluginEntries + pluginEntries)) {
    when (entry) {
      is ModuleOutputEntry -> {
        classPath.add(context.getModuleOutputDir(context.findRequiredModule(entry.moduleName)).toString())
        (pluginLayouts.firstOrNull { it.mainModule == entry.moduleName } ?: continue)
          .scrambleClasspathPlugins
          .asSequence()
          .map { it.first }
          .map { directoryName -> pluginLayouts.single { it.directoryName == directoryName } }
          .mapTo(classPath) { context.getModuleOutputDir(context.findRequiredModule(it.mainModule)).toString() }
      }
      is LibraryFileEntry -> classPath.add(entry.libraryFile.toString())
      else -> throw UnsupportedOperationException("Entry $entry is not supported")
    }
  }
  return classPath
}

suspend fun buildSearchableOptions(platform: PlatformLayout,
                                   context: BuildContext,
                                   systemProperties: Map<String, Any> = emptyMap()): Path? {
  return buildSearchableOptions(ideClassPath = createIdeClassPath(platform, context), context = context, systemProperties = systemProperties)
}

/**
 * Build index which is used to search options in the Settings dialog.
 */
suspend fun buildSearchableOptions(ideClassPath: Set<String>,
                                   context: BuildContext,
                                   systemProperties: Map<String, Any> = emptyMap()): Path? {
  val span = Span.current()
  if (context.isStepSkipped(BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP)) {
    span.addEvent("skip building searchable options index")
    return null
  }

  val targetDirectory = context.searchableOptionDir
  val modules = withContext(Dispatchers.IO) {
    NioFiles.deleteRecursively(targetDirectory)
    // bundled maven is also downloaded during traverseUI execution in an external process,
    // making it fragile to call more than one traverseUI at the same time (in the reproducibility test, for example),
    // so it's pre-downloaded with proper synchronization
    BundledMavenDownloader.downloadMavenCommonLibs(context.paths.communityHomeDirRoot)
    BundledMavenDownloader.downloadMavenDistribution(context.paths.communityHomeDirRoot)
    // Start the product in headless mode using com.intellij.ide.ui.search.TraverseUIStarter.
    // It'll process all UI elements in `Settings` dialog and build index for them.
    runApplicationStarter(context = context,
                          tempDir = context.paths.tempDir.resolve("searchableOptions"),
                          ideClasspath = ideClassPath,
                          arguments = listOf("traverseUI", targetDirectory.toString(), "true"),
                          vmOptions = listOf("-Xmx2g"),
                          systemProperties = systemProperties)
    check(Files.isDirectory(targetDirectory)) {
      "Failed to build searchable options index: $targetDirectory does not exist. See log above for error output from traverseUI run."
    }
    Files.newDirectoryStream(targetDirectory).use { it.toList() }
  }
  check(!modules.isEmpty()) {
    "Failed to build searchable options index: $targetDirectory is empty. See log above for error output from traverseUI run."
  }

  span.setAttribute(AttributeKey.longKey("moduleCountWithSearchableOptions"), modules.size)
  span.setAttribute(AttributeKey.stringArrayKey("modulesWithSearchableOptions"),
                    modules.map { targetDirectory.relativize(it).toString() })
  return targetDirectory
}
