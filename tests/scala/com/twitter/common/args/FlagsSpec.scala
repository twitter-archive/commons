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

package com.twitter.common.args

import org.specs.Specification

case class TopLevel(int: Int,  string: String)
case class WithAnnotations(
  @Flag(
    name = "queequeg",
    help = "Better sleep with a sober cannibal than a drunken Christian.")
  int: Int,
  @Flag(
    name = "starbuck",
    help = "And heaved and heaved, still unrestingly heaved the black sea, as if its vast tides " +
      "were a conscience; and the great mundane soul were in anguish and remorse for the long " +
      "sin and suffering it had bred.")
  string: String)

/**
 * @author nkallen
 */
class FlagsSpec extends Specification {
  case class Nested(int: Int,  string: String)

  "Flags" should {
    "work with top-level class" in {
      val result = Flags(TopLevel(101, "Nantucket"), Seq("-int=7685", "-string=Rokovoko"))
      result.int mustEqual 7685
      result.string mustEqual "Rokovoko"
    }

    "work with nested classes" in {
      val result = Flags(Nested(101, "Nantucket"), Seq("-int=7685", "-string=Rokovoko"))
      result.int mustEqual 7685
      result.string mustEqual "Rokovoko"
    }

    "work with annotations" in {
      val result = Flags(WithAnnotations(101, "Nantucket"), Seq("-queequeg=7685", "-starbuck=Rokovoko"))
      result.int mustEqual 7685
      result.string mustEqual "Rokovoko"
    }
  }
}
