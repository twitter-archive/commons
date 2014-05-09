python_library(
  name = 'rwbuf',
  sources = globs('*.py'),
  dependencies = [
    pants('src/python/twitter/common/lang')
  ],
  provides = setup_py(
    name = 'twitter.common.rwbuf',
    version = '0.1.0',
    description = 'twitter.common python client read-write buffer libs.',
    url = 'https://github.com/twitter/commons',
    license = 'Apache License, Version 2.0',
    zip_safe = True,
    classifiers = [
      'Intended Audience :: Developers',
      'License :: OSI Approved :: Apache Software License',
      'Operating System :: OS Independent',
      'Programming Language :: Python',
    ]
  )
)
