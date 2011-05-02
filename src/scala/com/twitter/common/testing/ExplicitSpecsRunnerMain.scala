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

/**
 * A specs runner that accepts an explicit list of spec source files to run the specs from.  This
 * runner is configured via the command line with 2 required system properties:
 * <ul>
 * <li>{@code specs.base.dir} - the base directory to calculate spec source file paths relative to
 * <li>{@code specs.path.list} - a comma-separated list of spec paths relative to
 * {@code specs.base.dir}
 * </ul>
 *
 * @author jsirois
 */
object ExplicitSpecsRunnerMain extends SpecsFileRunner("", ".*") {
  override def specificationNames(path: String, pattern: String) : List[String] = {
    val baseDir = new File(System.getProperty("specs.base.dir"))
    val specPaths = System.getProperty("specs.path.list").split(",")

    val result = new Queue[String]
    specPaths.foreach { specPath =>
      if (!specPath.isEmpty()) {
        val path = new File(baseDir, specPath).getPath
        collectSpecifications(result, path, pattern)
      }
    }
    result.toList
  }
}

