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

package com.twitter.common.testing

import java.io.File
import org.specs.runner.SpecsFileRunner
import scala.collection.mutable.Queue
import scala.io.Source

/**
 * A specs runner that accepts an explicit list of spec source files to run the specs from.  This
 * runner is configured via the command line with either 2 required system properties:
 * <ul>
 * <li>`specs.base.dir` - the base directory to calculate spec source file paths relative to
 * <li>`specs.path.list` - a comma-separated list of spec paths relative to `specs.base.dir`
 * </ul>
 *
 * Or via a flag:
 * <ul>
 * <li>`--specs` - a comma-separated list of full spec paths, @-prefixed argument
 * file paths or specs class names
 * </ul>
 */
object ExplicitSpecsRunnerMain extends SpecsFileRunner("", ".*") {
  private[this] def mapFlags(): Map[String, String] = {
    Map(args filter { _.startsWith("--") } map { _.split("=", 2) } flatMap {
      _ match {
        case Array(flag, value) => Some(flag, value)
        case _ => None
      }
    }:_*)
  }

  private[this] def specIds() = {
    def parseArgFile(path: String) = {
      Source.fromFile(path).getLines().toSeq flatMap { _.split("\\s+") } filter { !_.isEmpty }
    }

    def parseList(string: String) = string.split("\\s*,\\s*") filter { !_.isEmpty }

    val base = System.getProperty("specs.base.dir")
    if (base != null) {
      parseList(System.getProperty("specs.path.list", "")) map { new File(base, _).getPath }
    } else {
      mapFlags().get("--specs") match {
        case Some(list) => parseList(list) flatMap { item: String =>
          if (item.startsWith("@")) {
            parseArgFile(item.substring(1))
          } else {
            Some(item)
          }
        }
        case None =>
          val main = getClass.getName
          println("""usage:
                    |  java -Dspecs.base.dir=PATH -Dspecs.base.list=LIST %s
                    |  java %s --specs=LIST
                  """.format(main, main).stripMargin)
          println("""If using --specs, elements in the list prefixed with @ are considered
                    |arg file paths and these will be loaded and the whitespace delimited arguments
                    |found inside added to the list.  List items can be either paths to files
                    |containing specs or specs class names.
                  """.stripMargin)
          println("Must supply either -Dspecs.base.dir or --specs")
          exit(1)
      }
    }
  }

  override def specificationNames(path: String, pattern: String) : List[String] = {
    val result = new Queue[String]
    for (specId <- specIds()) {
      if (specId.endsWith(".scala")) {
        collectSpecifications(result, specId, pattern)
      } else {
        result += specId
      }
    }
    result.toList
  }
}
