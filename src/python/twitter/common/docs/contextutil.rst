`twitter.common.contextutil`
============================

.. py:module:: twitter.common.concurrent
               
A very handy set of context managers. For example::


    with environment_as(LD_LIBRARY_PATH=':'.join(libs)):
      po = subprocess.Popen(...)

    with temporary_dir() as td:
      with open(os.path.join(td, 'my_file.txt')) as fp:
         fp.write(junk)

    with temporary_file() as fp:
      fp.write('woot')
      fp.sync()
      <pass on to something>

    with pushd('subdir/data'):
      glob.glob("*json") # run code in subdir

    timer = Timer()
    with timer:
      ... do things ...
    print(timer.elapsed)

    
As well as  some specialized `open` contexts, e.g. `open_zip`, `open_tar`...

