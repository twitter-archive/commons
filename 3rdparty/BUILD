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

###################
# Production libs
#

jar_library(name = 'antlr2',
            dependencies = [ jar(org = 'antlr', name = 'antlr', rev = '2.7.7') ])

jar_library(name = 'antlr3',
            dependencies = [ jar(org = 'antlr', name = 'antlr', rev = '3.1.3') ])

jar_library(name = 'aopalliance',
            dependencies = [
              jar(org = 'aopalliance', name = 'aopalliance', rev = '1.0').withSources()
            ]
           )

jar_library(name = 'cascading-core',
            dependencies = [ jar(org = 'cascading', name = 'cascading-core', rev = '1.2.3') ]
           )

jar_library(name = 'cascading.jdbc',
            dependencies = [ jar(org = 'r0man', name = 'cascading.jdbc', rev = '1.2') ]
           )

jar_library(name = 'checkstyle',
            dependencies = [
              jar(org = 'com.puppycrawl.tools', name = 'checkstyle', rev = '5.3')
                .exclude(org = 'com.google.collections', name = 'google-collections')
                .withSources(),
              # Needs this as the replacement for the excluded google-collections.
              pants(':guava'),
            ]
           )

jar_library(name = 'cloud9',
            dependencies = [
              jar(org = 'edu.umd', name = 'cloud9', rev = '1.2.2').withSources()
                .exclude(org = 'fastutil', name = 'fastutil')
            ]
           )

jar_library(name = 'commons-beanutils',
            dependencies = [
              jar(org = 'commons-beanutils', name = 'commons-beanutils', rev = '1.8.3').withSources()
            ]
           )

jar_library(name = 'commons-codec',
            dependencies = [
              jar(org = 'commons-codec', name = 'commons-codec', rev = '1.4').withSources()
            ]
           )

jar_library(name = 'commons-io',
            dependencies = [
              jar(org = 'commons-io', name = 'commons-io', rev = '1.4').withSources()
            ]
           )

jar_library(name = 'commons-lang',
            dependencies = [
              jar(org = 'commons-lang', name = 'commons-lang', rev = '2.4').withSources()
            ]
           )

jar_library(name = 'gson',
            dependencies = [ jar(org = 'com.google.code.gson', name = 'gson', rev = '1.6') ])

jar_library(name = 'guava',
            dependencies = [
              jar(
                org = 'com.google.guava', name = 'guava', rev = 'r09',
                apidocs = 'http://guava-libraries.googlecode.com/svn/tags/release09/javadoc/'
              ).withSources(),

              # This dependency should not need to be explicit but its currently required to work
              # around a doc generation bug (RTS-524).
              jar(org = 'javax.inject', name = 'javax.inject', rev = '1').withSources(),
            ]
           )

jar_library(name = 'guice',
            dependencies = [
              jar(
                org = 'com.google.inject', name = 'guice', rev = '3.0',
                apidocs = 'http://google-guice.googlecode.com/svn/tags/3.0/javadoc/'
              ).withSources()
            ]
           )

jar_library(name = 'guice-multibindings',
            dependencies = [
              jar(org = 'com.google.inject.extensions', name = 'guice-multibindings', rev = '3.0')
                .withSources()
            ]
           )

jar_library(name = 'guice-servlet',
            dependencies = [
              jar(org = 'com.google.inject.extensions', name = 'guice-servlet', rev = '3.0')
                .withSources()
            ]
           )

jar_library(name = 'hamcrest-core',
            dependencies = [ jar(org = 'org.hamcrest', name = 'hamcrest-core', rev = '1.2') ])

jar_library(name = 'jetty',
            dependencies = [
              jar(org = 'org.mortbay.jetty', name = 'jetty', rev = '6.1.25').withSources()
            ]
           )

jar_library(name = 'jsr305',
            dependencies = [
              jar(org = 'com.google.code.findbugs', name = 'jsr305', rev = '1.3.9')
            ]
           )

jar_library(name = 'lucene-analyzers',
            dependencies = [
              jar(org = 'org.apache.lucene', name = 'lucene-analyzers', rev = '3.0.2',
                  apidocs = 'http://lucene.apache.org/java/3_0_2/api/all/').withSources()
             ]
            )

jar_library(name = 'lucene-core',
            dependencies = [
              jar(org = 'org.apache.lucene', name = 'lucene-core', rev = '3.0.2',
                  apidocs = 'http://lucene.apache.org/java/3_0_2/api/all/').withSources()
            ]
           )

jar_library(name = 'lucene-snowball',
            dependencies = [
              jar(org = 'org.apache.lucene', name = 'lucene-snowball', rev = '3.0.2',
                  apidocs = 'http://lucene.apache.org/java/3_0_2/api/all/').withSources()
            ]
           )

jar_library(name = 'ostrich',
            dependencies = [ jar(org = 'com.twitter', name = 'ostrich', rev = '2.3.6') ])

jar_library(name = 'protobuf-java',
            dependencies = [
              jar(org = 'com.google.protobuf', name = 'protobuf-java', rev = '2.3.0')
            ]
           )

jar_library(name = 'scala-library',
            dependencies = [
              jar(org='org.scala-lang', name='scala-library', rev='2.8.1').withSources()
            ]
           )

jar_library(name = 'servlet-api',
            dependencies = [
              jar(org = 'javax.servlet', name = 'servlet-api', rev = '2.5').withSources()
            ]
           )

jar_library(name = 'slf4j-api',
            dependencies = [
              jar(org = 'org.slf4j', name = 'slf4j-api', rev = '1.6.1').withSources()
            ]
           )

jar_library(name = 'slf4j-jdk14',
            dependencies = [
              jar(org = 'org.slf4j', name = 'slf4j-jdk14', rev = '1.6.1').withSources()
            ]
           )

jar_library(name = 'spy-memcached',
            dependencies = [ jar(org = 'spy', name = 'spymemcached', rev = '2.7.1') ])

jar_library(name = 'stringtemplate',
            dependencies = [
              jar(org = 'org.antlr', name = 'stringtemplate', rev = '3.2.1').withSources()
            ]
           )

jar_library(name = 'thrift-0.5',
            dependencies = [
              jar(org = 'org.apache.thrift', name = 'libthrift', rev = '0.5.0'),
              pants(':slf4j-api'),
              pants(':slf4j-jdk14'), # need a concrete slf4j backend at runtime
            ]
           )

jar_library(name = 'thrift',
            dependencies = [ pants(':thrift-0.5') ])

jar_library(name = 'twitter4j',
            dependencies = [
              jar(org = 'org.twitter4j', name = 'twitter4j-core', rev = '2.1.6-patched-1')
                .intransitive()
            ]
           )

jar_library(name = 'twitter-text',
            dependencies = [ jar(org = 'com.twitter', name = 'twitter-text', rev = '1.4.0') ])

jar_library(name = 'visualization-datasource',
            dependencies = [
              jar(org = 'com.google.visualization', name = 'visualization-datasource', rev = '1.0.2')
                .exclude(org = 'com.google.collections', name = 'google-collections')
              ]
             )

jar_library(name = 'zookeeper',
            dependencies = [
              jar(org = 'org.apache.zookeeper', name = 'zookeeper', rev = '3.3.3').withSources()
                .exclude(org = 'jline')
                .exclude(org = 'javax.jms')
                .exclude(org = 'com.sun.jdmk')
                .exclude(org = 'com.sun.jmx')
            ]
           )

###############
# Test support
#

jar_library(name = 'easymock',
            dependencies = [ jar(org = 'org.easymock', name = 'easymock', rev = '3.0') ])

jar_library(name = 'mockito',
            dependencies = [ jar(org = 'org.mockito', name = 'mockito-all', rev = '1.8.4') ])

jar_library(name = 'junit',
            dependencies = [ jar(org = 'junit', name = 'junit', rev = '4.8.2').withSources() ])

jar_library(name = 'specs',
            dependencies = [
              jar(org = 'org.scala-tools.testing', name = 'specs_2.8.0', rev = '1.6.5')
            ]
           )

jar_library(name = 'tl4j',
            dependencies = [
              jar(org = 'com.google.testing', name = 'test-libraries-for-java', rev = '1.1.1')
            ]
           )

# TODO(John Sirois): support an ivy 'runtime' configuration and punch through a way to indicate this
# via pants
jar_library(name = 'easymockclassextension',
            dependencies = [
              pants(':easymock'),
              jar(org = 'org.easymock', name = 'easymockclassextension', rev = '3.0'),
              jar(org = 'cglib', name = 'cglib', rev = '2.2'),
              jar(org = 'org.objenesis', name = 'objenesis', rev = '1.2'),
            ]
           )
