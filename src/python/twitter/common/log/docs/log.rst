`twitter.common.log`
====================

This is the second most used library in `twitter.common`.  All you need to do is `from twitter.common
import log` then use it as you would anywhere else.  No need to `logging.getLogger(__name__)` or
anything like that.

Also contains `parsers`. This is a submodule of `twitter.common.log` which can parse google-style
and zookeeper-style log lines from `twitter.common.log` and multiplex them together should you ever
find the need to do that.

This module also provides a ton of command line options (via a
:ref:`twitter.common.app.module`) to your `twitter.common.app` such as
`--log_to_stderr`, `--log_to_disk`, `--log_simple` as well as a few
logging classes: `plain`, `google`, and `scribe`.  Grep for
'twitter.common import log' and 'LogOptions' for uses throughout.

.. automodule:: twitter.common.log
   :members: debug, info, warning, warn, error, error, fatal, log, logger, init

