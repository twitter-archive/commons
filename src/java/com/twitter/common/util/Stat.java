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

/**    ************************************************************************
 **	Summize
 **	This work protected by US Copyright Law and contains proprietary and
 **	confidential trade secrets.
 **	(c) Copyright 2007 Summize,  ALL RIGHTS RESERVED.
 **	************************************************************************/
package com.twitter.common.util;

//***************************************************************
//

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.text.NumberFormat;

/**
 * This class is designed to provide basic statistics collection.
 * For each instance of this object statistics and be added to it
 * then the sum, mean, std dev, min and max can be gathered at the
 * end. To reuse this object, a clear method can be called to reset
 * the statistics.
 */
public class Stat implements Serializable {

  /**
   * Add a number to the statistics collector.
   * doubles are used for all collections.
   *
   * @param x number added to the statistics.
   */
  public void addNumber(int x) {
    addNumber((double) x);
  }

  /**
   * Add a number to the statistics collector.
   * doubles are used for all collections.
   *
   * @param x number added to the statistics.
   */
  public void addNumber(float x) {
    addNumber((double) x);
  }

  /**
   * Add a number to the statistics collector.
   * doubles are used for all collections.
   *
   * @param x number added to the statistics.
   */
  public synchronized void addNumber(double x) {
    if (_max < x) {
      _max = x;
    }
    if (_min > x) {
      _min = x;
    }

    _sum += x;
    _sumOfSq += (x * x);
    _number++;

    return;
  }


  /**
   * Clear the statistics counters...
   */
  public void clear() {
    _max = 0;
    _min = Double.MAX_VALUE;
    _number = 0;
    _mean = 0;
    _stdDev = 0;
    _sum = 0;
    _sumOfSq = 0;
  }


  /**
   * Create a string representation of the
   * statistics collected so far. NOTE this
   * is formatted and may not suit all needs
   * and thus the user should just call the
   * needed methods to get mean, std dev, etc.
   * and format the data as needed.
   *
   * @return String Java string formatted output of results.
   */
  public String toString() {
    return toString(false);
  }


  /**
   * Create a string representation of the
   * statistics collected so far. The results
   * are formatted in percentage format if
   * passed in true, otherwise the results
   * are the same as the toString call. NOTE this
   * is formatted and may not suit all needs
   * and thus the user should just call the
   * needed methods to get mean, std dev, etc.
   * and format the data as needed.
   *
   * @param percent Format as percentages if set to true.
   * @return String Java string formatted output of results.
   */
  public String toString(boolean percent) {
    calculate();
    NumberFormat nf = NumberFormat.getInstance();
    nf.setMaximumFractionDigits(4);

    if (_number > 1) {
      StringBuffer results = new StringBuffer();
      if (percent) {
        results.append("Number:" + nf.format(_number * 100) + "%");
      } else {
        results.append("Number:" + nf.format(_number));
      }

      if (percent) {
        results.append(" Max:" + nf.format(_max * 100) + "%");
      } else {
        results.append(" Max:" + nf.format(_max));
      }

      if (percent) {
        results.append(" Min:" + nf.format(_min * 100) + "%");
      } else {
        results.append(" Min:" + nf.format(_min));
      }

      if (percent) {
        results.append(" Mean:" + nf.format(_mean * 100) + "%");
      } else {
        results.append(" Mean:" + nf.format(_mean));
      }

      results.append(" Sum:" + nf.format(_sum));
      results.append(" STD:" + nf.format(_stdDev));
      return results.toString();
    } else if (_number == 1) {
      if (percent) {
        return ("Number:" + nf.format(_sum * 100) + "%");
      } else {
        return ("Number:" + nf.format(_sum));
      }
    } else {
      return ("Number: N/A");
    }
  }


  private void calculate() {
    getMean();
    getStandardDev();
  }


  /**
   * Get the max data element added to the statistics
   * object so far.
   *
   * @return double - Maximum entry added so far.
   */
  public double getMax() {
    return _max;
  }


  /**
   * Get the min data element added to the statistics
   * object so far.
   *
   * @return double - Min entry added so far.
   */
  public double getMin() {
    return _min;
  }


  /**
   * Get the number of data elements added to the statistics
   * object so far.
   *
   * @return double - Number of entries added so far.
   */
  public long getNumberOfElements() {
    return _number;
  }


  /**
   * Get the average or mean of data elements added to the
   * statistics object so far.
   *
   * @return double - Mean of entries added so far.
   */
  public double getMean() {
    if (_number > 0) {
      _mean = _sum / _number;
    }
    return _mean;
  }

  /**
   * Get the ratio of the sum of elements divided by the number
   * of elements added * 100
   *
   * @return double - Percent of entries added so far.
   */
  public double getPercent() {
    if (_number > 0) {
      _mean = _sum / _number;
    }
    _mean = _mean * 100;
    return _mean;
  }


  /**
   * Get the sum or mean of data elements added to the
   * statistics object so far.
   *
   * @return double - Sum of entries added so far.
   */
  public double getSum() {
    return _sum;
  }


  /**
   * Get the sum of the squares of the data elements added
   * to the statistics object so far.
   *
   * @return double - Sum of the squares of the entries added so far.
   */
  public double getSumOfSq() {
    return _sumOfSq;
  }


  /**
   * Get the standard deviation of the data elements added
   * to the statistics object so far.
   *
   * @return double - Sum of the standard deviation of the entries added so far.
   */
  public double getStandardDev() {
    if (_number > 1) {
      _stdDev = Math.sqrt((_sumOfSq - ((_sum * _sum) / _number)) / (_number - 1));
    }
    return _stdDev;
  }


  /**
   * Read the data from the InputStream so it can be used to populate
   * the current objects state.
   *
   * @param in java.io.InputStream to write to.
   * @throws IOException
   */
  public void readFromDataInput(InputStream in) throws IOException {
    DataInput di = new DataInputStream(in);
    readFromDataInput(di);
    return;
  }


  /**
   * Read the data from the DataInput so it can be used to populate
   * the current objects state.
   *
   * @param in java.io.InputStream to write to.
   * @throws IOException
   */
  public void readFromDataInput(DataInput in) throws IOException {
    _max = in.readDouble();
    _min = in.readDouble();
    _number = in.readLong();
    _mean = in.readDouble();
    _stdDev = in.readDouble();
    _sum = in.readDouble();
    _sumOfSq = in.readDouble();
    return;
  }


  /**
   * Write the data to the output steam so it can be streamed to an
   * other process, wire or storage median in a format that another Stats
   * object can read.
   *
   * @param out java.io.OutputStream to write to.
   * @throws IOException
   */
  public void writeToDataOutput(OutputStream out) throws IOException {
    DataOutput dout = new DataOutputStream(out);
    writeToDataOutput(dout);
    return;

  }


  /**
   * Write the data to the data output object so it can be written to an
   * other process, wire or storage median in a format that another Stats
   * object can read.
   *
   * @param out java.io.DataOutput to write to.
   * @throws IOException
   */
  public void writeToDataOutput(DataOutput out) throws IOException {
    out.writeDouble(_max);
    out.writeDouble(_min);
    out.writeLong(_number);
    out.writeDouble(_mean);
    out.writeDouble(_stdDev);
    out.writeDouble(_sum);
    out.writeDouble(_sumOfSq);
    return;
  }


  // ************************************
  private static final long serialVersionUID = 1L;
  private double      _max = 0 ;
  private double      _min = Double.MAX_VALUE ;
  private long        _number = 0 ;
  private double      _mean = 0 ;
  private double      _stdDev = 0 ;
  private double      _sum = 0 ;
  private double      _sumOfSq ;
}

