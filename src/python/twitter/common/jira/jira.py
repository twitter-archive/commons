import base64
import getpass
import json
import textwrap
import urllib
import urllib2
import urlparse

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

  def _get_resolve_transition_id(self, issue):
    '''Find the transition id to resolve the issue'''
    try:
      transitions = json.loads(self.get_transitions(issue))['transitions']
    except (KeyError, ValueError) as e:
      raise JiraError('Transitions list did not have the expected JSON format: %s', e)

    for transition in transitions:
      if transition['name'] == 'Resolve':
        return transition['id']

    raise JiraError(textwrap.dedent('''
    Could not find the id of the JIRA \'Resolve\' transition, here were the
    available transitions:
    %s
    ''' % (transitions)))

  def resolve(self, issue, comment=None):
    transition_id = self._get_resolve_transition_id(issue)

    data = {
      'fields': {'resolution': {'name': 'Fixed'}},
      'transition': {'id': transition_id}
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
  def create_issue(self, project, issue_type, summary, description=None, **kw):
    data = {
      'fields': {
        'project': {'key': project},
        'issuetype': {'name': issue_type},
        'summary': summary,
        'description': description
      }
    }
    data['fields'].update(kw)

    try:
      return self.api_call('issue', data)
    except urllib2.URLError as e:
      raise JiraError(cause=e)

  def fetch_issue_fields(self, project_key, issue_type):
    data = {
      "projectKeys": project_key,
      "issuetypeIds": issue_type,
      "expand": "projects.issuetypes.fields"
    }
    qs = urllib.urlencode(data)
    endpoint = '%s?%s' % ('issue/createmeta', qs)
    try:
      return self.api_call(endpoint)
    except urllib2.URLError as e:
      raise JiraError(cause=e)

  def api_call(self, endpoint, post_json=None, authorization=None):
    url = urlparse.urljoin(self._base_url, endpoint)
    headers = {'User-Agent': 'twitter.common.jira'}
    base64string = authorization or base64.b64encode('%s:%s' % (self._user, self._getpass()))
    headers['Authorization'] = 'Basic %s' % base64string
    log.debug(headers)
    data = json.dumps(post_json) if post_json else None
    if data:
      headers['Content-Type'] = 'application/json'
    return urllib2.urlopen(urllib2.Request(url, data, headers)).read()
