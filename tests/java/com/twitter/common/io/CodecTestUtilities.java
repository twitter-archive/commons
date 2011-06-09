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

package com.twitter.common.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

class CodecTestUtilities {
  static <T> byte[] serialize(Codec<T> codec, T item) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    codec.serialize(item, out);
    return out.toByteArray();
  }

  static <T> T deserialize(Codec<T> codec, byte[] serialized) throws IOException {
    return codec.deserialize(new ByteArrayInputStream(serialized));
  }

  static <T> T roundTrip(Codec<T> codec, T item) throws IOException {
    return deserialize(codec, serialize(codec, item));
  }
}
