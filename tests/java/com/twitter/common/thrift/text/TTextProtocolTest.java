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

package com.twitter.common.thrift.text;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.io.Resources;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TTransport;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test the TTextProtocol
 *
 * TODO(Alex Roetter): add more tests, especially ones that verify
 * that we generate ParseErrors for invalid input
 *
 * @author Alex Roetter
 */
public class TTextProtocolTest {
  private static final Logger LOG = Logger.getLogger(
      TTextProtocolTest.class.getName());

  // TODO(Alex Roetter): move this static variable over to ThriftCodec,
  // alongside the others
  private static final Function<TTransport, TProtocol> TEXT_PROTOCOL =
      new Function<TTransport, TProtocol>() {

        @Override
        public TProtocol apply(TTransport transport) {
          return new TTextProtocol(transport);
        }
      };

  private String fileContents;

  /**
   * Load a file containing a serialized thrift message in from disk
   * @throws IOException
   */
  @Before
  public void setUp() throws IOException {
    fileContents = Resources.toString(Resources.getResource(
        getClass(),
        "/com/twitter/common/thrift/text/TTextProtocol_TestData.txt"),
        Charsets.UTF_8);
  }

  /**
   * Read in (deserialize) a thrift message in TTextProtocol format
   * from a file on disk, then serialize it back out to a string.
   * Finally, deserialize that string and compare to the original
   * message.
   * @throws IOException
   */
  @Test
  public void tTextProtocolReadWriteTest() throws IOException, TException {
    // Deserialize the file contents into a thrift message.
    ByteArrayInputStream bais1 = new ByteArrayInputStream(
        fileContents.getBytes());

    TTextProtocolTestMsg msg1 = new TTextProtocolTestMsg();
    msg1.read(new TTextProtocol(new TIOStreamTransport(bais1)));
    LOG.info("Got thrift message from file: \n" + msg1);

    // Serialize that thrift message out to a byte array
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    msg1.write(new TTextProtocol(new TIOStreamTransport(baos)));
    byte[] bytes = baos.toByteArray();

    LOG.info("Serialized message to bytes string: \n" + bytes);

    // Deserialize that string back to a thrift message.
    ByteArrayInputStream bais2 = new ByteArrayInputStream(bytes);
    TTextProtocolTestMsg msg2 = new TTextProtocolTestMsg();
    msg2.read(new TTextProtocol(new TIOStreamTransport(bais2)));

    LOG.info("Deserialized back to thrift message: \n" + msg2);
    assertEquals(msg1, msg2);
  }
}
