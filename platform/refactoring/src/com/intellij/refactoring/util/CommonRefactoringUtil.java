// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.ide.ui.IdeUiService;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;
import static com.intellij.openapi.util.NlsContexts.DialogTitle;

public final class CommonRefactoringUtil {
  private CommonRefactoringUtil() { }

  public static void showErrorMessage(@DialogTitle String title,
                                      @DialogMessage String message,
                                      @NonNls @Nullable String helpId,
                                      @NotNull Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) throw new RuntimeException(message);
    IdeUiService.getInstance().showRefactoringMessageDialog(title, message, helpId, "OptionPane.errorIcon", false, project);
  }

  // order of usages across different files is irrelevant
  public static void sortDepthFirstRightLeftOrder(final UsageInfo[] usages) {
    Arrays.sort(usages, (usage1, usage2) -> {
      PsiElement element1 = usage1.getElement(), element2 = usage2.getElement();
      if (element1 == element2) return 0;
      if (element1 == null) return 1;
      if (element2 == null) return -1;
      return element2.getTextRange().getStartOffset() - element1.getTextRange().getStartOffset();
    });
  }

  /**
   * Fatal refactoring problem during unit test run. Corresponds to message of modal dialog shown during user driven refactoring.
   */
  public static class RefactoringErrorHintException extends RuntimeException {
    public RefactoringErrorHintException(String message) {
      super(message);
    }
  }

  public static void showErrorHint(@NotNull Project project,
                                   @Nullable Editor editor,
                                   @NotNull @DialogMessage String message,
                                   @NotNull @DialogTitle String title,
                                   @Nullable String helpId) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      throw new RefactoringErrorHintException(message);
    }

    // Invoke editor.getComponent() before invokeLater(), so we can quickly fail
    // and get better stack trace if the imaginary editor is supplied
    boolean noRootPane = editor == null || editor.getComponent().getRootPane() == null;
    ApplicationManager.getApplication().invokeLater(() -> {
      if (noRootPane) {
        showErrorMessage(title, message, helpId, project);
      }
      else {
        IdeUiService.getInstance().showErrorHint(editor, message);
      }
    });
  }

  public static @NlsSafe String htmlEmphasize(@NotNull @Nls String text) {
    return StringUtil.htmlEmphasize(text);
  }

  public static boolean checkReadOnlyStatus(@NotNull PsiElement element) {
    final VirtualFile file = element.getContainingFile().getVirtualFile();
    return file != null && !ReadonlyStatusHandler.getInstance(element.getProject()).ensureFilesWritable(Collections.singletonList(file)).hasReadonlyFiles();
  }

  public static boolean checkReadOnlyStatus(@NotNull Project project, @NotNull PsiElement element) {
    return checkReadOnlyStatus(element, project, RefactoringBundle.message("refactoring.cannot.be.performed"));
  }

  public static boolean checkReadOnlyStatus(@NotNull Project project, PsiElement @NotNull ... elements) {
    return checkReadOnlyStatus(project, Collections.emptySet(), Arrays.asList(elements), RefactoringBundle.message("refactoring.cannot.be.performed"), true);
  }

  public static boolean checkReadOnlyStatus(@NotNull Project project, @NotNull Collection<? extends PsiElement> elements, boolean notifyOnFail) {
    return checkReadOnlyStatus(project, Collections.emptySet(), elements, RefactoringBundle.message("refactoring.cannot.be.performed"), notifyOnFail);
  }

  public static boolean checkReadOnlyStatus(@NotNull PsiElement element, @NotNull Project project, @NotNull String messagePrefix) {
    return element.isWritable() || checkReadOnlyStatus(project, Collections.emptySet(), Collections.singleton(element), messagePrefix, true);
  }

  public static boolean checkReadOnlyStatusRecursively(@NotNull Project project, @NotNull Collection<? extends PsiElement> elements) {
    return checkReadOnlyStatus(project, elements, Collections.emptySet(), RefactoringBundle.message("refactoring.cannot.be.performed"), false);
  }

  public static boolean checkReadOnlyStatusRecursively(@NotNull Project project, @NotNull Collection<? extends PsiElement> elements, boolean notifyOnFail) {
    return checkReadOnlyStatus(project, elements, Collections.emptySet(), RefactoringBundle.message("refactoring.cannot.be.performed"), notifyOnFail);
  }

  public static boolean checkReadOnlyStatus(@NotNull Project project,
                                            @NotNull Collection<? extends PsiElement> recursive,
                                            @NotNull Collection<? extends PsiElement> flat,
                                            boolean notifyOnFail) {
    return checkReadOnlyStatus(project, recursive, flat, RefactoringBundle.message("refactoring.cannot.be.performed"), notifyOnFail);
  }

  private static boolean checkReadOnlyStatus(@NotNull Project project,
                                             @NotNull Collection<? extends PsiElement> recursive,
                                             @NotNull Collection<? extends PsiElement> flat,
                                             @NotNull String messagePrefix,
                                             boolean notifyOnFail) {
    Collection<VirtualFile> readonly = new HashSet<>();  // not writable, but could be checked out
    Collection<VirtualFile> failed = new HashSet<>();  // those located in read-only filesystem

    boolean seenNonWritablePsiFilesWithoutVirtualFile =
      ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(() -> ReadAction.compute(() -> checkReadOnlyStatus(flat, false, readonly, failed) || checkReadOnlyStatus(recursive, true, readonly, failed)),
                                             RefactoringBundle.message("progress.title.collect.read.only.files"),
                                             false, project);

    ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(readonly);
    ContainerUtil.addAll(failed, status.getReadonlyFiles());

    if (notifyOnFail && (!failed.isEmpty() || seenNonWritablePsiFilesWithoutVirtualFile && readonly.isEmpty())) {
      @NlsSafe StringBuilder message = new StringBuilder(messagePrefix).append('\n');
      int i = 0;
      for (VirtualFile virtualFile : failed) {
        String subj = RefactoringBundle.message(virtualFile.isDirectory() ? "directory.description" : "file.description", virtualFile.getPresentableUrl());
        if (virtualFile.getFileSystem().isReadOnly()) {
          message.append(RefactoringBundle.message("0.is.located.in.a.jar.file", subj)).append('\n');
        }
        else {
          message.append(RefactoringBundle.message("0.is.read.only", subj)).append('\n');
        }
        if (i++ > 20) {
          message.append("...\n");
          break;
        }
      }
      showErrorMessage(RefactoringBundle.message("error.title"), message.toString(), null, project);
      return false;
    }

    return failed.isEmpty();
  }

  private static boolean checkReadOnlyStatus(Collection<? extends PsiElement> elements,
                                             boolean recursively,
                                             Collection<? super VirtualFile> readonly,
                                             Collection<? super VirtualFile> failed) {
    boolean seenNonWritablePsiFilesWithoutVirtualFile = false;

    for (PsiElement element : elements) {
      if (element instanceof PsiDirectory dir) {
        final VirtualFile vFile = dir.getVirtualFile();
        if (vFile.getFileSystem().isReadOnly()) {
          failed.add(vFile);
        }
        else if (recursively) {
          collectReadOnlyFiles(vFile, readonly);
        }
        else {
          readonly.add(vFile);
        }
      }
      else if (element instanceof PsiDirectoryContainer) {
        final PsiDirectory[] directories = ((PsiDirectoryContainer)element).getDirectories();
        for (PsiDirectory directory : directories) {
          VirtualFile virtualFile = directory.getVirtualFile();
          if (recursively) {
            if (virtualFile.getFileSystem().isReadOnly()) {
              failed.add(virtualFile);
            }
            else {
              collectReadOnlyFiles(virtualFile, readonly);
            }
          }
          else if (virtualFile.getFileSystem().isReadOnly()) {
            failed.add(virtualFile);
          }
          else {
            readonly.add(virtualFile);
          }
        }
      }
      else {
        PsiFile file = element.getContainingFile();
        if (file == null) {
          if (!element.isWritable()) {
            seenNonWritablePsiFilesWithoutVirtualFile = true;
          }
        }
        else {
          final VirtualFile vFile = file.getVirtualFile();
          if (vFile != null) {
            readonly.add(vFile);
          }
          else if (!element.isWritable()) {
            seenNonWritablePsiFilesWithoutVirtualFile = true;
          }
        }
      }
    }

    return seenNonWritablePsiFilesWithoutVirtualFile;
  }

  public static void collectReadOnlyFiles(@NotNull VirtualFile vFile, @NotNull final Collection<? super VirtualFile> list) {
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();

    VfsUtilCore.visitChildrenRecursively(vFile, new VirtualFileVisitor<Void>(VirtualFileVisitor.NO_FOLLOW_SYMLINKS) {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        final boolean ignored = fileTypeManager.isFileIgnored(file);
        if (!file.isWritable() && !ignored) {
          list.add(file);
        }
        return !ignored;
      }
    });
  }

  /**
   * @deprecated use {@link StringUtil#capitalize(String)}
   */
  @Deprecated(forRemoval = true)
  public static String capitalize(@NotNull String text) {
    return StringUtil.capitalize(text);
  }

  public static boolean isAncestor(@NotNull PsiElement resolved, @NotNull Collection<? extends PsiElement> scopes) {
    for (final PsiElement scope : scopes) {
      if (PsiTreeUtil.isAncestor(scope, resolved, false)) return true;
    }
    return false;
  }

  private static int fixCaretOffset(@NotNull final Editor editor) {
    final int caret = editor.getCaretModel().getOffset();
    if (editor.getSelectionModel().hasSelection()) {
      if (caret == editor.getSelectionModel().getSelectionEnd()) {
        return Math.max(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd() - 1);
      }
    }

    return caret;
  }

  public static PsiElement getElementAtCaret(@NotNull final Editor editor, final PsiFile file) {
    final int offset = fixCaretOffset(editor);
    PsiElement element = file.findElementAt(offset);
    if (element == null && offset == file.getTextLength()) {
      element = file.findElementAt(offset - 1);
    }

    if (element instanceof PsiWhiteSpace) {
      element = file.findElementAt(element.getTextRange().getStartOffset() - 1);
    }
    return element;
  }

  public static PsiElement @NotNull [] getPsiElementArray(@NotNull DataContext dataContext) {
    PsiElement[] psiElements = PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    if (psiElements == null || psiElements.length == 0) {
      PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
      if (element != null) {
        psiElements = new PsiElement[]{element};
      }
    }

    return psiElements != null ? psiElements : PsiElement.EMPTY_ARRAY;
  }
}
