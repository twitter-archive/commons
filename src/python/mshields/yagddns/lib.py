"""Yagddns common library."""
# pylint: disable=import-error

import requests


def update_dns(creds, url='https://domains.google.com/nic/update'):
  """
  Update a Google Domains Dynamic DNS hostname using their API.

  :param creds: credentials from processed yaml.
  :type creds: dict
  :param url: URL to submit credentials.
  :yield: response
  :rtype: obj, requests response
  """
  for hostname in creds:
    username = creds[hostname]['username']
    password = creds[hostname]['password']
    myip = creds[hostname].get('myip')
    payload = dict(hostname=hostname, myip=myip)

    response = requests.post(url, auth=(username, password), data=payload)

    yield response
