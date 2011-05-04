// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================
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
