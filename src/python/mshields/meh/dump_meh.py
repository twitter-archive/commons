"""Dump Meh API data."""

from twitter.common import app
from mshields.meh.meh import Meh


def main(unused_options, unused_args):

  """Retrieve Meh data and dump to screen"""
  meh_obj = Meh()
  data_str = meh_obj.get_data('https://api.meh.com/1/current.json?apikey=cq8VfoC8DVLu8ELAk8u6cbivlexo84WE')
  data_dict = meh_obj.get_json(data_str)
  print data_dict

if __name__ == '__main__':
  app.main()
