import os
import errno
import time
import httplib

class MirrorFile(object):
  def __init__(self, http_host, http_path, local_file, https=False):
    """
      Given a file pointed to by 'url', mirror it to 'local_file', providing operations
      to check that it's up to date.
    """
    self._http_path = http_path
    self._http_host = http_host
    self._local_filename = local_file
    self._connection_class = httplib.HTTPSConnection if https else httplib.HTTPConnection
    self._local_mtime = None
    self._web_mtime = None

  def _get_local_timestamp(self):
    try:
      stat = os.stat(self._local_filename)
      return stat.st_mtime
    except OSError as e:
      if e.errno == errno.ENOENT:
        self._local_mtime = None
      else:
        # File is inaccessible.
        raise
    return None

  def _get_web_timestamp(self):
    # TODO(wickman)  Wrap this in an expontential backoff.
    conn = self._connection_class(self._http_host)
    try:
      conn.request('HEAD', self._http_path)
    except httplib.CannotSendRequest:
      return None
    try:
      res = conn.getresponse()
    except (httplib.ResponseNotReady, httplib.BadStatusLine):
      return None
    if res is not None:
      last_modified = res.getheader('last-modified')
      if last_modified is not None:
        try:
          last_modified = time.strptime(last_modified, '%a, %d %b %Y %H:%M:%S %Z')
        except ValueError:
          return None
        return int(time.mktime(last_modified))
    return None

  def filename(self):
    return self._local_filename

  def refresh(self):
    """
      Refresh the local file if necessary.  Returns truthy if the underlying file changed.
    """
    self._local_mtime = self._get_local_timestamp()
    self._web_mtime = self._get_web_timestamp()
    if self._web_mtime is None:
      return None
    else:
      if self._web_mtime != self._local_mtime:
        return self._fetch()

  def _fetch(self):
    conn = self._connection_class(self._http_host)
    try:
      conn.request('GET', self._http_path)
    except httplib.CannotSendRequest:
      return None
    try:
      res = conn.getresponse()
    except httplib.ResponseNotReady, httplib.BadStatusLine:
      return None
    if res is not None:
      with open(self._local_filename, 'w') as fp:
        fp.write(res.read())
      os.utime(self._local_filename, (self._web_mtime, self._web_mtime))
      return True
