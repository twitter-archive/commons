# ==================================================================================================
# Copyright 2011 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this work except in compliance with the License.
# You may obtain a copy of the License in the LICENSE file, or at:
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

# This BUILD file defines common external library dependencies used across many modules.  Modules
# can use these jar_library deps by specifying pants('3rdparty:[name]') in their dependencies list.

from pants import *



###################
# Production libs
#

jar_library('antlr', jar(org = 'antlr', name = 'antlr', rev = '2.7.7'))

jar_library('aopalliance',
            jar(org = 'aopalliance', name = 'aopalliance', rev = '1.0').withSources())

jar_library('checkstyle',
            jar(org = 'com.puppycrawl.tools', name = 'checkstyle', rev = '5.3')
              .exclude(org = 'com.google.collections', name = 'google-collections'),
            # Needs this as the replacement for the excluded google-collections.
            pants(':guava'))

jar_library('commons-beanutils',
            jar(org = 'commons-beanutils', name = 'commons-beanutils', rev = '1.8.3').withSources())
jar_library('commons-codec',
            jar(org = 'commons-codec', name = 'commons-codec', rev = '1.4').withSources())
jar_library('commons-io', jar(org = 'commons-io', name = 'commons-io', rev = '1.4').withSources())
jar_library('commons-lang',
            jar(org = 'commons-lang', name = 'commons-lang', rev = '2.4').withSources())

jar_library('gson', jar(org = 'com.google.code.gson', name = 'gson', rev = '1.6'))

jar_library('guava', jar(org = 'com.google.guava', name = 'guava', rev = 'r08').withSources())

jar_library('guice', jar(org = 'com.google.inject', name = 'guice', rev = '2.0').withSources())
jar_library('guice-multibindings',
            jar(org = 'com.google.inject.extensions', name = 'guice-multibindings', rev = '2.0'))
jar_library('guice-servlet',
            jar(org = 'com.google.inject.extensions', name = 'guice-servlet', rev = '2.0')
              .withSources())

jar_library('hamcrest-core', jar(org = 'org.hamcrest', name = 'hamcrest-core', rev = '1.2'))

jar_library('jetty', jar(org = 'org.mortbay.jetty', name = 'jetty', rev = '6.1.25').withSources())

jar_library('jsr305', jar(org = 'com.google.code.findbugs', name = 'jsr305', rev = '1.3.9'))

jar_library('ostrich', jar(org = 'com.twitter', name = 'ostrich', rev = '2.3.6'))

jar_library('protobuf-java',
            jar(org = 'com.google.protobuf', name = 'protobuf-java', rev = '2.3.0'))

jar_library('reflections',
            jar(org = 'org.reflections', name = 'reflections', rev = '0.9.5-RC2')
              .exclude(org = 'ch.qos.logback', name='logback-classic'))

jar_library('scala-library',
            jar(org='org.scala-lang', name='scala-library', rev='2.8.1').withSources())

jar_library('servlet-api',
            jar(org = 'javax.servlet', name = 'servlet-api', rev = '2.5').withSources())

jar_library('slf4j-api', jar(org = 'org.slf4j', name = 'slf4j-api', rev = '1.5.10').withSources())
jar_library('slf4j-jdk14',
            jar(org = 'org.slf4j', name = 'slf4j-jdk14', rev = '1.5.10').withSources())

jar_library('spy-memcached', jar(org = 'spy', name = 'memcached', rev = '2.4.2'))

jar_library('stringtemplate',
            jar(org = 'org.antlr', name = 'stringtemplate', rev = '3.2.1').withSources())

jar_library('thrift-0.5',
  jar(org = 'org.apache.thrift', name = 'libthrift', rev = '0.5.0'),
  pants(':slf4j-api'),
  pants(':slf4j-jdk14'), # need a concrete slf4j backend at runtime
)
jar_library('thrift', pants(':thrift-0.5'))

jar_library('twitter4j',
  jar(org = 'org.twitter4j', name = 'twitter4j-core', rev = '2.1.6-patched-1').intransitive())

jar_library('visualization-datasource',
            jar(org = 'com.google.visualization', name = 'visualization-datasource', rev = '1.0.2')
              .exclude(org = 'com.google.collections', name = 'google-collections'))

jar_library('zookeeper',
  jar(org = 'org.apache.zookeeper', name = 'zookeeper', rev = '3.3.1').withSources()
    .exclude(org = 'jline')
    .exclude(org = 'javax.jms')
    .exclude(org = 'com.sun.jdmk')
    .exclude(org = 'com.sun.jmx'))

###############
# Test support
#

jar_library('easymock', jar(org = 'org.easymock', name = 'easymock', rev = '3.0'))
jar_library('mockito', jar(org = 'org.mockito', name = 'mockito-all', rev = '1.8.4'))
jar_library('junit', jar(org = 'junit', name = 'junit', rev = '4.8.2').withSources())
jar_library('specs', jar(org = 'org.scala-tools.testing', name = 'specs_2.8.0', rev = '1.6.5'))
jar_library('tl4j',
            jar(org = 'com.google.testing', name = 'test-libraries-for-java', rev = '1.1.1'))

# TODO(John Sirois): support an ivy 'runtime' configuration and punch through a way to indicate this
# via pants
jar_library('easymockclassextension',
  pants(':easymock'),
  jar(org = 'org.easymock', name = 'easymockclassextension', rev = '3.0'),
  jar(org = 'cglib', name = 'cglib', rev = '2.2'),
  jar(org = 'org.objenesis', name = 'objenesis', rev = '1.2'))
