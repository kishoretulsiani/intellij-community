// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
  Propose to cast one argument to corresponding type
   in the constructor invocation
  E.g.

  User: cdr
  Date: Nov 13, 2002
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import org.jetbrains.annotations.NotNull;

public final class ConstructorParametersFixer {
  public static void registerFixActions(@NotNull PsiJavaCodeReferenceElement ctrRef,
                                        @NotNull PsiConstructorCall constructorCall,
                                        @NotNull HighlightInfo.Builder builder,
                                        @NotNull TextRange fixRange) {
    JavaResolveResult resolved = ctrRef.advancedResolve(false);
    PsiClass aClass = (PsiClass) resolved.getElement();
    if (aClass == null) return;
    PsiMethod[] methods = aClass.getConstructors();
    CandidateInfo[] candidates = new CandidateInfo[methods.length];
    for (int i = 0; i < candidates.length; i++) {
      candidates[i] = new CandidateInfo(methods[i], resolved.getSubstitutor());
    }
    CastMethodArgumentFix.REGISTRAR.registerCastActions(candidates, constructorCall, builder, fixRange);
    AddTypeArgumentsFix.REGISTRAR.registerCastActions(candidates, constructorCall, builder, fixRange);
    WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(candidates, constructorCall, builder, fixRange);
    WrapWithAdapterMethodCallFix.registerCastActions(candidates, constructorCall, builder, fixRange);
  }

  public static void registerEnumConstantFixActions(@NotNull PsiClass aClass,
                                                    @NotNull PsiConstructorCall constructorCall,
                                                    @NotNull HighlightInfo.Builder builder,
                                                    @NotNull TextRange fixRange) {
    PsiMethod[] methods = aClass.getConstructors();
    CandidateInfo[] candidates = new CandidateInfo[methods.length];
    for (int i = 0; i < candidates.length; i++) {
      candidates[i] = new CandidateInfo(methods[i], PsiSubstitutor.EMPTY);
    }
    CastMethodArgumentFix.REGISTRAR.registerCastActions(candidates, constructorCall, builder, fixRange);
    WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(candidates, constructorCall, builder, fixRange);
    WrapWithAdapterMethodCallFix.registerCastActions(candidates, constructorCall, builder, fixRange);
  }
}
