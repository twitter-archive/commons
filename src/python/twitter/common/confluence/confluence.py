# ==================================================================================================
# Copyright 2012 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this work except in compliance with the License.
# You may obtain a copy of the License in the LICENSE file, or at:
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

from twitter.common import log

import getpass
import urllib

try:
  from xmlrpclib import ServerProxy, Fault
except ImportError:
  from xmlrpc.client import ServerProxy, Fault


class ConfluenceError(Exception):
  """Indicates a problem performing an action with confluence."""


class Confluence(object):
  """Interface for fetching and storing data in confluence."""

  def __init__(self, server, server_url, session_token):
    """Initialize with an established confluence connection."""
    self._server = server
    self._server_url = server_url
    self._session_token = session_token

  @staticmethod
  def login(confluence_url, user=None):
    """Prompts the user to log in to confluence, and returns a Confluence object.

    raises ConfluenceError if login is unsuccessful.
    """
    server = ServerProxy(confluence_url + '/rpc/xmlrpc')
    user = user or getpass.getuser()
    password = getpass.getpass('Please enter confluence password for %s: ' % user)
    try:
      return Confluence(server, confluence_url, server.confluence1.login(user, password))
    except Fault as e:
      raise ConfluenceError('Failed to log in to %s: %s' % (confluence_url, e))

  @staticmethod
  def get_url(server_url, wiki_space, page_title):
    """ return the url for a confluence page in a given space and with a given
    title. """
    return '%s/display/%s/%s' % (server_url, wiki_space, urllib.quote_plus(page_title))

  def logout(self):
    """Terminates the session and connection to the server.

    Upon completion, the invoking instance is no longer usable to communicate with confluence.
    """
    self._server.confluence1.logout(self._session_token)

  def getpage(self, wiki_space, page_title):
    """ Fetches a page object.

    Returns None if the page does not exist or otherwise could not be fetched.
    """
    try:
      return self._server.confluence1.getPage(self._session_token, wiki_space, page_title)
    except Fault as e:
      log.warn('Failed to fetch page %s: %s' % (page_title, e))
      return None

  def storepage(self, page):
    """Stores a page object, updating the page if it already exists.

    returns the stored page, or None if the page could not be stored.
    """
    try:
      return self._server.confluence1.storePage(self._session_token, page)
    except Fault as e:
      log.error('Failed to store page %s: %s' % (page.get('title', '[unknown title]'), e))
      return None

  def removepage(self, page):
    """Deletes a page from confluence.

    raises ConfluenceError if the page could not be removed.
    """
    try:
      self._server.confluence1.removePage(self._session_token, page)
    except Fault as e:
      raise ConfluenceError('Failed to delete page: %s' % e)

  def create(self, space, title, content, parent_page=None, **pageoptions):
    """ Create a new confluence page with the given title and content.  Additional page options
    available in the xmlrpc api can be specified as kwargs.

    returns the created page or None if the page could not be stored.
    raises ConfluenceError if a parent page was specified but could not be found.
    """

    pagedef = dict(
      space = space,
      title = title,
      url = Confluence.get_url(self._server_url, space, title),
      content = content,
      contentStatus = 'current',
      current = True
    )
    pagedef.update(**pageoptions)

    if parent_page:
      # Get the parent page id.
      parent_page_obj = self.getpage(space, parent_page)
      if parent_page_obj is None:
        raise ConfluenceError('Failed to find parent page %s in space %s' % (parent_page, space))
      pagedef['parentId'] = parent_page_obj['id']

    # Now create the page
    return self.storepage(pagedef)
