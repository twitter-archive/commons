import base64
import getpass
import json
import textwrap
import urllib
import urllib2
import urlparse

from contextlib import contextmanager

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

  RESOLVE_NAMES = (
    'Resolve',
    'Resolve Issue',
  )

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
    with self._api_call_guard():
      self.api_call('issue/%s/comment' % issue, {'body': comment})

  def get_transitions(self, issue):
    return self.api_call('issue/%s/transitions' % issue)

  def _get_resolve_transition_id(self, issue):
    '''Find the transition id to resolve the issue'''
    try:
      transitions = json.loads(self.get_transitions(issue))['transitions']
    except (KeyError, ValueError) as e:
      raise JiraError(cause=e, message='Transitions list did not have the expected JSON format')

    for transition in transitions:
      if transition['name'] in self.RESOLVE_NAMES:
        return transition['id']

    raise JiraError(message=textwrap.dedent('''
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

    with self._api_call_guard(http_error_msg='Transition failed, is the bug already closed?'):
      self.api_call('issue/%s/transitions' % issue, data)

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

    with self._api_call_guard():
      return self.api_call('issue', data)

  def fetch_issue_fields(self, project_key, issue_type):
    data = {
      "projectKeys": project_key,
      "issuetypeIds": issue_type,
      "expand": "projects.issuetypes.fields"
    }
    qs = urllib.urlencode(data)
    endpoint = '%s?%s' % ('issue/createmeta', qs)

    with self._api_call_guard():
      return self.api_call(endpoint)

  def get_issue(self, issue):
    '''Returns the data for the given issue'''
    endpoint = 'issue/%s' % (issue)

    with self._api_call_guard():
      return self.api_call(endpoint)

  def get_link_types(self):
    '''Returns a list of the available link types'''
    link_types = []

    with self._api_call_guard():
      try:
        link_types_data = json.loads(self.api_call('issueLinkType'))
      except (KeyError, ValueError) as e:
        raise JiraError(cause=e, message='Transitions list did not have the expected JSON format')

    for link_type in link_types_data['issueLinkTypes']:
      link_types.append(link_type['name'])

    return link_types

  def add_link(self, inward_issue, outward_issue, comment=None, link_type='Related'):
    '''Add a link between the inward_issue and the outward_issue'''
    link_types = self.get_link_types()

    if not link_type in link_types:
      raise JiraError(message="Error: Link type of '%s' not valid: " % link_type)

    data = {
      'type': {'name': link_type},
      'inwardIssue': {'key': inward_issue},
      'outwardIssue': {'key': outward_issue},
    }

    if comment:
      data['comment'] = {'body': comment}

    with self._api_call_guard():
      return self.api_call('issueLink', data)

  def remove_link(self, link_id):
    '''Remove a link specified by by the link ID. The link ID can be found with get_issue_links()'''
    endpoint = 'issueLink/%s' % (link_id)

    with self._api_call_guard():
      self.api_call(endpoint, send_delete=True)

  def get_issue_links(self, issue):
    '''Returns the links section of an issue'''
    issue_data = self.get_issue(issue)
    issue_parsed = json.loads(issue_data)

    return issue_parsed['fields']['issuelinks']

  def add_watcher(self, issue, watcher):
    '''Adds the given watcher to the given issue'''
    endpoint = 'issue/%s/watchers' % (issue)

    with self._api_call_guard():
      return self.api_call(endpoint, watcher)

  def remove_watcher(self, issue, watcher):
    '''Removes the given watcher from the given issue'''
    endpoint = 'issue/%s/watchers?username=%s' % (issue, watcher)

    with self._api_call_guard():
      return self.api_call(endpoint, send_delete=True)

  def transition(self, issue, transition_name, comment=None, **kw):
    '''Transition an issue from one state to another

    issue -- The issue key to appy the transition to (e.g. SEARCH-1000)
    transition_name -- The name of the transition state in the JIRA web UI (e.g. "Start Progress")
    comment -- Adds a comment during the transition
    **kw -- Allows for optional fields to be passed during the transition
    '''
    transitions_json = self.get_transitions(issue)

    transitions = json.loads(transitions_json)
    for transition in transitions['transitions']:
      if transition['name'] == transition_name:
        endpoint = 'issue/%s/transitions' % (issue)
        data = {'transition': {'id': transition['id']}}

        if comment:
          data['update'] = {'comment': [{'add': {'body': 'Deploy completed'}}]}

        if kw:
          data['fields'] = kw

        with self._api_call_guard():
          return self.api_call(endpoint, data)

    raise JiraError(message="Transition '%s' not currently available for this issue" %
                      (transition_name))

  @contextmanager
  def _api_call_guard(self, http_error_msg=None, url_error_msg=None):
    try:
      yield
    except urllib2.HTTPError as e:
      raise JiraError(cause=e, message=http_error_msg)
    except urllib2.URLError as e:
      raise JiraError(cause=e, message=url_error_msg)

  def api_call(self, endpoint, post_json=None, authorization=None, send_delete=False):
    url = urlparse.urljoin(self._base_url, endpoint)
    headers = {'User-Agent': 'twitter.common.jira'}
    base64string = authorization or base64.b64encode('%s:%s' % (self._user, self._getpass()))
    headers['Authorization'] = 'Basic %s' % base64string
    log.debug(headers)
    data = json.dumps(post_json) if post_json else None
    if data:
      headers['Content-Type'] = 'application/json'
    request = urllib2.Request(url, data, headers)
    if send_delete:
      request.get_method = lambda: 'DELETE'
    return urllib2.urlopen(request).read()
