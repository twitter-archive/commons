`twitter.common.net`
====================

Helpers to create SSH tunnels or SOCKS proxies.

.. py:module:: twitter.common.net

.. automodule:: twitter.common.net.tunnel
   :members:


Example Usage
-------------

to create a direct tunnel::

    from twitter.common.net.tunnel import TunnelHelper
    host, port = TunnelHelper.create_tunnel('foo.bar.net', 31337)
    urlopen('http://%s:%s/health' % (host, port)).read()

To create socks proxy::

    host, port = TunnelHelper.create_proxy()

To create a proxiable urllib opener::

    from twitter.common.net.socks import urllib_opener

    opener = urllib_opener(*TunnelHelper.create_proxy())
    opener.open('http://smf1-aes-23-sr3.prod.twitter.com:31337/health').read()

