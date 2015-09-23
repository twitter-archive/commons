`twitter.common.lang`
=====================

This is our 2.x / 3.x compatibility swiss-army knife.  it also contains a lot of the idiomatic
boilerplate for writing code in science.  it is briefly covered in the ref:`stylguide`. You might
like:

* Singleton::

    >>> class Foo(Singleton):
    ...   pass
    >>> foo = Foo()
    >>> foo2 = Foo()
    >>> foo is foo2

* Interface::

    class GroupBase(Interface):
      @abstractmethod
      def info(self):
         """Here's a docstring!"""
         pass

    class Group(GroupBase):
      def info(self):
         ...implement...

  Interface is nice in that docstrings are inherited by implementing classes and Group(...) will
  literally fail to instantiate if it has not implemented all of the abstractmethods or
  abstractproperties defined.  Mostly just a thin wrapper around the Python abc module


* Compatibility

  A class that holds Python 2.x/3.x friendly versions of base types (`StringIO`, `BytesIO`, `str`, `bytes`, as well as
  a 2.x/3.x compatible `exec_function` and a backported `total_ordering` decorator.


.. py:module:: twitter.common.lang
