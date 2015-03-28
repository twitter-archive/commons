""" Tests for meh API """


import json
import mock
import unittest

from mshields.meh.meh import Meh

RAW_DATA = """{"key1":"value1","key2":"value2"}"""
JSON_DICT = {'key1': 'value1',
             'key2': 'value2',
            }

def mock_meh():
  """ Mocks Meh object. 

  Returns:
    mock_meh: object, mock
  """

  MockMeh = mock.patch('mshields.meh.meh.Meh')


class TestMeh(unittest.TestCase):

  def setUp(self):
    self.meh_api_url_prefix = 'https://api.meh.com/1/current.json?apikey='
    self.meh_api_key = 'cq8VfoC8DVLu8ELAk8u6cbivlexo84WE'
    self.meh_api_url = self.meh_api_url_prefix + self.meh_api_key

  def test_get_data(self):
    mock_obj = mock.Mock()
    meh_obj = Meh()
    mock_obj.meh_obj.get_data(self.meh_api_url)
    mock_obj.meh_obj.assert_called_with(self.meh_api_url)

    #fake_meh_data = MockMeh.get_data
    #fake_meh_data.return_value = RAW_DATA
    #self.assertEqual(fake_meh_data.return_value, RAW_DATA)

  def no_test_get_json(self):
    fake_meh_json = MockMeh.get_json(self.meh_api_url)
    fake_meh_json.assert_called_with(self.meh_api_url)
    fake_meh_json.return_value = JSON_DICT
    self.assertEqual(fake_meh_json.return_value, JSON_DICT)
