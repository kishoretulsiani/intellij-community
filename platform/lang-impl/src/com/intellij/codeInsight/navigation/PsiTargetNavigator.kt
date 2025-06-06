// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.find.FindUtil
import com.intellij.ide.util.EditSourceUtil
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.openapi.util.NlsContexts.TabTitle
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.list.buildTargetPopupWithMultiSelect
import com.intellij.util.containers.map2Array
import java.awt.event.MouseEvent
import java.util.function.*
import java.util.function.Function

class PsiTargetNavigator<T: PsiElement>(val supplier: Supplier<List<T>>) {

  constructor(elements: Array<T>) : this(Supplier { elements.toList() })

  private var selection: PsiElement? = null
  private var presentationProvider: Function<T, TargetPresentation> = Function { targetPresentation(it) }
  private var elementsConsumer: BiConsumer<List<T>, PsiTargetNavigator<T>>? = null
  private var title: @PopupTitle String? = null
  private var tabTitle: @TabTitle String? = null

  fun selection(selection: PsiElement?): PsiTargetNavigator<T> = apply { this.selection = selection }
  fun presentationProvider(provider: Function<T, TargetPresentation>): PsiTargetNavigator<T> = apply { this.presentationProvider = provider }
  fun elementsConsumer(consumer: BiConsumer<List<T>, PsiTargetNavigator<T>>): PsiTargetNavigator<T> = apply { elementsConsumer = consumer }
  fun title(title: @PopupTitle String?): PsiTargetNavigator<T> = apply { this.title = title }
  fun tabTitle(title: @TabTitle String?): PsiTargetNavigator<T> = apply { this.tabTitle = title }

  fun createPopup(project: Project, @PopupTitle title: String?): JBPopup {
    return createPopup(project, title) { element -> EditSourceUtil.navigateToPsiElement(element) }
  }

  fun createPopup(project: Project, @PopupTitle title: String?, processor: PsiElementProcessor<T>): JBPopup {
    val (items, selected) = computeItems(project)
    return buildPopup(items, title, project, selected, getPredicate(processor))
  }

  private fun getPredicate(processor: PsiElementProcessor<T>) = Predicate<ItemWithPresentation> {
    @Suppress("UNCHECKED_CAST") ((it.dereference() as T).let { element -> processor.execute(element) })
  }

  fun navigate(editor: Editor, @PopupTitle title: String?, processor: PsiElementProcessor<T>) {
    navigate(editor.project!!, title, processor, Consumer { it.showInBestPositionFor(editor) })
  }

  fun navigate(e: MouseEvent, @PopupTitle title: String?, project: Project) {
    navigate(project, title, { element -> EditSourceUtil.navigateToPsiElement(element) }, Consumer { it.show(RelativePoint(e)) })
  }

  private fun navigate(project: Project,
                       @PopupTitle title: String?,
                       processor: PsiElementProcessor<T>,
                       popupConsumer: Consumer<JBPopup>
  ) {
    val (items, selected) = computeItems(project)
    val predicate = getPredicate(processor)
    if (items.isEmpty()) {
      return
    }
    else if (items.size == 1) {
      predicate.test(items.first())
    }
    else {
      val popup = buildPopup(items, title, project, selected, predicate)
      popupConsumer.accept(popup)
    }
  }

  fun performSilently(project: Project, processor: PsiElementProcessor<T>) {
    val (items) = computeItems(project)
    val predicate = getPredicate(processor)
    if (items.isEmpty()) {
      return
    }
    else {
      predicate.test(items.first())
    }
  }

  private fun computeItems(project: Project): Pair<List<ItemWithPresentation>, ItemWithPresentation?> {
    return ActionUtil.underModalProgress(project, CodeInsightBundle.message("progress.title.preparing.result"),
                                         Computable {
                                           val elements = supplier.get()
                                           elementsConsumer?.accept(elements, this)
                                           val list = elements.map {
                                             ItemWithPresentation(SmartPointerManager.createPointer(it),
                                                                  presentationProvider.apply(it))
                                           }
                                           val selected = if (selection == null) null
                                           else list[elements.indexOf(selection)]
                                           return@Computable Pair<List<ItemWithPresentation>, ItemWithPresentation?>(list, selected)
                                         })
  }

  private fun buildPopup(targets: List<ItemWithPresentation>,
                         @PopupTitle title: String?,
                         project: Project,
                         selected: ItemWithPresentation?,
                         predicate: Predicate<ItemWithPresentation>): JBPopup {
    val builder = buildTargetPopupWithMultiSelect(targets, Function { it.presentation }, predicate)
    val s = title ?: this.title
    s?.let {
      builder.setTitle(s)
    }
    if (tabTitle != null) {
      builder.setCouldPin { FindUtil.showInUsageView(null, tabTitle!!, project, targets.map2Array { item ->  item.item as SmartPsiElementPointer<*>}) != null }
    }
    selected.let { builder.setSelectedValue(selected, true) }
    return builder.createPopup()
  }
}