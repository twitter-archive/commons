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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;

/**
 * Utility methods for working with Suppliers.
 *
 * @author John Sirois
 */
public final class MoreSuppliers {

  private MoreSuppliers() {
    // utility
  }

  /**
   * Creates a Supplier that uses the no-argument constructor of {@code type} to supply new
   * instances.
   *
   * @param type the type of object this supplier creates
   * @param <T> the type of object this supplier creates
   * @return a Supplier that created a new obeject of type T on each call to {@link Supplier#get()}
   * @throws IllegalArgumentException if the given {@code type} does not have a no-arg constructor
   */
  public static <T> Supplier<T> of(final Class<? extends T> type) {
    Preconditions.checkNotNull(type);

    try {
      final Constructor<? extends T> constructor = getNoArgConstructor(type);
      return new Supplier<T>() {
        @Override public T get() {
          try {
            return constructor.newInstance();
          } catch (InstantiationException e) {
            throw instantiationFailed(e, type);
          } catch (IllegalAccessException e) {
            throw instantiationFailed(e, type);
          } catch (InvocationTargetException e) {
            throw instantiationFailed(e, type);
          }
        }
      };
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("No accessible no-arg constructor for " + type, e);
    }
  }

  private static RuntimeException instantiationFailed(Exception cause, Object type) {
    return new RuntimeException("Could not create a new instance of type: " + type, cause);
  }

  private static <T> Constructor<T> getNoArgConstructor(Class<T> type)
    throws NoSuchMethodException {

    try {
      Constructor<T> constructor = type.getConstructor();
      if (!MoreSuppliers.class.getPackage().equals(type.getPackage())
          && !Modifier.isPublic(type.getModifiers())) {
        // Handle a public no-args constructor in a non-public class
        constructor.setAccessible(true);
      }
      return constructor;
    } catch (NoSuchMethodException e) {
      Constructor<T> declaredConstructor = type.getDeclaredConstructor();
      declaredConstructor.setAccessible(true);
      return declaredConstructor;
    }
  }

  /**
   * Returns an {@link ExceptionalSupplier} that always supplies {@code item} without error.
   *
   * @param item The item to supply.
   * @param <T> The type of item being supplied.
   * @return A supplier that will always supply {@code item}.
   */
  public static <T> Supplier<T> ofInstance(@Nullable final T item) {
    return new Supplier<T>() {
      @Override public T get() {
        return item;
      }
    };
  }
}
