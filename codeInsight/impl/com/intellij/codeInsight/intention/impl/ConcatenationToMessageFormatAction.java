package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class ConcatenationToMessageFormatAction implements IntentionAction {
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.replace.concatenation.with.formatted.output.family");
  }

  public String getText() {
    return CodeInsightBundle.message("intention.replace.concatenation.with.formatted.output.text");
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    PsiBinaryExpression concatenation = getEnclosingConcatenation(file, editor);
    PsiManager manager = concatenation.getManager();
    StringBuffer formatString = new StringBuffer();
    List<PsiExpression> args = new ArrayList<PsiExpression>();
    ArrayList<PsiExpression> argsToCombine = new ArrayList<PsiExpression>();
    calculateFormatAndArguments(concatenation, formatString, args, argsToCombine, false);
    appendArgument(args, argsToCombine, formatString);

    PsiMethodCallExpression call = (PsiMethodCallExpression) manager.getElementFactory().createExpressionFromText("java.text.MessageFormat.format()", concatenation);
    PsiExpressionList argumentList = call.getArgumentList();
    String format = prepareString(formatString.toString());
    PsiExpression formatArgument = manager.getElementFactory().createExpressionFromText("\"" + format + "\"", null);
    argumentList.add(formatArgument);
    if (manager.getEffectiveLanguageLevel().compareTo(LanguageLevel.JDK_1_5) >= 0) {
      for (PsiExpression arg : args) {
        argumentList.add(arg);
      }
    } else {
      final PsiNewExpression arrayArg = (PsiNewExpression)manager.getElementFactory().createExpressionFromText("new java.lang.Object[]{}", null);
      final PsiArrayInitializerExpression arrayInitializer = arrayArg.getArrayInitializer();
      assert arrayInitializer != null;
      for (PsiExpression arg : args) {
        arrayInitializer.add(arg);
      }

      argumentList.add(arrayArg);
    }
    call = (PsiMethodCallExpression) manager.getCodeStyleManager().shortenClassReferences(call);
    call = (PsiMethodCallExpression) manager.getCodeStyleManager().reformat(call);
    concatenation.replace(call);
  }

  public static String prepareString(final String s) {
    return repeatSingleQuotes(StringUtil.escapeStringCharacters(s));
  }

  private static String repeatSingleQuotes(String s) {
    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      if (c == '\'') {
        buffer.append(c);
        buffer.append(c);
      } else {
        buffer.append(c);
      }
    }
    return buffer.toString();
  }

  public static boolean calculateFormatAndArguments(PsiExpression expression, StringBuffer formatString,
                                              List<PsiExpression> args, List<PsiExpression> argsToCombine,
                                              boolean wasLiteral) throws IncorrectOperationException {
    if (expression == null) return wasLiteral;
    if (expression instanceof PsiBinaryExpression) {
      final PsiType type = expression.getType();
      if (type != null && type.equalsToText("java.lang.String") &&
          ((PsiBinaryExpression) expression).getOperationSign().getTokenType() == JavaTokenType.PLUS) {
        wasLiteral = calculateFormatAndArguments(((PsiBinaryExpression) expression).getLOperand(), formatString, args, argsToCombine, wasLiteral);
        wasLiteral = calculateFormatAndArguments(((PsiBinaryExpression) expression).getROperand(), formatString, args, argsToCombine, wasLiteral);
      } else if (expression instanceof PsiLiteralExpression &&
                 ((PsiLiteralExpression) expression).getValue() instanceof String) {
        appendArgument(args, argsToCombine, formatString);
        argsToCombine.clear();
        formatString.append(((PsiLiteralExpression) expression).getValue());
        return true;
      } else {
        if (wasLiteral) {
          appendArgument(args, Collections.singletonList(expression), formatString);
        }
        else {
          argsToCombine.add(expression);
        }
      }
    } else if (expression instanceof PsiLiteralExpression &&
               ((PsiLiteralExpression) expression).getValue() instanceof String) {
      appendArgument(args, argsToCombine, formatString);
      argsToCombine.clear();
      formatString.append(((PsiLiteralExpression) expression).getValue());
      return true;
    } else {
      if (wasLiteral) {
        appendArgument(args, Collections.singletonList(expression), formatString);
      }
      else {
        argsToCombine.add(expression);
      }
    }

    return wasLiteral;
  }

  private static void appendArgument(List<PsiExpression> args, List<PsiExpression> argsToCombine, StringBuffer formatString) throws IncorrectOperationException {
    if (argsToCombine.size() == 0) return;
    PsiExpression argument = argsToCombine.get(0);
    final PsiManager manager = argument.getManager();
    final PsiElementFactory factory = manager.getElementFactory();
    for (int i = 1; i < argsToCombine.size(); i++) {
      PsiBinaryExpression newArg = (PsiBinaryExpression) factory.createExpressionFromText("a+b", null);
      newArg.getLOperand().replace(argument);
      PsiExpression rOperand = newArg.getROperand();
      assert rOperand != null;
      rOperand.replace(argsToCombine.get(i));
      argument = newArg;
    }

    formatString.append("{").append(args.size()).append("}");
    args.add(getBoxedArgument(argument));
  }

  private static PsiExpression getBoxedArgument(PsiExpression arg) throws IncorrectOperationException {
    arg = PsiUtil.deparenthesizeExpression(arg);
    final PsiManager manager = arg.getManager();
    final PsiElementFactory factory = manager.getElementFactory();
    if (manager.getEffectiveLanguageLevel().compareTo(LanguageLevel.JDK_1_5) < 0) {
      final PsiType type = arg.getType();
      if (type instanceof PsiPrimitiveType && !type.equals(PsiType.NULL)) {
        final PsiPrimitiveType primitiveType = (PsiPrimitiveType)type;
        final String boxedQName = PsiPrimitiveType.ourUnboxedToQName.get(primitiveType);
        if (boxedQName != null) {
          final GlobalSearchScope resolveScope = arg.getResolveScope();
          final PsiClass boxedClass = manager.findClass(boxedQName, resolveScope);
          if (boxedClass != null) {
            final PsiJavaCodeReferenceElement ref = factory.createReferenceExpression(boxedClass);
            final PsiMethodCallExpression valueOfExpr = (PsiMethodCallExpression)factory.createExpressionFromText("A.valueOf(b)", null);
            final PsiElement qualifier = valueOfExpr.getMethodExpression().getQualifier();
            assert qualifier != null;
            qualifier.replace(ref);
            valueOfExpr.getArgumentList().getExpressions()[0].replace(arg);
            return valueOfExpr;
          }
        }
      }
    }

    return arg;
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (PsiManager.getInstance(project).getEffectiveLanguageLevel().compareTo(LanguageLevel.JDK_1_4) < 0) return false;
    return getEnclosingConcatenation(file, editor) != null;
  }

  private PsiBinaryExpression getEnclosingConcatenation(PsiFile file, Editor editor) {
    final PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());
    return getEnclosingConcatenation(elementAt);
  }

  public static PsiBinaryExpression getEnclosingConcatenation(final PsiElement psiElement) {
    PsiBinaryExpression concatenation = null;
    final PsiLiteralExpression literal = PsiTreeUtil.getParentOfType(psiElement, PsiLiteralExpression.class, false);
    if (literal != null && literal.getValue() instanceof String) {
      PsiElement run = literal;
      while (run.getParent() instanceof PsiBinaryExpression &&
             ((PsiBinaryExpression) run.getParent()).getOperationSign().getTokenType() == JavaTokenType.PLUS) {
        concatenation = (PsiBinaryExpression) run.getParent();
        run = concatenation;
      }
    }
    return concatenation;
  }

  public boolean startInWriteAction() {
    return true;
  }
}
