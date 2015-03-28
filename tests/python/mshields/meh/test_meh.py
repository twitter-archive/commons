""" Tests for meh API """


import json
import mock
import unittest

from mshields.meh.meh import Meh

RAW_DATA = """{"key1":"value1","key2":"value2"}"""
JSON_DICT = {'key1': 'value1',
             'key2': 'value2',
            }


class TestMeh(unittest.TestCase):

  def setUp(self):
    self.meh_api_url_prefix = 'https://api.meh.com/1/current.json?apikey='
    self.meh_api_key = 'cq8VfoC8DVLu8ELAk8u6cbivlexo84WE'
    self.meh_api_url = self.meh_api_url_prefix + self.meh_api_key

  def test_get_data(self):
    mock_obj = mock.Mock()
    mock_obj.meh_obj = mock_obj.Meh()
    fake_meh = mock_obj.meh_obj

    fake_meh.get_data(self.meh_api_url)
    fake_meh.get_data.assert_called_with(self.meh_api_url)
    fake_meh.get_data.return_value = RAW_DATA

    self.assertEqual(mock_obj.meh_obj.get_data.return_value, RAW_DATA)

  def test_get_json(self):
    mock_obj = mock.Mock()
    mock_obj.meh_obj = mock_obj.Meh()
    fake_meh = mock_obj.meh_obj

    fake_meh.get_json(RAW_DATA)
    fake_meh.get_json.assert_called_with(RAW_DATA)
    fake_meh.get_json.return_value = JSON_DICT

    self.assertEqual(fake_meh.get_json.return_value, JSON_DICT)
