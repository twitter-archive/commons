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

package com.twitter.common.quantity;

import com.google.common.base.Preconditions;

import com.twitter.common.collections.Pair;

/**
 * Represents a value in a unit system and facilitates unambiguous communication of amounts.
 * Instances are created via static factory {@code of(...)} methods.
 *
 * @param <T> the type of number the amount value is expressed in
 * @param <U> the type of unit that this amount quantifies
 *
 * @author John Sirois
 */
public abstract class Amount<T extends Number & Comparable<T>, U extends Unit<U>>
    implements Comparable<Amount<T, U>> {

  /**
   * Thrown when a checked operation on an amount would overflow.
   */

  public static class TypeOverflowException extends RuntimeException {
    public TypeOverflowException() {
      super();
    }
  }

  private final Pair<T, U> amount;
  private final T maxValue;

  private Amount(T value, U unit, T maxValue) {
    Preconditions.checkNotNull(value);
    Preconditions.checkNotNull(unit);
    this.maxValue = maxValue;
    this.amount = Pair.of(value, unit);
  }

  public T getValue() {
    return amount.getFirst();
  }

  public U getUnit() {
    return amount.getSecond();
  }

  public T as(U unit) {
    return asUnit(unit);
  }

  /**
   * Throws TypeOverflowException if an overflow occurs during scaling.
   */
  public T asChecked(U unit) {
    T retVal = asUnit(unit);
    if (retVal.equals(maxValue)) {
      throw new TypeOverflowException();
    }
    return retVal;
  }

  private T asUnit(Unit<?> unit) {
    return sameUnits(unit) ? getValue() : scale(getUnit().multiplier() / unit.multiplier());
  }

  @Override
  public int hashCode() {
    return amount.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Amount)) {
      return false;
    }

    Amount<?, ?> other = (Amount<?, ?>) obj;
    return amount.equals(other.amount) || isSameAmount(other);
  }

  private boolean isSameAmount(Amount<?, ?> other) {
    // Equals allows Object - so we have no compile time check that other has the right value type;
    // ie: make sure they don't have Integer when we have Long.
    Number value = other.getValue();
    if (!getValue().getClass().isInstance(value)) {
      return false;
    }

    Unit<?> unit = other.getUnit();
    if (!getUnit().getClass().isInstance(unit)) {
      return false;
    }

    @SuppressWarnings("unchecked")
    U otherUnit = (U) other.getUnit();
    return isSameAmount(other, otherUnit);
  }

  private boolean isSameAmount(Amount<?, ?> other, U otherUnit) {
    // Compare in the more precise unit (the one with the lower multiplier).
    if (otherUnit.multiplier() > getUnit().multiplier()) {
      return getValue().equals(other.asUnit(getUnit()));
    } else {
      return as(otherUnit).equals(other.getValue());
    }
  }

  @Override
  public String toString() {
    return amount.toString();
  }

  @Override
  public int compareTo(Amount<T, U> other) {
    // Compare in the more precise unit (the one with the lower multiplier).
    if (other.getUnit().multiplier() > getUnit().multiplier()) {
      return getValue().compareTo(other.as(getUnit()));
    } else {
      return as(other.getUnit()).compareTo(other.getValue());
    }
  }

  private boolean sameUnits(Unit<? extends Unit<?>> unit) {
    return getUnit().equals(unit);
  }

  protected abstract T scale(double multiplier);

  /**
   * Creates an amount that uses a {@code double} value.
   *
   * @param number the number of units the returned amount should quantify
   * @param unit the unit the returned amount is expressed in terms of
   * @param <U> the type of unit that the returned amount quantifies
   * @return an amount quantifying the given {@code number} of {@code unit}s
   */
  public static <U extends Unit<U>> Amount<Double, U> of(double number, U unit) {
    return new Amount<Double, U>(number, unit, Double.MAX_VALUE) {
      @Override protected Double scale(double multiplier) {
        return getValue() * multiplier;
      }
    };
  }

  /**
   * Creates an amount that uses a {@code float} value.
   *
   * @param number the number of units the returned amount should quantify
   * @param unit the unit the returned amount is expressed in terms of
   * @param <U> the type of unit that the returned amount quantifies
   * @return an amount quantifying the given {@code number} of {@code unit}s
   */
  public static <U extends Unit<U>> Amount<Float, U> of(float number, U unit) {
    return new Amount<Float, U>(number, unit, Float.MAX_VALUE) {
      @Override protected Float scale(double multiplier) {
        return (float) (getValue() * multiplier);
      }
    };
  }

  /**
   * Creates an amount that uses a {@code long} value.
   *
   * @param number the number of units the returned amount should quantify
   * @param unit the unit the returned amount is expressed in terms of
   * @param <U> the type of unit that the returned amount quantifies
   * @return an amount quantifying the given {@code number} of {@code unit}s
   */
  public static <U extends Unit<U>> Amount<Long, U> of(long number, U unit) {
    return new Amount<Long, U>(number, unit, Long.MAX_VALUE) {
      @Override protected Long scale(double multiplier) {
        return (long) (getValue() * multiplier);
      }
    };
  }

  /**
   * Creates an amount that uses an {@code int} value.
   *
   * @param number the number of units the returned amount should quantify
   * @param unit the unit the returned amount is expressed in terms of
   * @param <U> the type of unit that the returned amount quantifies
   * @return an amount quantifying the given {@code number} of {@code unit}s
   */
  public static <U extends Unit<U>> Amount<Integer, U> of(int number, U unit) {
    return new Amount<Integer, U>(number, unit, Integer.MAX_VALUE) {
      @Override protected Integer scale(double multiplier) {
        return (int) (getValue() * multiplier);
      }
    };
  }
}
