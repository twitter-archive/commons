`git`
=====

.. py:module:: twitter.common.git
               
Git `checkout(...)` and `branch(...)` context managers::

    import subprocess
    from twitter.common.git import branch
    with branch('master@{yesterday}'):
      subprocess.check_call('./pants tests/python/twitter/common:all') 

