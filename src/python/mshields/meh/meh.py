""" A basic utility for retrieving json via Meh API and returning an object. """

import json
import urllib2


class Meh(object):
  

  def get_data(self, meh_api_url):
    """ Retrieve and returns JSON string from Meh API.

    Args:
      meh_api_url: string

    Returns:
      meh_data: string, json data
    """
    
    try:
      meh_data = urllib2.urlopen(meh_api_url)
    except urllib2.HTTPError as http_error:
      print http_error.code
      print http_error.read()
    
    return meh_data

  def get_json(self, meh_data):
    """ Converts JSON string to dictionary

    Args:
      meh_data: string, json data

    Returns:
      meh_dict: dictionary, json data
    """

    meh_dict = json.load(meh_data)
    return meh_dict
