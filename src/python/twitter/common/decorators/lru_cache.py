# Taken from http://code.activestate.com/recipes/578078
# A 2.6.x backport of Python 3.3 functools.lru_cache
#
# Modified from 4=>2 length indents for code consistency.
#
# Added an on_eviction parameter that gets applied to each element
# when it is evicted from the cache, e.g. file.close(...).

from collections import namedtuple
from functools import update_wrapper
from threading import Lock

_CacheInfo = namedtuple("CacheInfo", ["hits", "misses", "maxsize", "currsize"])

def lru_cache(maxsize=100, typed=False, on_eviction=lambda x:x):
  """Least-recently-used cache decorator.

  If *maxsize* is set to None, the LRU features are disabled and the cache
  can grow without bound.

  If *typed* is True, arguments of different types will be cached separately.
  For example, f(3.0) and f(3) will be treated as distinct calls with
  distinct results.

  Arguments to the cached function must be hashable.

  View the cache statistics named tuple (hits, misses, maxsize, currsize) with
  f.cache_info().  Clear the cache and statistics with f.cache_clear().
  Access the underlying function with f.__wrapped__.

  See:  http://en.wikipedia.org/wiki/Cache_algorithms#Least_Recently_Used
  """

  # Users should only access the lru_cache through its public API:
  #       cache_info, cache_clear, and f.__wrapped__
  # The internals of the lru_cache are encapsulated for thread safety and
  # to allow the implementation to change (including a possible C version).
  def decorating_function(user_function):
    cache = dict()
    stats = [0, 0]                  # make statistics updateable non-locally
    HITS, MISSES = 0, 1             # names for the stats fields
    kwd_mark = (object(),)          # separate positional and keyword args
    cache_get = cache.get           # bound method to lookup key or return None
    _len = len                      # localize the global len() function
    lock = Lock()                   # because linkedlist updates aren't threadsafe
    root = []                       # root of the circular doubly linked list
    nonlocal_root = [root]                  # make updateable non-locally
    root[:] = [root, root, None, None]      # initialize by pointing to self
    PREV, NEXT, KEY, RESULT = 0, 1, 2, 3    # names for the link fields

    def make_key(args, kwds, typed, tuple=tuple, sorted=sorted, type=type):
      # helper function to build a cache key from positional and keyword args
      key = args
      if kwds:
        sorted_items = tuple(sorted(kwds.items()))
        key += kwd_mark + sorted_items
      if typed:
        key += tuple(type(v) for v in args)
        if kwds:
          key += tuple(type(v) for k, v in sorted_items)
      return key

    if maxsize == 0:
      def wrapper(*args, **kwds):
        # no caching, just do a statistics update after a successful call
        result = user_function(*args, **kwds)
        stats[MISSES] += 1
        return result

    elif maxsize is None:
      def wrapper(*args, **kwds):
        # simple caching without ordering or size limit
        key = make_key(args, kwds, typed) if kwds or typed else args
        result = cache_get(key, root)   # root used here as a unique not-found sentinel
        if result is not root:
          stats[HITS] += 1
          return result
        result = user_function(*args, **kwds)
        cache[key] = result
        stats[MISSES] += 1
        return result

    else:
      def wrapper(*args, **kwds):
        # size limited caching that tracks accesses by recency
        key = make_key(args, kwds, typed) if kwds or typed else args
        with lock:
          link = cache_get(key)
          if link is not None:
            # record recent use of the key by moving it to the front of the list
            root, = nonlocal_root
            link_prev, link_next, key, result = link
            link_prev[NEXT] = link_next
            link_next[PREV] = link_prev
            last = root[PREV]
            last[NEXT] = root[PREV] = link
            link[PREV] = last
            link[NEXT] = root
            stats[HITS] += 1
            return result
        result = user_function(*args, **kwds)
        with lock:
          root = nonlocal_root[0]
          if _len(cache) < maxsize:
            # put result in a new link at the front of the list
            last = root[PREV]
            link = [last, root, key, result]
            cache[key] = last[NEXT] = root[PREV] = link
          else:
            # use root to store the new key and result
            root[KEY] = key
            root[RESULT] = result
            cache[key] = root
            # empty the oldest link and make it the new root
            root = nonlocal_root[0] = root[NEXT]
            evicted = cache.pop(root[KEY])
            on_eviction(evicted[RESULT])
            root[KEY] = None
            root[RESULT] = None
          stats[MISSES] += 1
        return result

    def cache_info():
      """Report cache statistics"""
      with lock:
        return _CacheInfo(stats[HITS], stats[MISSES], maxsize, len(cache))

    def cache_clear():
      """Clear the cache and cache statistics"""
      with lock:
        for value in cache.values():
          on_eviction(value[RESULT])
        cache.clear()
        root = nonlocal_root[0]
        root[:] = [root, root, None, None]
        stats[:] = [0, 0]

    wrapper.__wrapped__ = user_function
    wrapper.cache_info = cache_info
    wrapper.cache_clear = cache_clear
    return update_wrapper(wrapper, user_function)

  return decorating_function
