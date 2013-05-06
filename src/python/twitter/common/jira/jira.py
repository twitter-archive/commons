import base64
import getpass
import json
import urllib
import urllib2
import urlparse
import xmlrpclib

try:
  from xmlrpclib import ServerProxy, Fault
except ImportError:
  from xmlrpc.client import ServerProxy, Fault

from twitter.common import log


class JiraError(Exception):
  '''Indicates a problem performing an action with JIRA.'''
  def __init__(self, cause=None, message=None):
    self._cause = cause
    self._message = message

  def __str__(self):
    msg = 'JIRA request failed'
    if self._message:
      msg += ', message: %s' % self._message
    if self._cause:
      msg += ' due to %s' % self._cause
    return msg


class Jira(object):
  '''Interface for interacting with JIRA.

     Currently only works for situations where the user may be prompted for
     credentials.
  '''

  # Values documented at:
  # http://docs.atlassian.com/software/jira/docs/api/5.0.1/constant-values.html
  RESOLVED_STATUS_ID = 5

  def __init__(self, server_url, api_base='/rest/api/2/', user=None, password=None):
    self._base_url = urlparse.urljoin(server_url, api_base)
    self._user = user or getpass.getuser()
    #Set the password if initialized or Lazily request password later when request is made.
    self._pass = password

  def _getpass(self):
    if not self._pass:
      self._pass = getpass.getpass('Please enter JIRA password for %s: ' % self._user)
    return self._pass

  def comment(self, issue, comment):
    try:
      self.api_call('issue/%s/comment' % issue, {'body': comment})
    except urllib2.URLError as e:
      raise JiraError(cause=e)

  def get_transitions(self, issue):
    return self.api_call('issue/%s/transitions' % issue)

  def resolve(self, issue, comment=None):
    data = {
      'fields': {'resolution': {'name': 'Fixed'}},
      'transition': {'id': Jira.RESOLVED_STATUS_ID}
    }
    if comment:
      data['update'] = {'comment': [{'add': {'body': comment}}]}
    try:
      self.api_call('issue/%s/transitions' % issue, data)
    except urllib2.HTTPError as e:
      raise JiraError(cause=e, message='Transition failed, is the bug already closed?')
    except urllib2.URLError as e:
      raise JiraError(cause=e)

  # create a new issue using project key and issuetype names, i.e. TEST, Incident
  def create_issue(self, project, issue_type, summary, description=None):
    data = {
      'fields': {
        'project': {'key': project},
        'issuetype': {'name': issue_type},
        'summary': summary,
        'description': description
      }
    }
    try:
      self.api_call('issue', data)
    except urllib2.URLError as e:
      raise JiraError(cause=e)

  def api_call(self, endpoint, post_json=None, authorization=None):
    url = urlparse.urljoin(self._base_url, endpoint)
    headers = {'User-Agent': 'twitter.common.jira'}
    base64string = authorization or base64.b64encode('%s:%s' % (self._user, self._getpass()))
    headers['Authorization'] = 'Basic %s' % base64string
    log.info(headers)
    data = json.dumps(post_json) if post_json else None
    if data:
      headers['Content-Type'] = 'application/json'
    return urllib2.urlopen(urllib2.Request(url, data, headers)).read()
