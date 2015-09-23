// =================================================================================================
// Copyright 2012 Twitter, Inc.
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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation to mark an {@link Arg} for gathering the positional command line arguments.
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface Positional {
  /**
   * The help string to display on the command line in a usage message.
   */
  String help();

  /**
   * The parser class to use for parsing the positional arguments.  The parser must return the same
   * type as the field being annotated.
   */
  Class<? extends Parser<?>> parser() default NoParser.class;

  // TODO: https://github.com/twitter/commons/issues/353, Consider to add argFile for positional.
}
