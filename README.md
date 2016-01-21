Twitter common libraries for Python and the JVM
=============
[![Build Status](https://travis-ci.org/twitter/commons.svg?branch=master)](https://travis-ci.org/twitter/commons)

**Read more** http://twitter.github.com/commons/

## Compilation Prerequisite 
+ JDK 1.6+
+ Python 2.7+

## Compilation
The build tool is custom and hosted in the repository itself.

To build all **JVM** code and run all tests
````
   $ ./pants test {src,tests}/java/com/twitter/common::
   $ ./pants test {src,tests}/scala/com/twitter/common::
````

To build for **Python** commons:
````
   $ ./pants test tests/python/twitter/common/:all
````
To get help on *pants*:
````
   $ ./pants help
````

Refer to pants documentation at http://pantsbuild.github.io

### Usage questions and feature discussions
Please check the archives for twitter-commons@googlegroups.com then fire away if the question has not been addressed.

### Reporting bugs 
Please use the github issue tracker for twitter commons at:
https://github.com/twitter/commons/issues

## Copyright and License 
````
   Copyright 2015 Twitter, Inc.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this work except in compliance with the License.
   You may obtain a copy of the License in the LICENSE file, or at:

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
````

