"""Yet another Google Dynamic DNS updater."""

# pylint: disable=import-error

from twitter.common import app
from twitter.common import log

import requests  # pylint: disable=import-error
from yaml import safe_load


def update_dns(creds, url='https://domains.google.com/nic/update'):
  """
  Update a Google Domains Dynamic DNS hostname using their API.

  Args:
    creds (:obj:`str`): processed from yaml.
  Return:
    responses (:obj:`grequests.map`).
  """
  log.info(creds)
  for hostname in creds:
    username = creds[hostname]['username']
    password = creds[hostname]['password']
    myip = creds[hostname].get('myip')
    payload = dict(hostname=hostname, myip=myip)

    response = requests.post(url, auth=(username, password), data=payload)

    log.info(response)


def public_ip(url='https://domains.google.com/checkip'):
  """
  Gets public IP address from Google Domains.

  Returns:
    ip_addr (str).
  """
  response = requests.get(url)
  return response


def main(args):
  """
  Iterate through given yaml files args and update Google Domains Dynamic DNS.

  Args:
    *args, **options
  """
  for yaml_file in args:
    log.info('Loading %s', yaml_file)

    with open(yaml_file) as yaml_fh:
      creds = safe_load(yaml_fh)

    update_dns(creds)


app.main()
