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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation for a command line argument.
 */
@Target(FIELD)
@Retention(RUNTIME)
public @interface CmdLine {
  /**
   * The short name of the argument, as supplied on the command line.  The argument can also be
   * accessed by the canonical name, which is {@code com.foo.bar.MyArgClass.arg_name}.
   * If the global scope contains more than one argument with the same name, all colliding arguments
   * must be disambiguated with the canonical form.
   *
   * The argument name must match the format {@code [\w\-\.]+}.
   */
  String name();

  /**
   * The help string to display on the command line in a usage message.
   */
  String help();

  /**
   * The parser class to use for parsing this argument.  The parser must return the same type as
   * the field being annotated.
   */
  Class<? extends Parser<?>> parser() default NoParser.class;

  /**
   * The flag to indicate whether an argument file is accepted for this argument.
   *
   */
  boolean argFile() default false;
}
