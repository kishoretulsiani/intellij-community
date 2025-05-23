/*
 * Copyright 2008-2017 Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.logging;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class LoggingConditionDisagreesWithLogStatementInspection extends BaseInspection {

  private static final @NonNls Set<String> loggingLevels = Set.of(
    "debug", "error", "fatal", "info", "trace", "warn", "severe", "warning", "config", "fine", "finer", "finest"
  );

  private static final Map<String, LoggingProblemChecker> problemCheckers = new HashMap<>();

  static {
    final Log4jLikeProblemChecker checker = new Log4jLikeProblemChecker();
    problemCheckers.put("org.apache.log4j.Category", checker);
    problemCheckers.put("org.apache.logging.log4j.Logger", checker);
    problemCheckers.put("org.apache.commons.logging.Log", checker);
    problemCheckers.put("org.slf4j.Logger", checker);
    problemCheckers.put("java.util.logging.Logger", new JavaUtilLoggingProblemChecker());
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("logging.condition.disagrees.with.log.statement.problem.descriptor", infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LoggingConditionDisagreesWithLogStatementVisitor();
  }

  private static class LoggingConditionDisagreesWithLogStatementVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      if (referenceName == null) {
        return;
      }
      final String loggingLevel;
      if (!loggingLevels.contains(referenceName)) {
        if (!"log".equals(referenceName)) {
          return;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length < 2) {
          return;
        }
        final PsiExpression argument = arguments[0];
        loggingLevel = JavaUtilLoggingProblemChecker.getLoggingLevelFromArgument(argument);
        if (loggingLevel == null) {
          return;
        }
      }
      else {
        loggingLevel = referenceName;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof PsiExpressionStatement)) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      final PsiIfStatement ifStatement;
      if (grandParent instanceof PsiCodeBlock) {
        final PsiElement greatGrandParent = grandParent.getParent();
        if (!(greatGrandParent instanceof PsiBlockStatement)) {
          return;
        }
        final PsiElement greatGreatGrandParent = greatGrandParent.getParent();
        if (!(greatGreatGrandParent instanceof PsiIfStatement)) {
          return;
        }
        ifStatement = (PsiIfStatement)greatGreatGrandParent;
      }
      else if (grandParent instanceof PsiIfStatement) {
        ifStatement = (PsiIfStatement)grandParent;
      }
      else {
        return;
      }
      PsiExpression condition = ifStatement.getCondition();
      if (condition instanceof PsiMethodCallExpression) {
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        if (!PsiTreeUtil.isAncestor(thenBranch, expression, false)) {
          return;
        }
      }
      else if (condition instanceof PsiPrefixExpression prefixExpression) {
        if (!JavaTokenType.EXCL.equals(prefixExpression.getOperationTokenType())) {
          return;
        }
        final PsiStatement elseBranch = ifStatement.getElseBranch();
        if (!PsiTreeUtil.isAncestor(elseBranch, expression, false)) {
          return;
        }
        condition = prefixExpression.getOperand();
        if (!(condition instanceof PsiMethodCallExpression)) {
          return;
        }
      }
      else {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression referenceExpression)) {
        return;
      }
      final PsiElement target = referenceExpression.resolve();
      if (target == null) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)condition;
      final PsiReferenceExpression conditionMethodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression conditionQualifier = conditionMethodExpression.getQualifierExpression();
      if (!(conditionQualifier instanceof PsiReferenceExpression conditionReferenceExpression)) {
        return;
      }
      final PsiElement conditionTarget = conditionReferenceExpression.resolve();
      if (!target.equals(conditionTarget)) {
        return;
      }
      LoggingProblemChecker problemChecker = null;
      for (String superClassName : problemCheckers.keySet()) {
        if (InheritanceUtil.isInheritor(containingClass, superClassName)) {
          problemChecker = problemCheckers.get(superClassName);
          break;
        }
      }
      if (problemChecker == null || !problemChecker.hasLoggingProblem(loggingLevel, methodCallExpression)) {
        return;
      }
      registerMethodCallError(methodCallExpression, loggingLevel);
    }
  }

  interface LoggingProblemChecker {

    boolean hasLoggingProblem(String priority, PsiMethodCallExpression methodCallExpression);
  }

  private static class JavaUtilLoggingProblemChecker implements LoggingProblemChecker {

    @Override
    public boolean hasLoggingProblem(String priority, PsiMethodCallExpression methodCallExpression) {
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!"isLoggable".equals(methodName)) {
        return false;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return false;
      }
      final PsiExpression argument = arguments[0];
      final String loggingLevel = getLoggingLevelFromArgument(argument);
      if (loggingLevel == null) {
        return false;
      }
      return !loggingLevel.equals(priority);
    }

    @Nullable
    public static String getLoggingLevelFromArgument(PsiExpression argument) {
      if (!(argument instanceof PsiReferenceExpression argumentReference)) {
        return null;
      }
      if (!"java.util.logging.Level".equals(TypeUtils.resolvedClassName(argument.getType()))) {
        return null;
      }
      final PsiElement argumentTarget = argumentReference.resolve();
      if (!(argumentTarget instanceof PsiField field)) {
        return null;
      }
      return StringUtil.toLowerCase(field.getName());
    }
  }

  private static class Log4jLikeProblemChecker implements LoggingProblemChecker {

    @Override
    public boolean hasLoggingProblem(@NonNls String priority, PsiMethodCallExpression methodCallExpression) {
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if ("isDebugEnabled".equals(methodName)) {
        return !"debug".equals(priority);
      }
      else if ("isInfoEnabled".equals(methodName)) {
        return !"info".equals(priority);
      }
      else if ("isTraceEnabled".equals(methodName)) {
        return !"trace".equals(priority);
      }
      else if ("isWarnEnabled".equals(methodName)) {
        return !"warn".equals(priority);
      }
      else if ("isErrorEnabled".equals(methodName)) {
        return !"error".equals(priority);
      }
      else if ("isFatalEnabled".equals(methodName)) {
        return !"fatal".equals(priority);
      }
      else if ("isEnabled".equals(methodName)) {
        final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        final PsiExpression argument = arguments[0];
        if (!(argument instanceof PsiReferenceExpression referenceExpression)) {
          return false;
        }
        if (!InheritanceUtil.isInheritor(argument.getType(), "org.apache.logging.log4j.Level")) {
          return false;
        }
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiField field)) {
          return false;
        }
        final String fieldName = field.getName();
        return !StringUtil.toLowerCase(fieldName).equals(priority);
      }
      else if ("isEnabledFor".equals(methodName)) {
        final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        for (PsiExpression argument : arguments) {
          if (!(argument instanceof PsiReferenceExpression argumentReference)) {
            continue;
          }
          final PsiType type = argument.getType();
          if (!(type instanceof PsiClassType classType)) {
            continue;
          }
          final PsiClass aClass = classType.resolve();
          if (!InheritanceUtil.isInheritor(aClass, "org.apache.log4j.Priority")) {
            continue;
          }
          final PsiElement argumentTarget = argumentReference.resolve();
          if (!(argumentTarget instanceof PsiField field)) {
            continue;
          }
          final String fieldName = field.getName();
          return !StringUtil.toLowerCase(fieldName).equals(priority);
        }
      }
      return false;
    }
  }
}
