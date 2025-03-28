// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.AddMissingDeconstructionComponentsFix;
import com.intellij.codeInsight.daemon.impl.quickfix.AddMissingDeconstructionComponentsFix.Pattern;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.PsiClassType.ClassResolveResult;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class PatternHighlightingModel {

  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  static void createDeconstructionErrors(@Nullable PsiDeconstructionPattern deconstructionPattern, @NotNull HighlightInfoHolder holder) {
    if (deconstructionPattern == null) return;
    PsiTypeElement typeElement = deconstructionPattern.getTypeElement();
    PsiType recordType = typeElement.getType();
    var resolveResult = recordType instanceof PsiClassType classType ? classType.resolveGenerics() : ClassResolveResult.EMPTY;
    PsiClass recordClass = resolveResult.getElement();
    if (recordClass == null || !recordClass.isRecord()) {
      String message = JavaErrorBundle.message("deconstruction.pattern.requires.record", JavaHighlightUtil.formatType(recordType));
      var info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message).create();
      holder.add(info);
      return;
    }
    if (resolveResult.getInferenceError() != null) {
      String message = JavaErrorBundle.message("error.cannot.infer.pattern.type", resolveResult.getInferenceError());
      var info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message).create();
      holder.add(info);
      return;
    }
    PsiJavaCodeReferenceElement ref = typeElement.getInnermostComponentReferenceElement();
    if (recordClass.hasTypeParameters() && ref != null && ref.getTypeParameterCount() == 0 &&
        PsiUtil.getLanguageLevel(deconstructionPattern).isLessThan(LanguageLevel.JDK_20_PREVIEW)) {
      String message = JavaErrorBundle.message("error.raw.deconstruction", typeElement.getText());
      var info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message).create();
      holder.add(info);
      return;
    }
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    PsiRecordComponent[] recordComponents = recordClass.getRecordComponents();
    PsiPattern[] deconstructionComponents = deconstructionPattern.getDeconstructionList().getDeconstructionComponents();
    boolean hasMismatchedPattern = false;
    for (int i = 0; i < Math.min(recordComponents.length, deconstructionComponents.length); i++) {
      PsiPattern deconstructionComponent = deconstructionComponents[i];
      PsiType recordComponentType = recordComponents[i].getType();
      PsiType substitutedRecordComponentType = substitutor.substitute(recordComponentType);
      PsiType deconstructionComponentType = JavaPsiPatternUtil.getPatternType(deconstructionComponent);
      if (!isApplicable(substitutedRecordComponentType, deconstructionComponentType)) {
        hasMismatchedPattern = true;
        if (recordComponents.length == deconstructionComponents.length) {
          var builder = HighlightUtil.createIncompatibleTypeHighlightInfo(substitutedRecordComponentType, deconstructionComponentType,
                                                                          deconstructionComponent.getTextRange(), 0);
          holder.add(builder.create());
        }
      }
      else {
        HighlightInfo.Builder info = getUncheckedPatternConversionError(deconstructionComponent);
        if (info != null) {
          hasMismatchedPattern = true;
          holder.add(info.create());
        }
      }
      if (recordComponents.length != deconstructionComponents.length && hasMismatchedPattern) {
        break;
      }
      if (deconstructionComponent instanceof PsiDeconstructionPattern) {
        createDeconstructionErrors((PsiDeconstructionPattern)deconstructionComponent, holder);
      }
    }
    if (recordComponents.length != deconstructionComponents.length) {
      HighlightInfo info = createIncorrectNumberOfNestedPatternsError(deconstructionPattern, deconstructionComponents, recordComponents,
                                                                      !hasMismatchedPattern);
      holder.add(info);
    }
  }

  static @Nullable HighlightInfo.Builder getUncheckedPatternConversionError(@NotNull PsiPattern pattern) {
    PsiType patternType = JavaPsiPatternUtil.getPatternType(pattern);
    if (patternType == null) return null;
    if (pattern instanceof PsiDeconstructionPattern subPattern) {
      PsiJavaCodeReferenceElement element = subPattern.getTypeElement().getInnermostComponentReferenceElement();
      if (element != null && element.getTypeParameterCount() == 0 && patternType instanceof PsiClassType classType) {
        patternType = classType.rawType();
      }
    }
    PsiType contextType = JavaPsiPatternUtil.getContextType(pattern);
    if (contextType == null) return null;
    if (contextType instanceof PsiWildcardType wildcardType) {
      contextType = wildcardType.getExtendsBound();
    }
    if (!JavaGenericsUtil.isUncheckedCast(patternType, contextType)) return null;
    String message = JavaErrorBundle.message("unsafe.cast.in.instanceof", contextType.getPresentableText(),
                                             patternType.getPresentableText());
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(pattern).descriptionAndTooltip(message);
  }

  private static boolean isApplicable(@NotNull PsiType recordType, @Nullable PsiType patternType) {
    if (recordType instanceof PsiPrimitiveType || patternType instanceof PsiPrimitiveType) {
      return recordType.equals(patternType);
    }
    return patternType != null && TypeConversionUtil.areTypesConvertible(recordType, patternType);
  }

  private static HighlightInfo createIncorrectNumberOfNestedPatternsError(@NotNull PsiDeconstructionPattern deconstructionPattern,
                                                                          PsiPattern @NotNull [] patternComponents,
                                                                          PsiRecordComponent @NotNull [] recordComponents,
                                                                          boolean needQuickFix) {
    assert patternComponents.length != recordComponents.length;
    String message = JavaErrorBundle.message("incorrect.number.of.nested.patterns", recordComponents.length, patternComponents.length);
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).description(message).escapedToolTip(message);
    PsiDeconstructionList deconstructionList = deconstructionPattern.getDeconstructionList();
    final IntentionAction fix;
    if (needQuickFix) {
      if (patternComponents.length < recordComponents.length) {
        builder.range(deconstructionList);
        var missingRecordComponents = Arrays.copyOfRange(recordComponents, patternComponents.length, recordComponents.length);
        var missingPatterns = ContainerUtil.map(missingRecordComponents, component -> Pattern.create(component, deconstructionList));
        fix = new AddMissingDeconstructionComponentsFix(deconstructionList, missingPatterns);
        builder.registerFix(fix, null, null, null, null);
      }
      else {
        PsiPattern[] deconstructionComponents = deconstructionList.getDeconstructionComponents();
        int endOffset = deconstructionList.getTextLength();
        int startOffset = deconstructionComponents[recordComponents.length].getStartOffsetInParent();
        TextRange textRange = TextRange.create(startOffset, endOffset);
        builder.range(deconstructionList, textRange);
        PsiPattern[] elementsToDelete = Arrays.copyOfRange(patternComponents, recordComponents.length, patternComponents.length);
        int diff = patternComponents.length - recordComponents.length;
        String text = QuickFixBundle.message("remove.redundant.nested.patterns.fix.text", diff);
        fix = QUICK_FIX_FACTORY.createDeleteFix(elementsToDelete, text);
        builder.registerFix(fix, null, text, null, null);
      }
    }
    else {
      builder.range(deconstructionList);
    }
    return builder.create();
  }

  @NotNull
  static RecordExhaustivenessResult checkRecordExhaustiveness(@NotNull List<? extends PsiCaseLabelElement> caseElements) {
    List<PsiDeconstructionPattern> deconstructions =
      ContainerUtil.mapNotNull(caseElements, element -> findUnconditionalDeconstruction(element));
    if (deconstructions.isEmpty()) {
      return RecordExhaustivenessResult.createExhausted(); //no deconstruction
    }
    PsiDeconstructionPattern scope = deconstructions.get(0);
    if (scope == null) {
      return RecordExhaustivenessResult.createExhausted(); //must be warned before
    }
    MultiMap<PsiType, PsiDeconstructionPattern> deconstructionGroups =
      ContainerUtil.groupBy(deconstructions, deconstruction -> deconstruction.getTypeElement().getType());

    RecordExhaustivenessResult result = RecordExhaustivenessResult.createExhausted();
    for (Map.Entry<PsiType, Collection<PsiDeconstructionPattern>> entry : deconstructionGroups.entrySet()) {
      PsiType type = entry.getKey();
      if (type == null) continue;
      PsiClassType.ClassResolveResult resolve = PsiUtil.resolveGenericsClassInType(PsiUtil.captureToplevelWildcards(type, scope));
      PsiClass selectorClass = resolve.getElement();
      PsiSubstitutor substitutor = resolve.getSubstitutor();
      if (selectorClass == null) continue;
      List<PsiType> recordTypes =
        ContainerUtil.map(selectorClass.getRecordComponents(), component -> substitutor.substitute(component.getType()));

      List<List<PsiPattern>> deconstructionComponentsGroup = ContainerUtil.map(entry.getValue(), deconstruction -> Arrays.asList(
        deconstruction.getDeconstructionList().getDeconstructionComponents()));
      if (ContainerUtil.exists(deconstructionComponentsGroup, group -> group.size() != recordTypes.size())) {
        return RecordExhaustivenessResult.createExhausted(); //must be warned before
      }
      RecordExhaustivenessResult currentResult = findExhaustiveInGroup(type, recordTypes, deconstructionComponentsGroup);
      result.merge(currentResult);
    }
    return result;
  }

  private static @Nullable PsiDeconstructionPattern findUnconditionalDeconstruction(PsiCaseLabelElement caseElement) {
    if (caseElement instanceof PsiParenthesizedPattern parenthesizedPattern) {
      return findUnconditionalDeconstruction(parenthesizedPattern.getPattern());
    }
    else if (caseElement instanceof PsiPatternGuard guarded) {
      Object constVal = ExpressionUtils.computeConstantExpression(guarded.getGuardingExpression());
      if (!Boolean.TRUE.equals(constVal)) return null;
      return findUnconditionalDeconstruction(guarded.getPattern());
    }
    else if (caseElement instanceof PsiDeconstructionPattern deconstructionPattern) {
      return deconstructionPattern;
    }
    else {
      return null;
    }
  }

  @NotNull
  private static RecordExhaustivenessResult findExhaustiveInGroup(@NotNull PsiType currentRecordType,
                                                                  @NotNull List<? extends PsiType> leftRecordTypes,
                                                                  @NotNull List<? extends List<PsiPattern>> deconstructions) {
    if (leftRecordTypes.isEmpty() || ContainerUtil.exists(deconstructions, t -> t.size() == 0)) {
      return RecordExhaustivenessResult.createExhausted(); //must be another error
    }
    PsiType typeToCheck = leftRecordTypes.get(0);
    if (typeToCheck == null) return RecordExhaustivenessResult.createExhausted(); //must be another error
    MultiMap<PsiType, List<PsiPattern>> groupedByType = ContainerUtil.groupBy(deconstructions,
                                                                              deconstructionComponents -> JavaPsiPatternUtil.getPatternType(
                                                                                deconstructionComponents.get(0)));
    List<GroupExhaustiveness> groupsExhaustiveness = getGroupsExhaustiveness(currentRecordType, leftRecordTypes, groupedByType);
    if (groupsExhaustiveness.isEmpty()) return RecordExhaustivenessResult.createNotBeAdded();
    List<PsiPattern> checkedExhaustedPatterns = new ArrayList<>();
    Map<PsiType, BranchesExhaustiveness> notExhausted = new LinkedHashMap<>();
    for (GroupExhaustiveness group : groupsExhaustiveness) {
      if (!group.branchesExhaustiveness().result().isExhausted()) {
        PsiType notExhaustedType = group.psiType();
        notExhausted.put(notExhaustedType, group.branchesExhaustiveness());
        continue;
      }
      Collection<List<PsiPattern>> lists = groupedByType.get(group.psiType());
      if (lists.size() == 0) continue;
      List<PsiPattern> next = lists.iterator().next();
      if (next == null || next.isEmpty()) continue;
      checkedExhaustedPatterns.add(next.get(0));
    }
    if (ContainerUtil.exists(checkedExhaustedPatterns, pattern -> isCoveredBy(typeToCheck, pattern))) {
      return RecordExhaustivenessResult.createExhausted(); // exhausted even without not-exhausted-subgroup
    }
    LinkedHashMap<PsiClass, PsiPattern> patternClasses = SwitchBlockHighlightingModel.findPatternClasses(checkedExhaustedPatterns);
    Set<PsiClass> missedClasses = SwitchBlockHighlightingModel.findMissedClasses(typeToCheck, patternClasses);
    if (missedClasses.isEmpty() && !patternClasses.isEmpty()) {
      return RecordExhaustivenessResult.createExhausted(); //exhausted even without not-exhausted-subgroup
    }
    //if one of them is unconditional, return any of them
    List<BranchesExhaustiveness> coveredPatterns =
      ContainerUtil.filter(notExhausted.values(), group -> group.branches().stream().flatMap(patterns -> patterns.stream())
        .anyMatch(pattern -> isCoveredBy(typeToCheck, pattern)));
    if (!coveredPatterns.isEmpty()) {
      RecordExhaustivenessResult nextResult = coveredPatterns.get(0).result();
      nextResult.addNextType(currentRecordType, typeToCheck);
      return nextResult;
    }
    return mergeMissedClasses(currentRecordType, leftRecordTypes, notExhausted, missedClasses);
  }

  private static boolean isCoveredBy(@NotNull PsiType typeToCheck, @Nullable PsiPattern pattern) {
    return JavaPsiPatternUtil.isUnconditionalForType(pattern, typeToCheck, true);
  }

  @NotNull
  private static RecordExhaustivenessResult mergeMissedClasses(@NotNull PsiType recordType,
                                                      @NotNull List<? extends PsiType> recordTypes,
                                                      @NotNull Map<PsiType, BranchesExhaustiveness> notExhaustedBranches,
                                                      @NotNull Set<PsiClass> missedClasses) {
    RecordExhaustivenessResult result = RecordExhaustivenessResult.createNotExhausted();
    for (PsiClass missedClass : missedClasses) {
      PsiClassType missedType = PsiTypesUtil.getClassType(missedClass);
      BranchesExhaustiveness branchesExhaustiveness = notExhaustedBranches.get(missedType);
      if (branchesExhaustiveness == null) {
        //There is a chance that existed branchExhaustiveness cover partially this new branch,
        //but let's not recalculate and make it simple and fast.
        //Otherwise, we have to process all classes in a permit list every time
        result.addNewBranch(recordType, missedType, recordTypes);
      }
      else {
        RecordExhaustivenessResult recordExhaustivenessResult = branchesExhaustiveness.result();
        recordExhaustivenessResult.addNextType(recordType, missedType);
        result.merge(recordExhaustivenessResult);
      }
    }
    return result;
  }

  private record GroupExhaustiveness(@NotNull PsiType psiType,
                                     @NotNull PatternHighlightingModel.BranchesExhaustiveness branchesExhaustiveness) {
    GroupExhaustiveness(@NotNull PsiType psiType, @NotNull RecordExhaustivenessResult result,
                        @NotNull Collection<List<PsiPattern>> branches) {
      this(psiType, new BranchesExhaustiveness(result, branches));
    }
  }

  private record BranchesExhaustiveness(@NotNull RecordExhaustivenessResult result,
                                        @NotNull Collection<List<PsiPattern>> branches) {
  }


  @NotNull
  private static List<GroupExhaustiveness> getGroupsExhaustiveness(@NotNull PsiType recordType,
                                                                   @NotNull List<? extends PsiType> recordTypes,
                                                                   @NotNull MultiMap<PsiType, List<PsiPattern>> groupedByType) {
    MultiMap<PsiType, List<PsiPattern>> deconstructionGroups = getDeconstructionGroupsByType(groupedByType);

    return ContainerUtil.map(deconstructionGroups.entrySet(), deconstructionGroup -> {
      if (ContainerUtil.exists(deconstructionGroup.getValue(), t -> t == null || t.isEmpty())) {
        return new GroupExhaustiveness(deconstructionGroup.getKey(),
                                       RecordExhaustivenessResult.createNotBeAdded(), deconstructionGroup.getValue());
      }
      List<PsiPattern> firstElements = ContainerUtil.map(deconstructionGroup.getValue(), it -> it.get(0));
      if (ContainerUtil.exists(firstElements, pattern -> pattern instanceof PsiDeconstructionPattern)) {
        if (!checkRecordExhaustiveness(firstElements).isExhausted()) {
          //support only first level
          return new GroupExhaustiveness(deconstructionGroup.getKey(), RecordExhaustivenessResult.createNotBeAdded(),
                                         deconstructionGroup.getValue());
        }
      }
      RecordExhaustivenessResult result = findExhaustiveInGroup(
        recordType, dropFirst(recordTypes), ContainerUtil.map(deconstructionGroup.getValue(), PatternHighlightingModel::dropFirst)
      );
      return new GroupExhaustiveness(deconstructionGroup.getKey(), result, deconstructionGroup.getValue());
    });
  }

  @NotNull
  private static MultiMap<PsiType, List<PsiPattern>> getDeconstructionGroupsByType(@NotNull MultiMap<PsiType, List<PsiPattern>> groupedByType) {
    MultiMap<PsiType, List<PsiPattern>> deconstructionGroups = MultiMap.create();
    Set<PsiType> types = new HashSet<>(groupedByType.keySet());
    for (PsiType currentType : types) {
      for (PsiType compareType : groupedByType.keySet()) {
        if (JavaPsiPatternUtil.dominates(compareType, currentType)) {
          deconstructionGroups.putValues(currentType, groupedByType.get(compareType));
        }
      }
    }
    return deconstructionGroups;
  }

  private static <T> List<T> dropFirst(List<T> list) {
    return list.subList(1, list.size());
  }

  static class RecordExhaustivenessResult {
    private boolean isExhausted;
    private boolean canBeAdded;

    private final Map<PsiType, Set<List<PsiType>>> missedBranchesByType = new HashMap<>();

    private RecordExhaustivenessResult(boolean exhausted, boolean added) {
      isExhausted = exhausted;
      canBeAdded = added;
    }

    public Map<PsiType, Set<List<PsiType>>> getMissedBranchesByType() {
      Map<PsiType, Set<List<PsiType>>> result = new HashMap<>();
      for (Map.Entry<PsiType, Set<List<PsiType>>> missedBranches : missedBranchesByType.entrySet()) {
        Set<List<PsiType>> branchSet = new HashSet<>();
        for (List<PsiType> missedBranch : missedBranches.getValue()) {
          List<PsiType> revertMissedBranch = new ArrayList<>(missedBranch);
          Collections.reverse(revertMissedBranch);
          branchSet.add(revertMissedBranch);
        }
        result.put(missedBranches.getKey(), branchSet);
      }
      return result;
    }

    public boolean isExhausted() {
      return isExhausted;
    }

    public boolean canBeAdded() {
      return canBeAdded;
    }

    public void merge(RecordExhaustivenessResult result) {
      if (!this.isExhausted && !this.canBeAdded) {
        return;
      }
      if (!result.isExhausted) {
        this.isExhausted = false;
      }
      if (!result.canBeAdded) {
        this.canBeAdded = false;
      }
      for (Map.Entry<PsiType, Set<List<PsiType>>> newEntry : result.missedBranchesByType.entrySet()) {
        missedBranchesByType.merge(newEntry.getKey(), newEntry.getValue(),
                                   (lists, lists2) -> {
                                     HashSet<List<PsiType>> result1 = new HashSet<>();
                                     result1.addAll(lists);
                                     result1.addAll(lists2);
                                     return result1;
                                   });
      }
      if (!this.canBeAdded) {
        missedBranchesByType.clear();
      }
    }

    public void addNextType(PsiType recordType, PsiType nextClass) {
      if (!this.canBeAdded) {
        return;
      }
      Set<List<PsiType>> branches = missedBranchesByType.get(recordType);
      if (branches == null) {
        return;
      }
      for (List<PsiType> classes : branches) {
        classes.add(nextClass);
      }
    }

    public void addNewBranch(@NotNull PsiType recordType,
                             @NotNull PsiType classForNextBranch,
                             @NotNull List<? extends PsiType> types) {
      if (!this.canBeAdded) {
        return;
      }
      List<PsiType> nextBranch = new ArrayList<>();
      for (int i = types.size() - 1; i >= 1; i--) {
        nextBranch.add(types.get(i));
      }
      nextBranch.add(classForNextBranch);
      HashSet<List<PsiType>> newBranchSet = new HashSet<>();
      newBranchSet.add(nextBranch);
      this.missedBranchesByType.merge(recordType, newBranchSet,
                                      (lists, lists2) -> {
                                        HashSet<List<PsiType>> set = new HashSet<>(lists);
                                        set.addAll(lists2);
                                        return set;
                                      });
    }

    static RecordExhaustivenessResult createExhausted() {
      return new RecordExhaustivenessResult(true, true);
    }

    static RecordExhaustivenessResult createNotExhausted() {
      return new RecordExhaustivenessResult(false, true);
    }

    static RecordExhaustivenessResult createNotBeAdded() {
      return new RecordExhaustivenessResult(false, false);
    }
  }
}
