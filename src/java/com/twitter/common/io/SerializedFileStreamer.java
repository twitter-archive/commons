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

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.twitter.common.base.Closure;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A streamer that reads from serialized files.
 *
 * @author Gilad Mishne
 */
public class SerializedFileStreamer<T extends Serializable> implements Streamer<T> {
  private static final Logger LOG = Logger.getLogger(SerializedFileStreamer.class.getName());

  private final Iterable<? extends ObjectInputStream> inputs;
  private Predicate<T> filter = Predicates.alwaysTrue();
  private Predicate<T> endCondition = Predicates.alwaysFalse();

  /**
   * Returns a streamer that will deserialize objects of type T from a set of files in the order
   * the files are given.
   *
   * @param inputs The input streams to read from.
   */
  public SerializedFileStreamer (Iterable<? extends ObjectInputStream> inputs) {
    this.inputs = Preconditions.checkNotNull(inputs);
  }

  /**
   * Deserialize {@code inputStream}, executing {@code work} on each object.
   */
  private void process(ObjectInputStream inputStream, Closure<T> work) {
    Object o;
    int count = 0;
    try {
      while ((o = inputStream.readObject()) != null) {
        try {
          T t = (T) o;
          if (endCondition.apply(t)) {
            break;
          }
          if (filter.apply(t)) {
            work.execute(t);
            ++count;
          }
        } catch (ClassCastException e) {
          LOG.log(Level.SEVERE, "Unexpected object " + o.toString() + " in stream: " , e);
        }
      }
    } catch (EOFException e) {
      LOG.log(Level.INFO, "Read " + count + " objects from stream.");
    } catch (ClassNotFoundException e) {
      LOG.log(Level.SEVERE, "Unexpected class in stream.");
      throw new RuntimeException(e);
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Error reading from stream.");
      throw new RuntimeException(e);
    } catch (NullPointerException e) {
      LOG.log(Level.SEVERE, "Unexpected data in status file stream.");
      throw new RuntimeException(e);
    } finally {
      try {
        inputStream.close();
      } catch (IOException e) {
        LOG.log(Level.SEVERE, "Can't close stream.", e);
      }
    }
  }

  @Override
  public Streamer<T> endOn(Predicate<T> cond) {
    Preconditions.checkNotNull(cond);
    endCondition = Predicates.or(cond);
    return this;
  }

  @Override public void process(Closure<T> work) {
    Preconditions.checkNotNull(work);
    for (ObjectInputStream input : inputs) {
      process(input, work);
    }
  }

  @Override public Streamer<T> filter(Predicate<T> filter) {
    this.filter = Preconditions.checkNotNull(filter);
    return this;
  }
}
