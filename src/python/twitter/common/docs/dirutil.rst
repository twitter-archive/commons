`twitter.common.dirutil`
========================

Useful utilities for manipulating and finding files and
directories. Use these to write less code! For example recursively
finding all the files with a particular extension is as simple as::

    >>> list(Fileset.rglobs("*/*.yml", root='/etc/foobar'))
    ['sphinx.yml', 'it/sphinx.yml', 'en/sphinx.yml']

Available Utilities
-------------------

.. automodule:: twitter.common.dirutil
   :members:
