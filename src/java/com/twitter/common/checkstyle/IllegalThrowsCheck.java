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

import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FullIdent;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.checks.coding.AbstractIllegalCheck;

/**
 * Relaxes the built-in checkstyle
 * {@link com.puppycrawl.tools.checkstyle.checks.coding.IllegalThrowsCheck} by only checking
 * non-overidden methods.
 *
 * @author John Sirois
 */
public class IllegalThrowsCheck extends AbstractIllegalCheck {

  /**
   * Creates a new check for illegal throws clauses..
   */
  public IllegalThrowsCheck() {
    super(new String[] {
      "Error",
      "RuntimeException",
      "Throwable",
      "java.lang.Error",
      "java.lang.RuntimeException",
      "java.lang.Throwable",
    });
  }

  @Override
  public int[] getDefaultTokens() {
    return new int[] {
        TokenTypes.METHOD_DEF
    };
  }

  @Override
  public int[] getRequiredTokens() {
    return getDefaultTokens();
  }

  @Override
  public void visitToken(DetailAST aDetailAST) {
    if (!CheckStyleUtils.isOverrideMethod(aDetailAST)) {
      DetailAST token = aDetailAST.findFirstToken(TokenTypes.LITERAL_THROWS);
      if (token != null) {
        checkThrows(token);
      }
    }
  }

  private void checkThrows(DetailAST throwsProduction) {
    DetailAST token = throwsProduction.getFirstChild();
    while (token != null) {
      if (token.getType() != TokenTypes.COMMA) {
        final FullIdent ident = FullIdent.createFullIdent(token);
        if (isIllegalClassName(ident.getText())) {
          log(token, "illegal.throw", ident.getText());
        }
      }

      token = token.getNextSibling();
    }
  }

  @Override
  protected String getMessageBundle() {
    return CheckStyleUtils.getMessageBundle(
        com.puppycrawl.tools.checkstyle.checks.coding.IllegalThrowsCheck.class);
  }
}
