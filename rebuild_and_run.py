#!/usr/bin/env python

import subprocess

def _print_loudly(msg):
  prefix = "*" * 5
  print prefix
  print prefix + " " + msg
  print prefix


_print_loudly("Rebuilding pants.")
subprocess.call('rm -rf pants.pex .pants.d', shell=True)
subprocess.call('./pants', shell=True)

_print_loudly("Building and running from binary deploy jar.")
subprocess.call("./pants goal binary --binary-deployjar src/java/com/foobar:example_bin",
                shell=True)
subprocess.call("java -jar ./dist/example_bin.jar", shell=True)

_print_loudly("Running with pants goal run.")
subprocess.call("./pants goal run src/java/com/foobar:example_bin", shell=True)

_print_loudly("Running unit test.")
subprocess.call("./pants goal test tests/java/com/foobar:example_test", shell=True)
