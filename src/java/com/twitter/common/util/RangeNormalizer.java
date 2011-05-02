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

//************************************************************************
//
//                      Summize
//
// This work protected by US Copyright Law and contains proprietary and
// confidential trade secrets.
//
// (c) Copyright 2006 Summize,  ALL RIGHTS RESERVED.
//
//************************************************************************
package com.twitter.common.util;

/**
 * Generic range normalizer class. Values must be positive.
 *
 * @author Abdur Chowdhury
 */
public class RangeNormalizer {
  public RangeNormalizer(double minA, double maxA, double minB, double maxB) {
    _minA = minA;
    _maxA = maxA;
    _minB = minB;
    _maxB = maxB;
    _denominator = (_maxA - _minA);
    _B = (_maxB - _minB);
    _midB = minB + (_B / 2f);
  }

  public double normalize(double value) {
    // if no input range, return a mid range value
    if (_denominator == 0) {
      return _midB;
    }

    return ((value - _minA) / _denominator) * _B + _minB;
  }

  public static double normalize(double value, double minA, double maxA, double minB, double maxB) {
    // if the source min and max are equal, don't return 0, return something
    // in the target range (perhaps this "default" should be another argument)
    if (minA == maxA) {
      return minB;
    }

    return ((value - minA) / (maxA - minA)) * (maxB - minB) + minB;
  }

  public static float normalizeToStepDistribution(double rating) {
    int integerRating = (int) Math.round(rating);

    if (integerRating == 2) {
      integerRating = 1;
    } else if (integerRating == 4) {
      integerRating = 3;
    } else if (integerRating == 6) {
      integerRating = 5;
    } else if (integerRating == 8) {
      integerRating = 7;
    } else if (integerRating == 9) {
      integerRating = 10;
    }

    return (float) integerRating;
  }

  // *******************************************************************
  private double _denominator;
  private double _B;
  private double _minA = Double.MIN_VALUE;
  private double _maxA = Double.MAX_VALUE;
  private double _minB = Double.MIN_VALUE;
  private double _maxB = Double.MAX_VALUE;
  private double _midB = Double.MAX_VALUE;
}

