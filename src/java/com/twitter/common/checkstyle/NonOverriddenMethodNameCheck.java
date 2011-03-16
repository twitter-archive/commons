package com.twitter.common.checkstyle;

import com.puppycrawl.tools.checkstyle.api.AnnotationUtility;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.checks.naming.MethodNameCheck;

/**
 * An extension to method name check that does not check-style the name of an overridden method
 * because the programmer does not have a choice in renaming such methods.
 * If the interface/base class definition is checkstyled, the bad name will always be caught
 * over there.
 *
 * @author Utkarsh Srivastava
 */
public class NonOverriddenMethodNameCheck extends MethodNameCheck {
  /**
   * {@link Override Override} annotation name.
   */
  private static final String OVERRIDE = "Override";

  /**
   * Fully-qualified {@link Override Override} annotation name.
   */
  private static final String FQ_OVERRIDE = "java.lang." + OVERRIDE;

  // Based on code in com.puppycrawl.tools.checkstyle.checks.annotation.MissingOverrideCheck
  @Override
  public void visitToken(DetailAST aAST) {
    if (!AnnotationUtility.containsAnnotation(aAST, OVERRIDE)
        && !AnnotationUtility.containsAnnotation(aAST, FQ_OVERRIDE)) {
      super.visitToken(aAST);
    }
  }

  /**
   * For human readable error messages
   */
  @Override
  protected String getMessageBundle() {
    return CheckStyleUtils.getMessageBundle(MethodNameCheck.class);
  }
}
