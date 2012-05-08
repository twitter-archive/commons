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
              jar(org = 'aopalliance', name = 'aopalliance', rev = '1.0').with_sources()
            ]
          )

jar_library(name = 'args4j',
            dependencies = [ jar(org = 'args4j', name = 'args4j', rev = '2.0.16').with_sources() ])


jar_library(name = 'cascading-core',
            dependencies = [ jar(org = 'cascading', name = 'cascading-core', rev = '1.2.3',
                             force = True )]
           ).with_description('''
This is cascading 1, only project depends upon this rule will likely to have confliction with
cascading 2 which is used by elephant bird, so depends upon this explicitly to force the right
version.
Notice: force is NOT transitive, so any indirectly depended package will not have forced version
           ''')


jar_library(name = 'cascading2-core',
            dependencies = [
              jar(org = 'cascading', name = 'cascading-core', rev = '2.0.0-wip-291')
                .exclude(org = 'org.apache.hadoop', name="hadoop-core")
            ])

jar_library(name = 'cascading-kryo',
            dependencies = [
              jar(org = 'cascading.kryo', name = 'cascading.kryo', rev = '0.3.1')
            ])

jar_library(name = 'maple',
            dependencies = [
              jar(org = 'com.twitter', name = 'maple', rev = '0.1.7')
            ])

jar_library(name = 'cascading2-local',
            dependencies = [
              jar(org = 'cascading', name = 'cascading-local', rev = '2.0.0-wip-291')
                .exclude(org = 'org.apache.hadoop', name="hadoop-core")
            ])

jar_library(name = 'cascading2-hadoop',
            dependencies = [
              jar(org = 'cascading', name = 'cascading-hadoop', rev = '2.0.0-wip-291')
                .exclude(org = 'org.apache.hadoop', name="hadoop-core")
            ])


jar_library(name = 'cascading-thrift',
            dependencies = [
              jar(org = 'backtype', name = 'cascading-thrift', rev = '0.2.5')
                .exclude(org = 'org.apache.thrift')
            ])

jar_library(name = 'cascading2-test',
    dependencies = [ jar(org = 'cascading', name = 'cascading-test', rev = '2.0.0-wip-291') ]
  )

jar_library(name = 'cascading.jdbc',
            dependencies = [ jar(org = 'r0man', name = 'cascading.jdbc', rev = '1.2') ]
           )

jar_library(name = 'checkstyle',
            dependencies = [
              jar(org = 'com.puppycrawl.tools', name = 'checkstyle', rev = '5.5')
                .exclude(org = 'com.google.collections', name = 'google-collections')
                .with_sources(),
              # Needs this as the replacement for the excluded google-collections.
              pants(':guava'),
            ]
           )

jar_library(name = 'cloud9',
            dependencies = [
              jar(org = 'edu.umd', name = 'cloud9', rev = '1.2.2').with_sources()
                .exclude(org = 'fastutil', name = 'fastutil')
            ]
           )

jar_library(name = 'commons-beanutils',
            dependencies = [
              jar(org = 'commons-beanutils', name = 'commons-beanutils', rev = '1.8.3')
                .with_sources()
            ]
           )

jar_library(name = 'commons-codec',
            dependencies = [
              jar(org = 'commons-codec', name = 'commons-codec', rev = '1.4').with_sources()
            ]
           )

jar_library(name = 'commons-io',
            dependencies = [
              jar(org = 'commons-io', name = 'commons-io', rev = '2.1').with_sources()
            ]
           )

jar_library(name = 'commons-lang',
            dependencies = [
              jar(org = 'commons-lang', name = 'commons-lang', rev = '2.4').with_sources()
            ]
           )

jar_library(name = 'commons-logging',
            dependencies = [
              jar(org = 'commons-logging', name = 'commons-logging', rev = '1.1.1'),
            ]
           )

# common rev for all com.twitter%finagle* artifacts
FINAGLE_REV = '3.0.0'

jar_library(name = 'finagle-b3',
            dependencies = [ jar(org = 'com.twitter', name = 'finagle-b3', rev = FINAGLE_REV),
                           ])
jar_library(name = 'finagle-core',
            dependencies = [ jar(org = 'com.twitter', name = 'finagle-core', rev = FINAGLE_REV),
                             pants(':util-core'),
                           ])

jar_library(name = 'finagle-http',
            dependencies = [ jar(org = 'com.twitter', name = 'finagle-http', rev = FINAGLE_REV) ])

jar_library(name = 'finagle-thrift',
            dependencies = [ jar(org = 'com.twitter', name = 'finagle-thrift', rev = FINAGLE_REV) ])

jar_library(name = 'finagle-serversets',
            dependencies = [
              jar(org = 'com.twitter', name = 'finagle-serversets', rev = FINAGLE_REV)
                .exclude(org='com.twitter.common')
                .exclude(org='com.twitter.common.zookeeper')
            ])

jar_library(name = 'gson',
            dependencies = [ jar(org = 'com.google.code.gson', name = 'gson', rev = '2.1') ])

jar_library(name = 'guava',
            dependencies = [
              jar(
                org = 'com.google.guava', name = 'guava', rev = '12.0',
                apidocs = 'http://docs.guava-libraries.googlecode.com/git-history/v12.0/javadoc/'
              ).with_sources(),

              # This dependency should not need to be explicit but its currently required to work
              # around a doc generation bug (RTS-524).
              jar(org = 'javax.inject', name = 'javax.inject', rev = '1').with_sources(),
            ]
           )

jar_library(name = 'guice',
            dependencies = [
              jar(
                org = 'com.google.inject', name = 'guice', rev = '3.0',
                apidocs = 'http://google-guice.googlecode.com/svn/tags/3.0/javadoc/'
              ).with_sources()
            ]
           )

jar_library(name = 'guice-multibindings',
            dependencies = [
              jar(org = 'com.google.inject.extensions', name = 'guice-multibindings', rev = '3.0')
                .with_sources()
            ]
           )

jar_library(name = 'guice-servlet',
            dependencies = [
              jar(org = 'com.google.inject.extensions', name = 'guice-servlet', rev = '3.0')
                .with_sources()
            ]
           )

jar_library(name = 'hsqldb',
            dependencies = [ jar(org = 'hsqldb', name = 'hsqldb', rev = '1.8.0.10') ])

jar_library(name = 'jetty',
            dependencies = [
              jar(org = 'org.mortbay.jetty', name = 'jetty', rev = '6.1.25').with_sources()
            ]
           )

jar_library(name = 'joda-time',
            dependencies = [ jar(org = 'joda-time', name = 'joda-time', rev = '1.6') ])

jar_library(name = 'json-simple',
            dependencies = [
              jar(org = 'com.googlecode.json-simple', name = 'json-simple', rev = '1.1')])

jar_library(name = 'jsr305',
            dependencies = [
              jar(org = 'com.google.code.findbugs', name = 'jsr305', rev = '1.3.9')
            ]
           )

jar_library(name = 'kryo-serializers',
            dependencies = [
              jar(org = 'de.javakaffee', name = 'kryo-serializers', rev = '0.9')
            ]
           )

jar_library(name = 'lucene-analyzers',
            dependencies = [
              jar(org = 'org.apache.lucene', name = 'lucene-analyzers', rev = '3.0.2',
                  apidocs = 'http://lucene.apache.org/java/3_0_2/api/all/').with_sources()
             ]
            )

jar_library(name = 'lucene-smartcn',
            dependencies = [
              jar(org = 'org.apache.lucene', name = 'lucene-smartcn', rev = '3.0.2',
                  apidocs = 'http://lucene.apache.org/java/3_0_2/api/all/').with_sources()
             ]
            )

jar_library(name = 'lucene-core',
            dependencies = [
              jar(org = 'org.apache.lucene', name = 'lucene-core', rev = '3.0.2',
                  apidocs = 'http://lucene.apache.org/java/3_0_2/api/all/').with_sources()
            ]
           )

jar_library(name = 'lucene-snowball',
            dependencies = [
              jar(org = 'org.apache.lucene', name = 'lucene-snowball', rev = '3.0.2',
                  apidocs = 'http://lucene.apache.org/java/3_0_2/api/all/').with_sources()
            ]
           )

jar_library(name = 'meat-locker',
  dependencies = [ jar(org='com.twitter', name='meat-locker', rev='0.1.6') ]
  )

jar_library(name = 'ostrich',
            dependencies = [ jar(org = 'com.twitter', name = 'ostrich', rev = '2.3.6') ])

jar_library(name = 'protobuf-2.3.0',
            dependencies = [
              jar(org = 'com.google.protobuf', name = 'protobuf-java', rev = '2.3.0')
            ]
           )

jar_library(name = 'protobuf-java',
            dependencies = [ pants(':protobuf-2.3.0') ])

jar_library(name = 'scala-compiler',
            dependencies = [
              jar(org='org.scala-lang', name='scala-compiler', rev='2.8.1').with_sources()
            ]
           )

jar_library(name = 'scala-library',
            dependencies = [
              jar(org='org.scala-lang', name='scala-library', rev='2.8.1').with_sources()
            ]
           )

jar_library(name = 'servlet-api',
            dependencies = [
              jar(org = 'javax.servlet', name = 'servlet-api', rev = '2.5').with_sources()
            ]
           )

jar_library(name = 'slf4j-api',
            dependencies = [
              jar(org = 'org.slf4j', name = 'slf4j-api', rev = '1.6.1').with_sources()
            ]
           )

jar_library(name = 'slf4j-jdk14',
            dependencies = [
              jar(org = 'org.slf4j', name = 'slf4j-jdk14', rev = '1.6.1').with_sources()
            ]
           )

jar_library(name = 'spy-memcached',
            dependencies = [
              jar(org = 'spy', name = 'spymemcached', rev = '2.7.1').with_sources()
            ]
           )

jar_library(name = 'stringtemplate',
            dependencies = [
              jar(org = 'org.antlr', name = 'stringtemplate', rev = '3.2.1').with_sources()
            ]
           )

jar_library(name = 'libthrift-0.5.0',
            dependencies = [
              jar(org = 'org.apache.thrift', name = 'libthrift', rev = '0.5.0'),
            ]
           )

jar_library(name = 'thrift-0.5',
            dependencies = [
              pants(':libthrift-0.5.0'),
              pants(':slf4j-api'),
              pants(':slf4j-jdk14'), # need a concrete slf4j backend at runtime
            ]
           )

jar_library(name = 'thrift-0.5.0-finagle',
            dependencies = [
              pants(':libthrift-0.5.0'),
              pants(':commons-lang'),
              pants(':slf4j-api'),

              # finagle thrift extra deps
              pants(':finagle-core'),
              pants(':finagle-thrift'),
              pants(':util-core'),
            ]
           )

jar_library(name = 'thrift-0.7.0',
            dependencies = [
	      jar(org = 'org.apache.thrift', name = 'libthrift', rev = '0.7.0'),
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
            dependencies = [ jar(org = 'com.twitter', name = 'twitter-text', rev = '1.4.10') ])

# common rev for all com.twitter%util* artifacts
UTIL_REV = '3.0.0'
jar_library(name = 'util',
            dependencies = [
              jar(org = 'com.twitter', name = 'util', rev = UTIL_REV)
                  .exclude(org = 'com.twitter.common.zookeeper')
            ])

jar_library(name = 'util-core',
            dependencies = [ jar(org = 'com.twitter', name = 'util-core', rev = UTIL_REV) ])

jar_library(name = 'vertica',
            dependencies = [ jar(org = 'com.vertica', name = 'vertica', rev = '4.1.2') ])

jar_library(name = 'visualization-datasource',
            dependencies = [
              jar(org = 'com.google.visualization', name = 'visualization-datasource',
                  rev = '1.0.2')
                  .exclude(org = 'com.google.collections', name = 'google-collections')
            ])

jar_library(name = 'zookeeper',
            dependencies = [
              jar(org = 'org.apache.zookeeper', name = 'zookeeper', rev = '3.3.4').with_sources()
                .exclude(org = 'jline', name = 'jline')
                .exclude(org = 'javax.jms', name = 'jms')
                .exclude(org = 'com.sun.jdmk', name = 'jmxtools')
                .exclude(org = 'com.sun.jmx', name = 'jmxri')
            ]
           )

jar_library(name = 'twadoop',
            dependencies = [
              jar(org ='com.twitter.twadoop', name='twadoop', rev = '1.0')
            ]
           )

jar_library(name = 'commons-net',
           dependencies = [
             jar(org = 'commons-net', name = 'commons-net', rev = '3.1')
           ]
          )

###############
# Test support
#

jar_library(name = 'easymock',
            dependencies = [ jar(org = 'org.easymock', name = 'easymock', rev = '3.0') ])

jar_library(name = 'mockito',
            dependencies = [ jar(org = 'org.mockito', name = 'mockito-all', rev = '1.8.4') ])

jar_library(name = 'hamcrest-core',
            dependencies = [ jar(org = 'org.hamcrest', name = 'hamcrest-core', rev = '1.2') ])

jar_library(name = 'junit',
            dependencies = [
              jar(org = 'junit', name = 'junit-dep', rev = '4.10').with_sources(),
              pants(':hamcrest-core')
            ]
           )

jar_library(name = 'scalap',
            dependencies = [
              jar(org = 'org.scala-lang', name = 'scalap', rev = '2.8.1')
            ])

jar_library(name = 'specs_2.8',
            dependencies = [
              jar(org = 'org.scala-tools.testing', name = 'specs_2.8.0', rev = '1.6.5')
                .with_sources()
            ]
           )
jar_library(name = 'specs_2.9',
            dependencies = [
              jar(org = 'org.scala-tools.testing', name = 'specs_2.9.1', rev = '1.6.9')
            ]
           )
jar_library(name = 'specs', dependencies = [ pants(':specs_2.8') ])

jar_library(name = 'tl4j',
            dependencies = [
              jar(org = 'com.google.testing', name = 'test-libraries-for-java', rev = '1.1.1')
                # tl4j by default pulls in junit-4.6, which conflicts with 4.10 needed by our
                # testing code (e.g. ConcurrentJunitRunner). So exclude it here so that the junit
                # target defined above is used instead. tl4j seems to be happy with junit-4.10.
                .exclude(org = 'junit', name='junit')
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
