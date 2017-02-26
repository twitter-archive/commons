from __future__ import print_function

__author__ = 'Bill Farner'

import base64
import cookielib
import mimetools
import os
import getpass
import json
import sys
import urllib2
from urlparse import urljoin
from urlparse import urlparse


VERSION = '0.8-precommit'


class APIError(Exception):
  pass


class RepositoryInfo:
  """
  A representation of a source code repository.
  """
  def __init__(self, path=None, base_path=None, supports_changesets=False,
               supports_parent_diffs=False):
    self.path = path
    self.base_path = base_path
    self.supports_changesets = supports_changesets
    self.supports_parent_diffs = supports_parent_diffs
    self.debug('repository info: %s' % self)

  def debug(self, message):
    """
    Does nothing by default but can be oferwritten on an Repository info object
    to print the message to the screen and such.
    """
    pass

  def __str__(self):
    return ('Path: %s, Base path: %s, Supports changesets: %s' %
      (self.path, self.base_path, self.supports_changesets))


class ReviewBoardServer:
  """
  An instance of a Review Board server.
  """
  def __init__(self,
               url,
               cookie_file=None,
               info=None,
               repository=None,
               username=None,
               password=None,
               debug=False,
               timeout=None):
    self._debug = debug

    # Load the config and cookie files
    if cookie_file is None:
      if 'USERPROFILE' in os.environ:
        homepath = os.path.join(os.environ['USERPROFILE'],
                                'Local Settings', 'Application Data')
      elif 'HOME' in os.environ:
        homepath = os.environ['HOME']
      else:
        homepath = ''

      cookie_file = os.path.join(homepath, '.post-review-cookies.txt')

    if info is None:
      info = RepositoryInfo(path=repository, base_path='/')

    self.url = url
    if self.url[-1] != '/':
      self.url += '/'
    self.info = info
    self.cookie_file = cookie_file
    self.cookie_jar = cookielib.MozillaCookieJar(self.cookie_file)
    self.timeout = timeout

    if not self.has_valid_cookie() and (not username or not password):
      print('==> Review Board Login Required')
      print('Enter username and password for Review Board at %s' % self.url)
      username = raw_input('Username: ')
      password = getpass.getpass('Password: ')
    self._add_auth_params(username, password)

  def _add_auth_params(self, username, password):
    headers = [('User-agent', 'post-review/' + VERSION)]

    if username and password:
      self.debug('Authorizing as user %s' % username)
      base64string = base64.encodestring('%s:%s' % (username, password))
      base64string = base64string[:-1]
      headers.append(('Authorization', 'Basic %s' % base64string))

    # Set up the HTTP libraries to support all of the features we need.
    opener = urllib2.build_opener(urllib2.HTTPCookieProcessor(self.cookie_jar))
    opener.addheaders = headers
    urllib2.install_opener(opener)

  def get_url(self, rb_id):
    return urljoin(self.url, '/r/%s' % (rb_id))

  def debug(self, message):
    """
    Prints a debug message, if debug is enabled.
    """
    if self._debug:
      print('[Debug] %s' % message)

  def die(self, msg=None):
    """
    Cleanly exits the program with an error message. Erases all remaining
    temporary files.
    """
    raise Exception(msg)

  def has_valid_cookie(self):
    """
    Load the user's cookie file and see if they have a valid
    'rbsessionid' cookie for the current Review Board server.  Returns
    true if so and false otherwise.
    """
    try:
      parsed_url = urlparse(self.url)
      host = parsed_url[1]
      path = parsed_url[2] or '/'

      # Cookie files don't store port numbers, unfortunately, so
      # get rid of the port number if it's present.
      host = host.split(':')[0]

      self.debug('Looking for "%s %s" cookie in %s' %
                 (host, path, self.cookie_file))
      self.cookie_jar.load(self.cookie_file, ignore_expires=True)

      try:
        cookie = self.cookie_jar._cookies[host][path]['rbsessionid']

        if not cookie.is_expired():
          self.debug('Loaded valid cookie -- no login required')
          return True

        self.debug('Cookie file loaded, but cookie has expired')
      except KeyError:
        self.debug('Cookie file loaded, but no cookie for this server')
    except IOError, error:
      self.debug('Couldn\'t load cookie file: %s' % error)

    return False

  def new_review_request(self, changenum=None, submit_as=None, diff_only=False):
    """
    Creates a review request on a Review Board server, updating an
    existing one if the changeset number already exists.

    If submit_as is provided, the specified user name will be recorded as
    the submitter of the review request (given that the logged in user has
    the appropriate permissions).
    """
    try:
      data = { 'repository_path': self.info.path }

      if changenum:
        data['changenum'] = changenum

      if submit_as:
        self.debug('Submitting the review request as %s' % submit_as)
        data['submit_as'] = submit_as

      rsp = self.api_call('api/review-requests/new/', data)
    except APIError, e:
      rsp, = e.args

      if not diff_only:
        if rsp['err']['code'] == 204: # Change number in use
          self.debug('Review request already exists. Updating it...')
          rsp = self.api_call(
            'api/review-requests/%s/update_from_changenum/' %
            rsp['review_request']['id'])
        else:
          raise e

    self.debug('Review request created')
    return rsp['review_request']

  def set_submitted(self, review_request_id):
    """
    Marks a review request as submitted.
    """
    self.api_call('api/review-requests/%s/' % review_request_id, {
        'status': 'submitted',
        }, method='PUT')

  def set_discarded(self, review_request_id):
    """
    Marks a review request as discarded.
    """
    self.api_call('api/review-requests/%s/' % review_request_id, {
        'status': 'discarded',
        }, method='PUT')

  def send_review_reply(self, review_request_id, message):
    """
    Replies to a review with a message.
    """
    self.api_call('api/review-requests/%s/reviews/' % review_request_id, {
        'public': True,
        'body_top': message
        }, method='POST')

  def set_review_request_field(self, review_request, field, value):
    """
    Sets a field in a review request to the specified value.
    """
    rid = review_request['id']

    self.debug('Attempting to set field "%s" to "%s" for review request "%s"' %
               (field, value, rid))

    self.api_call('api/review-requests/%s/draft/set/' % rid,
            {field: value})

  def _smart_query(self, base_url, element_name, start=0, max_results=25):
    base_url += "&" if "?" in base_url else "?"

    if max_results < 0:
      rsp = self.api_call('%scounts-only=true' % base_url)
      count = rsp['count']

      files = []
      while len(files) < count:
        rsp = self.api_call('%sstart=%s&max-results=200' % (base_url, len(files)))
        files.extend(rsp[element_name])

      return files
    else:
      rsp = self.api_call('%sstart=%d&max-results=%d' % (base_url, start, max_results))
      return rsp[element_name]

  def fetch_review_requests(self,
                            time_added_from=None,
                            time_added_to=None,
                            last_updated_from=None,
                            last_updated_to=None,
                            from_user=None,
                            to_groups=None,
                            to_user_groups=None,
                            to_users=None,
                            to_users_directly=None,
                            ship_it=None,
                            status=None,
                            start=0,
                            max_results=25):
    """
    Returns a list of review requests that meet specified criteria.
    If max_results is negative, then ignores 'start' and returns all the matched review requests.
    """
    url = "api/review-requests/"

    params = [
      ("time-added-from", time_added_from),
      ("time-added-to", time_added_to),
      ("last-updated-from", last_updated_from),
      ("last-updated-to", last_updated_to),
      ("from-user", from_user),
      ("to-groups", to_groups),
      ("to-user-groups", to_user_groups),
      ("to-users", to_users),
      ("to-users-directly", to_users_directly),
      ("ship-it", ship_it),
      ("status", status)
    ]

    qs = "&".join(["%s=%s" % p for p in params if p[1] is not None])
    url = ("%s?%s" % (url, qs)) if len(qs) > 0 else url

    return self._smart_query(url, "review_requests", start, max_results)

  def get_review_request(self, rid):
    """
    Returns the review request with the specified ID.
    """
    rsp = self.api_call('api/review-requests/%s/' % rid)
    return rsp['review_request']

  def save_draft(self, review_request):
    """
    Saves a draft of a review request.
    """
    self.api_call('api/review-requests/%s/draft/save/' %
            review_request['id'])
    self.debug('Review request draft saved')

  def upload_diff(self, review_request, diff_content, parent_diff_content):
    """
    Uploads a diff to a Review Board server.
    """
    self.debug('Uploading diff, size: %d' % len(diff_content))

    if parent_diff_content:
      self.debug('Uploading parent diff, size: %d' % len(parent_diff_content))

    fields = {}
    files = {}

    if self.info.base_path:
      fields['basedir'] = self.info.base_path

    files['path'] = {
        'filename': 'diff',
        'content': diff_content
        }

    if parent_diff_content:
      files['parent_diff_path'] = {
          'filename': 'parent_diff',
          'content': parent_diff_content
          }

    self.api_call('api/review-requests/%s/diff/new/' %
                  review_request['id'], fields, files)

  def publish(self, review_request):
    """
    Publishes a review request.
    """
    self.debug('Publishing')
    self.api_call('api/review-requests/%s/publish/' %
                  review_request['id'])

  def fetch_reviews(self, rb_id, start=0, max_results=25):
    """
    Fetches reviews in response to a review request.
    If max_results is negative, then ignores 'start' and returns all reviews.
    """
    url = 'api/review-requests/%s/reviews/' % rb_id
    return self._smart_query(url, 'reviews', start, max_results)

  def get_reviews(self, rb_id, start=0, max_results=25):
    return self.fetch_reviews(rb_id, start, max_results)

  def get_replies(self, rb_id, review, start=0, max_results=25):
    """
    Fetches replies to a given review in a review request.
    If max_results is negative, then ignores 'start' and returns all reviews.
    """
    url = 'api/review-requests/%s/reviews/%s/replies/' % (rb_id, review)
    return self._smart_query(url, 'replies', start, max_results)

  def process_json(self, data):
    """
    Loads in a JSON file and returns the data if successful. On failure,
    APIError is raised.
    """
    rsp = json.loads(data)

    if rsp['stat'] == 'fail':
      raise APIError, rsp

    return rsp

  def _make_url(self, path):
    """Given a path on the server returns a full http:// style url"""
    url = urljoin(self.url, path)
    if not url.startswith('http'):
      url = 'http://%s' % url
    return url

  def http_request(self, path, fields=None, files=None, headers=None, method=None):
    """
    Executes an HTTP request against the specified path, storing any cookies that
    were set.  By default, if there are no field or files a GET is issued, otherwise a POST is used.
    The HTTP verb can be customized by specifying method.
    """
    if fields:
      debug_fields = fields.copy()
    else:
      debug_fields = {}

    if 'password' in debug_fields:
      debug_fields['password'] = '**************'
    url = self._make_url(path)
    self.debug('HTTP request to %s: %s' % (url, debug_fields))

    headers = headers or {}
    if fields or files:
      content_type, body = self._encode_multipart_formdata(fields, files)
      headers.update({
        'Content-Type': content_type,
        'Content-Length': str(len(body))
      })
      r = urllib2.Request(url, body, headers)
    else:
      r = urllib2.Request(url, headers=headers)

    if method:
      r.get_method = lambda: method

    try:
      return urllib2.urlopen(r, timeout=self.timeout).read()
    except urllib2.URLError, e:
      try:
        self.debug(e.read())
      except AttributeError:
        pass

      self.die('Unable to access %s. The host path may be invalid\n%s' %
               (url, e))
    except urllib2.HTTPError, e:
      return self.die('Unable to access %s (%s). The host path may be invalid'
                       '\n%s' % (url, e.code, e.read()))

  def api_call(self, path, fields=None, files=None, method=None):
    """
    Performs an API call at the specified path. By default, if there are no field or files a GET is
    issued, otherwise a POST is used. The HTTP verb can be customized by specifying method.
    """
    return self.process_json(
      self.http_request(path, fields, files, {'Accept': 'application/json'}, method=method))

  def _encode_multipart_formdata(self, fields, files):
    """
    Encodes data for use in an HTTP POST or PUT.
    """
    BOUNDARY = mimetools.choose_boundary()
    content = []

    fields = fields or {}
    files = files or {}

    for key in fields:
      content.append('--' + BOUNDARY + '\r\n')
      content.append('Content-Disposition: form-data; name="%s"\r\n' % key)
      content.append('\r\n')
      content.append(fields[key])
      content.append('\r\n')

    for key in files:
      filename = files[key]['filename']
      value = files[key]['content']
      content.append('--' + BOUNDARY + '\r\n')
      content.append('Content-Disposition: form-data; name="%s"; ' % key)
      content.append('filename="%s"\r\n' % filename)
      content.append('\r\n')
      content.append(value)
      content.append('\r\n')

    content.append('--')
    content.append(BOUNDARY)
    content.append('--\r\n')
    content.append('\r\n')

    content_type = 'multipart/form-data; boundary=%s' % BOUNDARY

    return content_type, ''.join(map(str, content))

  def post_review(self, changenum, diff_content=None,
          parent_diff_content=None, submit_as=None,
          target_groups=None, target_people=None, summary=None,
          branch=None, bugs_closed=None, description=None,
          testing_done=None, rid=None, publish=True):
    """
    Attempts to create a review request on a Review Board server
    and upload a diff. On success, the review request path is displayed.
    """
    try:
      save_draft = False

      if rid:
        review_request = self.get_review_request(rid)
      else:
        review_request = self.new_review_request(changenum, submit_as)

      if target_groups:
        self.set_review_request_field(review_request, 'target_groups',
                                      target_groups)
        save_draft = True

      if target_people:
        self.set_review_request_field(review_request, 'target_people',
                                      target_people)
        save_draft = True

      if summary:
        self.set_review_request_field(review_request, 'summary',
                                      summary)
        save_draft = True

      if branch:
        self.set_review_request_field(review_request, 'branch', branch)
        save_draft = True

      if bugs_closed:
        self.set_review_request_field(review_request, 'bugs_closed',
                                      bugs_closed)
        save_draft = True

      if description:
        self.set_review_request_field(review_request, 'description',
                                      description)
        save_draft = True

      if testing_done:
        self.set_review_request_field(review_request, 'testing_done',
                                      testing_done)
        save_draft = True

      if save_draft:
        self.save_draft(review_request)
    except APIError, e:
      rsp, = e.args
      if rid:
        return self.die('Error getting review request %s: %s (code %s)' %
                        (rid, rsp['err']['msg'], rsp['err']['code']))
      else:
        error_message = 'Error creating review request: %s (code %s)\n' % (rsp['err']['msg'],
                                                                           rsp['err']['code'])
        if rsp['err']['code'] == 105:
          bad_keys = rsp['fields']
          if bad_keys:
            error_message = 'Invalid key-value pairs:\n'
            for key, issues in bad_keys.items():
              error_message += '%s: %s\n' % (key, ', '.join(issues))

        return self.die(error_message)

    if not self.info.supports_changesets:
      try:
        self.upload_diff(review_request, diff_content, parent_diff_content)
      except APIError, e:
        rsp, = e.args
        print('Error uploading diff: %s (%s)' % (rsp['err']['msg'], rsp['err']['code']))
        self.debug(rsp)
        self.die('Your review request still exists, but the diff is not '
                 'attached.')

    if publish:
      self.publish(review_request)

    request_url = 'r/' + str(review_request['id'])
    review_url = urljoin(self.url, request_url)

    if not review_url.startswith('http'):
      review_url = 'http://%s' % review_url

    sys.stderr.write('Review request #%s posted.\n' % review_request['id'])
    sys.stderr.write('\n%s\n' % review_url)

    return 1

  def get_raw_diff(self, rb_id):
    """
    Returns the raw diff for the given reviewboard item.
    """
    return self.http_request('/r/%s/diff/raw/' % rb_id, {})

  def get_changes(self, rb_id, start=0, max_results=25):
    """
    Returns a list of changes of the sepcified review request.
    """
    url = 'api/review-requests/%s/changes/' % rb_id
    return self._smart_query(url, 'changes', start, max_results)

  def get_diffs(self, rb_id):
    """
    Returns a list of diffs of the sepcified review request.
    """
    rsp = self.api_call('api/review-requests/%s/diffs/' % rb_id)
    return rsp['diffs']

  def get_files(self, rb_id, revision, start=0, max_results=25):
    """
    Returns a list of files in the specified diff.
    If max_results is negative, then ignores 'start' and returns all the files.
    """
    url = 'api/review-requests/%d/diffs/%d/files/' % (rb_id, revision)
    return self._smart_query(url, "files", start, max_results)

  def get_diff_comments(self, rb_id, revision, file_id, start=0, max_results=25):
    """
    Returns a list of diff comments for the specified file.
    If max_results is negative, then ignores 'start' and returns all the files.
    """
    url = 'api/review-requests/%d/diffs/%d/files/%d/diff-comments/' % (rb_id, revision, file_id)
    return self._smart_query(url, "diff_comments", start, max_results)
