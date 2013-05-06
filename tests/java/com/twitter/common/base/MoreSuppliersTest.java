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

package com.twitter.common.base;

import com.google.common.base.Preconditions;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * @author John Sirois
 */
public class MoreSuppliersTest {
  private static final class PrivateClassPublicConstructor {
    public PrivateClassPublicConstructor() { }
  }

  private static final class PrivateClassPrivateConstructor {
    private PrivateClassPrivateConstructor() { }
  }

  public static final class PublicClassPublicConstructor {
    public PublicClassPublicConstructor() { }
  }

  public static final class PublicClassPrivateConstructor {
    private PublicClassPrivateConstructor() { }
  }

  @Test
  public void testOfVisibilitiesHandled() throws Exception {
    testOfForType(PrivateClassPublicConstructor.class);
    testOfForType(PrivateClassPrivateConstructor.class);
    testOfForType(PublicClassPublicConstructor.class);
    testOfForType(PublicClassPrivateConstructor.class);
  }

  private void testOfForType(Class<?> type) {
    Supplier<Object> supplier = MoreSuppliers.of(type);
    Object object = supplier.get();
    assertNotNull(object);
    assertNotSame(object, supplier.get());
  }

  static class NoNoArgConstructor {
    NoNoArgConstructor(String unused) { }
  }

  @Test(expected = NullPointerException.class)
  public void testNullArgumentRejected() {
    MoreSuppliers.of(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNoNoArgFailsFast() {
    MoreSuppliers.of(NoNoArgConstructor.class);
  }

  @Test
  public void testOfInstance() {
    class ValueEquals {
      private final String value;

      ValueEquals(String value) {
        this.value = Preconditions.checkNotNull(value);
      }

      @Override
      public boolean equals(Object o) {
        return value.equals(((ValueEquals) o).value);
      }

      @Override
      public int hashCode() {
        return value.hashCode();
      }
    }

    Supplier<ValueEquals> nullSupplier = MoreSuppliers.ofInstance(new ValueEquals("jake"));
    ValueEquals actual = nullSupplier.get();
    assertEquals(new ValueEquals("jake"), actual);
    assertSame(actual, nullSupplier.get());
  }

  @Test
  public void testOfInstanceNullable() {
    Supplier<String> nullSupplier = MoreSuppliers.ofInstance(null);
    assertNull(nullSupplier.get());
    assertNull(nullSupplier.get());
  }
}
