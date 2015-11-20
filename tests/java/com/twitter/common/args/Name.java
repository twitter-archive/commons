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

package com.twitter.common.args;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.google.common.base.Preconditions;

import com.twitter.common.args.parsers.NonParameterizedTypeParser;
import com.twitter.common.base.MorePreconditions;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * ArgParser annotation definitions must be defined in a separate compilation round than
 * they are used from.
 */
public class Name {
  private final String name;

  public Name(String name) {
    this.name = MorePreconditions.checkNotBlank(name);
  }

  public String getName() {
    return name;
  }

  @Override
  public int hashCode() {
    return this.name.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return (obj instanceof Name) && name.equals(((Name) obj).name);
  }

  @ArgParser
  public static class Parser extends NonParameterizedTypeParser<Name> {
    @Override public Name doParse(String raw) {
      return new Name(raw);
    }
  }

  @Target(FIELD)
  @Retention(RUNTIME)
  public static @interface Equals {
    String value();
  }

  @VerifierFor(Equals.class)
  public static class Same implements Verifier<Name> {
    @Override
    public void verify(Name value, Annotation annotation) {
      Preconditions.checkArgument(getValue(annotation).equals(value.getName()));
    }

    @Override
    public String toString(Class<? extends Name> argType, Annotation annotation) {
      return "name = " + getValue(annotation);
    }

    private String getValue(Annotation annotation) {
      return ((Equals) annotation).value();
    }
  }
}
