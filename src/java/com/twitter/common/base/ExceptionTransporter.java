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

import com.google.common.base.Function;

/**
 * A utility for transporting checked exceptions across boundaries that do not allow for checked
 * exception propagation.
 *
 * @param <E> The type of checked exception the ExceptionTransported can transport
 *
 * @author John Sirois
 */
public class ExceptionTransporter<E extends Exception> {

  /**
   * An exception wrapper used to transport checked exceptions.  Never leaves an
   * {@link ExceptionTransporter#guard(com.google.common.base.Function)} call.
   */
  private static final class TransportingException extends RuntimeException {
    private TransportingException(Exception cause) {
      super("It is a usage error to see this message!", cause);
    }
  }

  /**
   * Guards a unit of work that internally can generate checked exceptions.  Callers wrap up the
   * work in a function that rethrows any checked exceptions using the supplied
   * ExceptionTransporter.  Guard will ensure the original exception is unwrapped an re-thrown.
   *
   * @param work The unit of work that guards its checked exceptions.
   * @param <T> The type returned by the unit of work when it successfully completes.
   * @param <X> The type of checked exception that the unit of work wishes to guard.
   * @return the result of the unit of work if no excpetions are thrown
   * @throws X when the unit of work uses the ExceptionTransporter to throw a checked exception
   */
  public static <T, X extends Exception> T guard(Function<ExceptionTransporter<X>, T> work)
    throws X {

    try {
      return work.apply(new ExceptionTransporter<X>());
    } catch (TransportingException e) {
      @SuppressWarnings("unchecked")
      X cause = (X) e.getCause();
      throw cause;
    }
  }

  /**
   * Throws the given {@code checked} exception across a boundary that does not allow checked
   * exceptions.  Although a RuntimeException is returned by this method signature, the method never
   * actually completes normally.  The return type does allow the following usage idiom however:
   * <pre>
   * public String apply(ExceptionTransporter transporter) {
   *   try {
   *     return doChecked();
   *   } catch (CheckedException e) {
   *     // Although transport internally throws and does not return, we satisfy the compiler that
   *     // our method returns a value or throws by pretending to throw the RuntimeException that
   *     // never actually gets returned by transporter.transport(...)
   *     throw transporter.transport(e);
   *   }
   * }
   * </pre>
   *
   * @param checked The checked exception to transport.
   * @return A RuntimeException that can be thrown to satisfy the compiler at the call site
   */
  public RuntimeException transport(E checked) {
    throw new TransportingException(checked);
  }
}
