`dirutil`
=========

.. py:module:: twitter.common.dirutil
               
Useful utilities for manipulating/finding files and directories.

 * `safe_mkdir` - like `mkdir -p`
 * `safe_mkdtemp` - make a temporary directory that is cleaned up when this process exits
 * `safe_rmtree` - `rm -rf`
 * `safe_open` - opens a file but ensures the parent exists via safe_mkdir
 * `safe_delete` - deletes a file but capture common errors like ENOENT
 * `safe_size` - get the size reported by ls
 * `safe_bsize` - get the best guess for size on disk
 * `chmod_plus_x` - chmod +x
 * `du` - yep
 * `touch` - yep
 * `lock_file`
 * `unlock_file`
 * `tail_f` - stream the lines of a file via a generator as they are produced
 * `Fileset.globs(...)` - local directory globbing
 * `Fileset.rglobs(...)` - recursive globbing
 * `Fileset.zglobs(...)` - zsh style globbing
