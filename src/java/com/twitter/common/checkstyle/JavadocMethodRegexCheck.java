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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FileContents;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.api.Utils;
import com.puppycrawl.tools.checkstyle.checks.javadoc.JavadocMethodCheck;

/**
 * A small extension to the JavadocMethodCheck. Only two additions
 * are the ability to skip certain methods if they match a regex,
 * and skip methods if they are short enough (number of non-blank lines)
 * TODO(Alex Roetter): write a unittest
 *
 * @author Alex Roetter
 */
public class JavadocMethodRegexCheck extends JavadocMethodCheck {
  // Method names that match this pattern do not require javadoc blocks
  private Pattern methodNameIgnoreRegex;
  // Don't require javadoc for methods shorter than this many (non-blank)
  // lines
  private int minLineCount = -1;

  // Javadoc not required here due to method length
  public void setIgnoreMethodNamesRegex(String s) {
    methodNameIgnoreRegex = Utils.createPattern(s);
  }

  public void setMinLineCount(final int n) {
    minLineCount = n;
  }

  /**
   * Return true if the given method name matches the regex. In that case
   * we skip enforcement of javadoc for this method
   */
  private boolean matchesSkipRegex(final DetailAST aAST) {
    if (methodNameIgnoreRegex != null) {
      final DetailAST ident = aAST.findFirstToken(TokenTypes.IDENT);
      String methodName = ident.getText();

      Matcher matcher = methodNameIgnoreRegex.matcher(methodName);
      if (matcher.matches()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Return true iff the method is short enough that we don't require
   * a javadoc.
   */
  private boolean isShortEnoughToSkip(final DetailAST aAST) {
    // Based on code from
    // com/puppycrawl/tools/checkstyle/api/MethodLengthCheck.java
    final DetailAST openingBrace = aAST.findFirstToken(TokenTypes.SLIST);
    if (openingBrace != null) {
      final DetailAST closingBrace =
        openingBrace.findFirstToken(TokenTypes.RCURLY);
      int length = closingBrace.getLineNo() - openingBrace.getLineNo() + 1;

      // skip blank lines
      final FileContents contents = getFileContents();
      final int lastLine = closingBrace.getLineNo();
      for (int i = openingBrace.getLineNo() - 1; i < lastLine; i++) {
        if (contents.lineIsBlank(i)) {
          length--;
        }
      }
      if (length <= minLineCount) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected boolean isMissingJavadocAllowed(final DetailAST aAST) {
    return super.isMissingJavadocAllowed(aAST)
      || matchesSkipRegex(aAST)
      || isShortEnoughToSkip(aAST);
  }

  /**
   * For human readable error messages
   */
  @Override
  protected String getMessageBundle() {
    return CheckStyleUtils.getMessageBundle(JavadocMethodCheck.class);
  }
}
