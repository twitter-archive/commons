from __future__ import absolute_import
from functools import wraps

from twitter.common import log
from twitter.common.contextutil import environment_as
from twitter.common.http import request, response
from twitter.common.http.plugin import Plugin

from bottle import HTTPResponse
import kerberos


class Kerberized(Plugin):
  """HTTPServer plugin for kerberos auth."""
  DEFAULT_AUTH_FAIL = 'kerberos auth failed'
  DEFAULT_AUTH_ERR = 'error during kerberos auth'
  DEFAULT_CONTENT_TYPE = 'text/plain'
  AUTH_HEADER = 'HTTP_AUTHORIZATION'

  def __init__(self,
               keytab,
               service='HTTP',
               fail_response=None,
               err_response=None,
               content_type=None):
    """Params

       keytab           path to kerberos keytab (e.g. '/etc/krb5.tab')
       service          kerberos service name (optional, defaults to HTTP)
       fail_response    response body to send on auth failures (optional)
       err_response     response body to send on auth errors (optional)
       content_type     content-type for fail/err responses, e.g. to support json APIs (optional)
    """

    self._keytab = keytab
    self._service = service
    self._fail_response = fail_response or self.DEFAULT_AUTH_FAIL
    self._err_response = err_response or self.DEFAULT_AUTH_ERR
    self._content_type = content_type or self.DEFAULT_CONTENT_TYPE

  def parse_auth_header(self, line):
    return line.split(' ', 1)

  def auth_error(self):
    resp = HTTPResponse(self._err_response, status=500)
    resp.set_header('Content-Type', self._content_type)
    return resp

  def auth_fail(self, gss_context=None):
    resp = HTTPResponse(self._fail_response, status=401)
    resp.set_header('Content-Type', self._content_type)
    resp.set_header(
      'WWW-Authenticate',
      'Negotiate' + (' ' + gss_context if gss_context else '')
    )
    return resp

  def check_result(self, result, success=1):
    return result == success

  def authorize(self, req, app, app_args, app_kwargs):
    """Perform a Kerberos authentication handshake with the KDC."""
    http_auth = req.environ.get(self.AUTH_HEADER)
    if not http_auth:
      log.info('kerberos: rejecting non-authed request from %s', req.environ.get('REMOTE_ADDR'))
      return self.auth_fail()

    log.debug('kerberos: processing auth: %s', http_auth)
    auth_type, auth_key = self.parse_auth_header(http_auth)

    if auth_type == 'Negotiate':
      # Initialize a kerberos context.
      try:
        result, context = kerberos.authGSSServerInit(self._service)
        log.debug('kerberos: authGSSServerInit(%s) -> %s, %s', self._service, result, context)
      except kerberos.GSSError as e:
        log.warning('kerberos: GSSError during init: %s', e)
        result, context = 0, None

      if not self.check_result(result):
        log.warning('kerberos: bad result from authGSSServerInit(%s): %s', self._service, result)
        return self.auth_error()

      # Process the next challenge step and retrieve the response.
      gss_key = None
      try:
        result = kerberos.authGSSServerStep(context, auth_key)
        log.debug('kerberos: authGSSServerStep(%s, %s) -> %s', context, auth_key, result)

        gss_key = kerberos.authGSSServerResponse(context)
        log.debug('kerberos: authGSSServerResponse(%s) -> %s', context, gss_key)
      except kerberos.GSSError as e:
        log.warning('kerberos: GSSError(%s)', e)
        result = 0

      if not self.check_result(result):
        return self.auth_fail(gss_key)

      # Retrieve the user id and add it to the request environment.
      username = kerberos.authGSSServerUserName(context)
      req.environ['REMOTE_USER'] = username
      log.info('kerberos: authenticated user %s from %s', username, req.environ.get('REMOTE_ADDR'))

      # Pass on the GSS response in the Bottle response.
      response.set_header('WWW-Authenticate', 'Negotiate ' + str(gss_key))

      # Clean up.
      kerberos.authGSSServerClean(context)

      return app(*app_args, **app_kwargs)
    else:
      return self.auth_fail()

  def apply(self, app, route):
    """Main entry-point for bottle plugins."""
    @wraps(app, assigned=())
    def wrapped_app(*args, **kwargs):
      with environment_as(KRB5_KTNAME=self._keytab):
        return self.authorize(request, app, args, kwargs)
    return wrapped_app

  def __call__(self, f):
    """Support usage as a route handler decorator to limit scope to individual routes. e.g.

       @Kerberized(keytab=keytab_path)
       @HttpServer.route('/blah')
       def blah_handler(self):
         return 'kerberized'
    """
    return self.apply(f, None)
