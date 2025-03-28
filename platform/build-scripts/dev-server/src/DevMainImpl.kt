// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused", "RAW_RUN_BLOCKING")

package org.jetbrains.intellij.build.devServer

import com.intellij.openapi.application.PathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.ConsoleSpanExporter
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.TracerProviderManager
import java.nio.file.Path

private class DevMainImpl {
  companion object {
    @Suppress("SpellCheckingInspection")
    @JvmStatic
    fun build(): Collection<Path> {
      // don't use JaegerJsonSpanExporter - not needed for clients, should be enabled only if needed to avoid writing ~500KB JSON file
      TracerProviderManager.spanExporterProvider = { listOf(ConsoleSpanExporter()) }
      //TracerProviderManager.setOutput(Path.of(System.getProperty("user.home"), "trace.json"))
      try {
        return runBlocking(Dispatchers.Default) {
          var newClassPath: Collection<Path>? = null
          buildProductInProcess(BuildRequest(
            platformPrefix = System.getProperty("idea.platform.prefix") ?: "idea",
            additionalModules = getAdditionalModules()?.toList() ?: emptyList(),
            homePath = Path.of(PathManager.getHomePath()),
            keepHttpClient = false,
            isPackagedLib = true,
            platformClassPathConsumer = { classPath, runDir ->
              newClassPath = classPath

              // see BuildContextImpl.getAdditionalJvmArguments - we should somehow deduplicate code
              val libDir = runDir.resolve("lib")
              System.setProperty("jna.boot.library.path", "$libDir/jna/${JvmArchitecture.currentJvmArch.dirName}")
              System.setProperty("pty4j.preferred.native.folder", "$libDir/pty4j")
              // require bundled JNA dispatcher lib
              System.setProperty("jna.nosys", "true")
              System.setProperty("jna.noclasspath", "true")
            },
          ))
          newClassPath!!
        }
      }
      finally {
        TracerProviderManager.finish()
      }
    }
  }
}