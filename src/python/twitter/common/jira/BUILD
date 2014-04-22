with open(os.path.join(os.path.dirname(__file__), '..', 'VERSION')) as fp:
  VERSION = fp.read().strip()

python_library(
  name = 'jira',
  sources = rglobs('*.py'),
  dependencies = [
    pants('src/python/twitter/common/log')
  ],
  provides = setup_py(
    name = 'twitter.common.jira',
    version = VERSION,
    description = 'twitter.common thin library for interacting with JIRA.',
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
