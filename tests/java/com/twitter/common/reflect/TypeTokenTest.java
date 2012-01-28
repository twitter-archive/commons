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

package com.twitter.common.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Supplier;
import com.google.common.testing.junit4.JUnitAsserts;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author John Sirois
 */
public class TypeTokenTest {

  public static final Supplier<String> STRING_SUPPLIER_FIELD = null;
  private static Field stringSupplierField;

  @BeforeClass
  public static void getFields() throws Exception {
    stringSupplierField = TypeTokenTest.class.getField("STRING_SUPPLIER_FIELD");
  }

  @Test
  public void testTypeCapture() {
    TypeToken<List<? extends Number>> numberListTypeToken =
        new TypeToken<List<? extends Number>>() { };
    Type numberListType = numberListTypeToken.getType();

    assertTrue(numberListType instanceof ParameterizedType);
    ParameterizedType listType = (ParameterizedType) numberListType;
    assertEquals(List.class, listType.getRawType());

    Type[] typeArguments = listType.getActualTypeArguments();
    assertEquals(1, typeArguments.length);
    Type listTypeArgument = typeArguments[0];

    assertTrue(listTypeArgument instanceof WildcardType);

    Type[] lowerBounds = ((WildcardType) listTypeArgument).getLowerBounds();
    assertEquals(0, lowerBounds.length);

    Type[] upperBounds = ((WildcardType) listTypeArgument).getUpperBounds();
    assertEquals(1, upperBounds.length);
    assertEquals(Number.class, upperBounds[0]);
  }

  static class MyType<T extends Number> extends TypeToken<T> { }

  @Test
  public void testIndirectCapture() {
    MyType<AtomicInteger> typeTokenSubclassInstance = new MyType<AtomicInteger>() { };
    assertEquals(AtomicInteger.class, typeTokenSubclassInstance.getType());
  }

  @Test
  public void testEquals() {
    TypeToken<String> stringTypeToken = new TypeToken<String>() { };
    assertEquals(stringTypeToken, stringTypeToken);
    assertEquals(stringTypeToken, new TypeToken<String>() { });

    JUnitAsserts.assertNotEqual(new TypeToken<Character>() { }, new TypeToken<String>() { });
    JUnitAsserts.assertNotEqual(new TypeToken<Supplier>() { },
        new TypeToken<Supplier<String>>() { });
    JUnitAsserts.assertNotEqual(new TypeToken<Supplier>() { }, new TypeToken<Supplier<?>>() { });
  }

  @Test(expected = IllegalStateException.class)
  public void testUnparameterized() {
    new TypeToken() { };
  }

  @Test
  public void testCreate() throws Exception {
    assertEquals(new TypeToken<String>() { }, TypeToken.create(String.class));

    assertEquals(new TypeToken<Supplier>() { }, TypeToken.create(Supplier.class));
    assertEquals(new TypeToken<Supplier>() { }, TypeToken.create(stringSupplierField.getType()));
    assertEquals(new TypeToken<Supplier<String>>() { },
        TypeToken.create(stringSupplierField.getGenericType()));
  }

  @Test
  public void testExtractTypeToken() {
    assertEquals(new TypeToken<String>() { },
        TypeToken.create(TypeToken.extractTypeToken(stringSupplierField.getGenericType())));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testExtractTypeTokenUnparameterizedType() {
    TypeToken.extractTypeToken(String.class);
  }
}
