from abc import abstractmethod

from twitter.common.lang import Interface


class Plugin(Interface):
  @property
  def name(self):
    """The name of the plugin."""
    return self.__class__.__name__

  @property
  def api(self):
    # This Plugin is the duck-typed Bottle Plugin interface v2.
    return 2

  def setup(self, app):
    pass

  @abstractmethod
  def apply(self, callback, route):
    """Given the Bottle callback and Route object, return a (possibly)
       decorated version of the original callback function, e.g. a
       version that profiles the endpoint.

       For more information see:
         http://bottlepy.org/docs/stable/plugindev.html
    """

  def close(self):
    pass

