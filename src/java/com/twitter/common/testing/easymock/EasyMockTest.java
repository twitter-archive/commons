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

package com.twitter.common.testing.easymock;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;

import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;
import com.google.common.testing.TearDown;
import com.google.common.testing.junit4.TearDownTestCase;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Before;

import static org.easymock.EasyMock.createControl;

/**
 * A baseclass for tests that use EasyMock.  A new {@link IMocksControl control} is set up before
 * each test and the mocks created and replayed with it are verified during tear down.
 *
 * @author John Sirois
 */
public abstract class EasyMockTest extends TearDownTestCase {
  protected IMocksControl control;

  /**
   * Creates an EasyMock {@link #control} for tests to use that will be automatically
   * {@link IMocksControl#verify() verified} on tear down.
   */
  @Before
  public final void setupEasyMock() {
    control = createControl();
    addTearDown(new TearDown() {
      @Override public void tearDown() {
        control.verify();
      }
    });
  }

  /**
   * Creates an EasyMock mock with this test's control.  Will be
   * {@link IMocksControl#verify() verified} in a tear down.
   */
  public <T> T createMock(Class<T> type) {
    Preconditions.checkNotNull(type);
    return control.createMock(type);
  }

  /**
   * A class meant to be sub-classed in order to capture a generic type literal value.  To capture
   * the type of a {@code List<String>} you would use: {@code new Clazz<List<String>>() {}}
   */
  public abstract static class Clazz<T> extends TypeToken {
    Class<T> rawType() {
      @SuppressWarnings("unchecked")
      Class<T> rawType = (Class<T>) findRawType();
      return rawType;
    }

    private Class<?> findRawType() {
      if (getType() instanceof Class<?>) { // Plain old
        return (Class<?>) getType();

      } else if (getType() instanceof ParameterizedType) { // Nested type parameter
        ParameterizedType parametrizedType = (ParameterizedType) getType();
        Type rawType = parametrizedType.getRawType();
        return (Class<?>) rawType;
      } else if (getType() instanceof GenericArrayType) {
        throw new IllegalStateException("cannot mock arrays, rejecting type: " + getType());
      } else if (getType() instanceof WildcardType) {
        throw new IllegalStateException(
            "wildcarded instantiations are not allowed in java, rejecting type: " + getType());
      } else {
        throw new IllegalArgumentException("Could not decode raw type for: " + getType());
      }
    }

    public T createMock() {
      return EasyMock.createMock(rawType());
    }

    public T createMock(IMocksControl control) {
      return control.createMock(rawType());
    }
  }

  /**
   * Creates an EasyMock mock with this test's control.  Will be
   * {@link IMocksControl#verify() verified} in a tear down.
   *
   * Allows for mocking of parameterized types without all the unchecked conversion warnings in a
   * safe way.
   */
  public <T> T createMock(Clazz<T> type) {
    Preconditions.checkNotNull(type);
    return type.createMock(control);
  }

  /**
   * A type-inferring convenience method for creating new captures.
   */
  public static <T> Capture<T> createCapture() {
    return new Capture<T>();
  }
}
