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

namespace py gen.twitter.thrift.text.testing

const i32 LUCKY_NUMBER = 7;

enum Color {
  RED = 0,
  GREEN = 1,
  BLUE = 3
}

struct InnerTestStruct {
  1: optional string foo,
  2: required Color color,
  3: optional map<i32, string> numbers
}

struct TestStruct {
  1: optional i32 field1 = LUCKY_NUMBER,
  2: required bool field2,
  3: optional string field3 = "default",
  4: optional list<i16> field4,
  5: optional set<string> field5,
  6: optional InnerTestStruct field6,
  7: required double field7
}
