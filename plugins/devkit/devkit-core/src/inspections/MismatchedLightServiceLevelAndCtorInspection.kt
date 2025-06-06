// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.lang.jvm.*
import com.intellij.lang.jvm.actions.*
import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue
import com.intellij.lang.jvm.annotation.JvmAnnotationEnumFieldValue
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

internal class MismatchedLightServiceLevelAndCtorInspection : DevKitJvmInspection() {

  private val COROUTINE_SCOPE_PARAM_NAME = "scope"

  override fun buildVisitor(project: Project, sink: HighlightSink, isOnTheFly: Boolean): JvmElementVisitor<Boolean> {
    return object : DefaultJvmElementVisitor<Boolean> {
      override fun visitMethod(method: JvmMethod): Boolean {
        if (!method.isConstructor) return true
        val file: PsiFile = method.sourceElement?.containingFile ?: return true
        val containingClass = method.containingClass ?: return true
        val serviceAnnotation = containingClass.annotations.find { it.qualifiedName == Service::class.java.canonicalName } ?: return true
        val level = getLevel(serviceAnnotation, file.fileType)
        if (level !in listOf(Level.PROJECT, Level.APP_AND_PROJECT)) {
          val isProjectParamCtor = (method.parameters.singleOrNull()?.type as? PsiType)?.canonicalText == Project::class.java.canonicalName
          if (isProjectParamCtor) {
            val projectLevelFqn = "${Service.Level::class.java.canonicalName}.${Service.Level.PROJECT}"
            val request = annotationRequest(Service::class.java.canonicalName,
                                            constantAttribute(DEFAULT_REFERENCED_METHOD_NAME, projectLevelFqn))
            val actions = createAddAnnotationActions(containingClass, request)
            val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), file)
            sink.highlight(DevKitBundle.message("inspection.mismatched.light.service.level.and.ctor.project.level.required"), *fixes)
          }
        }
        if (level == Level.APP || level == Level.APP_AND_PROJECT) {
          if (!isAppLevelServiceCtor(method) && !hasAppLevelServiceCtor(containingClass)) {
            val elementFactory = PsiElementFactory.getInstance(project)
            val coroutineScopeType = elementFactory.createTypeByFQClassName(CoroutineScope::class.java.canonicalName,
                                                                            GlobalSearchScope.allScope(project))
            val methodParametersRequest: ChangeParametersRequest = setMethodParametersRequest(
              linkedMapOf(COROUTINE_SCOPE_PARAM_NAME to coroutineScopeType).entries)
            val actions = createChangeParametersActions(method, setMethodParametersRequest(emptyList())) + createChangeParametersActions(
              method, methodParametersRequest)
            val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), file)
            val message = DevKitBundle.message("inspection.mismatched.light.service.level.and.ctor.app.level.ctor.required")
            sink.highlight(message, *fixes)
          }
        }
        return true
      }

      private fun hasAppLevelServiceCtor(clazz: JvmClass): Boolean {
        return clazz.methods.filter { it.isConstructor }.any { isAppLevelServiceCtor(it) }
      }

      private fun isAppLevelServiceCtor(method: JvmMethod): Boolean {
        return method.parameters.isEmpty() || (method.parameters.singleOrNull()?.type as? PsiType)?.canonicalText == CoroutineScope::class.java.canonicalName
      }

      private fun getLevel(annotation: JvmAnnotation, fileType: FileType): Level {
        val levels = when (val attributeValue = annotation.findAttribute(DEFAULT_REFERENCED_METHOD_NAME)?.attributeValue) {
          is JvmAnnotationArrayValue ->
            if (fileType == KotlinFileType.INSTANCE)
              attributeValue.values
                .filterIsInstance<JvmAnnotationConstantValue>()
                .map { it.constantValue }
                .filterIsInstance<Pair<ClassId, Name>>()
                .filter { (classId, name) ->
                  classId.asFqNameString() == Service.Level::class.java.canonicalName &&
                  name.identifier in listOf(Service.Level.APP.name, Service.Level.PROJECT.name)
                }
                .map { (_, name) -> name.identifier }
            else
              attributeValue.values
                .filterIsInstance<JvmAnnotationEnumFieldValue>()
                .filter {
                  it.containingClassName == Service.Level::class.java.canonicalName &&
                  it.fieldName in listOf(Service.Level.APP.name, Service.Level.PROJECT.name)
                }
                .map { it.fieldName!! }
          is JvmAnnotationEnumFieldValue -> {
            if (attributeValue.containingClassName == Service.Level::class.java.canonicalName &&
                attributeValue.fieldName in listOf(Service.Level.APP.name, Service.Level.PROJECT.name))
              setOf(attributeValue.fieldName!!) else emptySet()
          }
          else -> emptySet()
        }
        return getLevel(levels)
      }
    }
  }
}

internal enum class Level { APP, PROJECT, APP_AND_PROJECT, NOT_SPECIFIED }

internal fun getLevel(levels: Collection<String>): Level {
  return when {
    levels.containsAll(setOf(Service.Level.APP.name, Service.Level.PROJECT.name)) -> Level.APP_AND_PROJECT
    levels.contains(Service.Level.APP.name) -> Level.APP
    levels.contains(Service.Level.PROJECT.name) -> Level.PROJECT
    else -> Level.NOT_SPECIFIED
  }
}