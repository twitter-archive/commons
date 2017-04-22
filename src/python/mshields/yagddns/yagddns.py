"""Yet another Google Dynamic DNS updater."""
# pylint: disable=import-error

from twitter.common import app
from twitter.common import log

from yaml import safe_load

from mshields.yagddns import lib


def main(args):
  """
  Iterate through given yaml files args and update Google Domains Dynamic DNS.

  :param args: yaml files.
  :type args: list
  """
  for yaml_file in args:
    log.info('Loading %s', yaml_file)

    with open(yaml_file) as yaml_fh:
      creds = safe_load(yaml_fh)

    for response in lib.update_dns(creds):
      log.info('Response: %s', response)


app.main()
