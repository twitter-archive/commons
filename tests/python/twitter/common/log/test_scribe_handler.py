#!/usr/bin/python

__author__ = "Chris Chen (cchen@twitter.com)"


import mox
import logging
import unittest2 as unittest

from twitter.common.log.handlers import ScribeHandler

try:
  from scribe import scribe
  from thrift.protocol import TBinaryProtocol
  from thrift.transport import TTransport, TSocket
  _SCRIBE_PRESENT = True
except ImportError:
  _SCRIBE_PRESENT = False


_CATEGORY = "python_default"
_HOST = "localhost"
_PORT = 1463
_TEST_MSG = ("For years, the war-crimes fugitive known as 'The Terminator' was so supremely "
             "confident that he played tennis at a luxury hotel near the Congo-Rwanda border, "
             "flaunting his freedom while United Nations peacekeepers drove past.")

if _SCRIBE_PRESENT:
  _MESSAGES = [scribe.LogEntry(category=_CATEGORY, message=_TEST_MSG)]


class TestHandler(unittest.TestCase):
  def setUp(self):
    self.mox = mox.Mox()
    self.handler = None
    self.logger = logging.getLogger()
    self.logger.setLevel(logging.DEBUG)
    if _SCRIBE_PRESENT:
      self.handler_drop = ScribeHandler(buffer=False,
                                        category=_CATEGORY,
                                        host=_HOST,
                                        port=_PORT)
      self.handler_buffer = ScribeHandler(buffer=True,
                                          category=_CATEGORY,
                                          host=_HOST,
                                          port=_PORT)

  def tearDown(self):
    if self.handler:
      self.logger.removeHandler(self.handler)
      self.handler.close()
    self.mox.UnsetStubs()
    self.mox.VerifyAll()

  def set_handler(self, handler):
    self.handler = handler
    self.logger.addHandler(handler)

  def setup_mock_client(self):
    self.mox.StubOutClassWithMocks(TBinaryProtocol, "TBinaryProtocol")
    self.mock_protocol = TBinaryProtocol.TBinaryProtocol(trans=self.mock_transport,
                                                         strictRead=False,
                                                         strictWrite=False)
    self.mox.StubOutClassWithMocks(scribe, "Client")
    self.mock_client = scribe.Client(iprot=self.mock_protocol, oprot=self.mock_protocol)

  def setup_mock_transport(self):
    self.mox.StubOutClassWithMocks(TSocket, "TSocket")
    self.mock_tsocket = TSocket.TSocket(host=_HOST, port=_PORT)
    self.mox.StubOutClassWithMocks(TTransport, "TFramedTransport")
    self.mock_transport = TTransport.TFramedTransport(self.mock_tsocket)

  @unittest.skipIf(_SCRIBE_PRESENT, "Scribe Modules Present")
  def test_drop_no_scribe(self):
    with self.assertRaises(ScribeHandler.ScribeHandlerException):
      self.handler_drop = ScribeHandler(buffer=False,
                                        category=_CATEGORY,
                                        host=_HOST,
                                        port=_PORT)
      logging.debug(_TEST_MSG)

  @unittest.skipIf(_SCRIBE_PRESENT, "Scribe Modules Present")
  def test_buffer_no_scribe(self):
    with self.assertRaises(ScribeHandler.ScribeHandlerException):
      self.handler_buffer = ScribeHandler(buffer=True,
                                          category=_CATEGORY,
                                          host=_HOST,
                                          port=_PORT)
      logging.debug(_TEST_MSG)

  @unittest.skipUnless(_SCRIBE_PRESENT, "Scribe Modules Not Present")
  def test_drop_ok(self):
    self.setup_mock_transport()
    self.setup_mock_client()
    self.set_handler(self.handler_drop)
    self.mock_transport.open()
    self.mock_client.Log(_MESSAGES).AndReturn(scribe.ResultCode.OK)
    self.mock_transport.close()
    self.mox.ReplayAll()
    logging.error(_TEST_MSG)
    self.assertFalse(self.handler.messages_pending)

  @unittest.skipUnless(_SCRIBE_PRESENT, "Scribe Modules Not Present")
  def test_drop_client_fail(self):
    self.setup_mock_transport()
    self.setup_mock_client()
    self.set_handler(self.handler_drop)
    self.mock_transport.open()
    self.mock_client.Log(_MESSAGES).AndReturn(scribe.ResultCode.TRY_LATER)
    self.mock_transport.close()
    self.mox.ReplayAll()
    logging.debug(_TEST_MSG)
    self.assertFalse(self.handler.messages_pending)

  @unittest.skipUnless(_SCRIBE_PRESENT, "Scribe Modules Not Present")
  def test_drop_connect_fail(self):
    self.setup_mock_transport()
    self.set_handler(self.handler_drop)
    self.mock_transport.open().AndRaise(TTransport.TTransportException)
    self.mock_transport.close()
    self.mox.ReplayAll()
    logging.debug(_TEST_MSG)
    self.assertFalse(self.handler.messages_pending)

  @unittest.skipUnless(_SCRIBE_PRESENT, "Scribe Modules Not Present")
  def test_buffer_ok(self):
    self.setup_mock_transport()
    self.setup_mock_client()
    self.set_handler(self.handler_buffer)
    self.mock_transport.open()
    self.mock_client.Log(_MESSAGES).AndReturn(scribe.ResultCode.OK)
    self.mock_transport.close()
    self.mox.ReplayAll()
    logging.error(_TEST_MSG)

  @unittest.skipUnless(_SCRIBE_PRESENT, "Scribe Modules Not Present")
  def test_buffer_client_fail(self):
    self.setup_mock_transport()
    self.setup_mock_client()
    self.set_handler(self.handler_buffer)
    self.mock_transport.open()
    self.mock_client.Log(_MESSAGES).AndReturn(scribe.ResultCode.TRY_LATER)
    self.mock_transport.close()
    self.mock_transport.open()
    self.mock_client.Log(_MESSAGES).AndReturn(scribe.ResultCode.OK)
    self.mock_transport.close()
    self.mox.ReplayAll()
    logging.debug(_TEST_MSG)
    self.assertEquals(self.handler._log_buffer, _MESSAGES)

  @unittest.skipUnless(_SCRIBE_PRESENT, "Scribe Modules Not Present")
  def test_buffer_client_fail_close_client_fail(self):
    self.setup_mock_transport()
    self.setup_mock_client()
    self.set_handler(self.handler_buffer)
    self.mock_transport.open()
    self.mock_client.Log(_MESSAGES).AndReturn(scribe.ResultCode.TRY_LATER)
    self.mock_transport.close()
    self.mock_transport.open()
    self.mock_client.Log(_MESSAGES).AndReturn(scribe.ResultCode.TRY_LATER)
    self.mock_transport.close()
    self.mox.ReplayAll()
    logging.debug(_TEST_MSG)
    self.assertEquals(self.handler._log_buffer, _MESSAGES)

  @unittest.skipUnless(_SCRIBE_PRESENT, "Scribe Modules Not Present")
  def test_buffer_client_fail_close_connect_fail(self):
    self.setup_mock_transport()
    self.setup_mock_client()
    self.set_handler(self.handler_buffer)
    self.mock_transport.open()
    self.mock_client.Log(_MESSAGES).AndReturn(scribe.ResultCode.TRY_LATER)
    self.mock_transport.close()
    self.mock_transport.open().AndRaise(TTransport.TTransportException)
    self.mock_transport.close()
    self.mox.ReplayAll()
    logging.debug(_TEST_MSG)
    self.assertEquals(self.handler._log_buffer, _MESSAGES)

  @unittest.skipUnless(_SCRIBE_PRESENT, "Scribe Modules Not Present")
  def test_buffer_connect_fail(self):
    self.setup_mock_transport()
    self.setup_mock_client()
    self.set_handler(self.handler_buffer)
    self.mock_transport.open().AndRaise(TTransport.TTransportException)
    self.mock_transport.close()
    self.mock_transport.open()
    self.mock_client.Log(_MESSAGES).AndReturn(scribe.ResultCode.OK)
    self.mock_transport.close()
    self.mox.ReplayAll()
    logging.debug(_TEST_MSG)
    self.assertEquals(self.handler._log_buffer, _MESSAGES)

  @unittest.skipUnless(_SCRIBE_PRESENT, "Scribe Modules Not Present")
  def test_buffer_connect_fail_close_client_fail(self):
    self.setup_mock_transport()
    self.setup_mock_client()
    self.set_handler(self.handler_buffer)
    self.mock_transport.open().AndRaise(TTransport.TTransportException)
    self.mock_transport.close()
    self.mock_transport.open()
    self.mock_client.Log(_MESSAGES).AndReturn(scribe.ResultCode.TRY_LATER)
    self.mock_transport.close()
    self.mox.ReplayAll()
    logging.debug(_TEST_MSG)
    self.assertEquals(self.handler._log_buffer, _MESSAGES)

  @unittest.skipUnless(_SCRIBE_PRESENT, "Scribe Modules Not Present")
  def test_buffer_connect_fail_close_connect_fail(self):
    self.setup_mock_transport()
    self.set_handler(self.handler_buffer)
    self.mock_transport.open().AndRaise(TTransport.TTransportException)
    self.mock_transport.close()
    self.mock_transport.open().AndRaise(TTransport.TTransportException)
    self.mock_transport.close()
    self.mox.ReplayAll()
    logging.debug(_TEST_MSG)
    self.assertEquals(self.handler._log_buffer, _MESSAGES)

  @unittest.skipUnless(_SCRIBE_PRESENT, "Scribe Modules Not Present")
  def test_buffer_client_recover(self):
    self.setup_mock_transport()
    self.setup_mock_client()
    self.set_handler(self.handler_buffer)
    self.mock_transport.open()
    self.mock_client.Log(_MESSAGES).AndReturn(scribe.ResultCode.TRY_LATER)
    self.mock_transport.close()
    self.mock_transport.open()
    self.mock_client.Log(_MESSAGES + _MESSAGES).AndReturn(scribe.ResultCode.OK)
    self.mock_transport.close()
    self.mox.ReplayAll()
    logging.debug(_TEST_MSG)
    self.assertEquals(self.handler._log_buffer, _MESSAGES)
    logging.debug(_TEST_MSG)
    self.assertFalse(self.handler.messages_pending)

  @unittest.skipUnless(_SCRIBE_PRESENT, "Scribe Modules Not Present")
  def test_buffer_connect_recover(self):
    self.setup_mock_transport()
    self.setup_mock_client()
    self.set_handler(self.handler_buffer)
    self.mock_transport.open().AndRaise(TTransport.TTransportException)
    self.mock_transport.close()
    self.mock_transport.open()
    self.mock_client.Log(_MESSAGES + _MESSAGES).AndReturn(scribe.ResultCode.OK)
    self.mock_transport.close()
    self.mox.ReplayAll()
    logging.debug(_TEST_MSG)
    self.assertEquals(self.handler._log_buffer, _MESSAGES)
    logging.debug(_TEST_MSG)
    self.assertFalse(self.handler.messages_pending)

if __name__ == "__main__":
  unittest.main()
