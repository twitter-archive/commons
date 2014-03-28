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

# This BUILD file defines common external library dependencies used across many modules. Modules
# can use these jar_library deps by specifying pants('3rdparty:[name]') in their dependencies list.

###################
# Documentation
#

page(name='readme', source='README.md')

###################
# Production libs
#

jar_library(name = 'antlr2',
            dependencies = [ jar(org = 'antlr', name = 'antlr', rev = '2.7.7') ])

jar_library(name = 'antlr3',
            dependencies = [ jar(org = 'antlr', name = 'antlr', rev = '3.1.3') ])

jar_library(name = 'antlr-3.4',
            dependencies = [
                jar(org = 'org.antlr', name = 'antlr', rev = '3.4'),
                jar(org = 'org.antlr', name = 'antlr-runtime', rev = '3.4')
             ]
           )

jar_library(name = 'antlr4',
            dependencies = [
                jar(org = 'org.antlr', name = 'antlr4', rev = '4.1').with_sources(),
                jar(org = 'org.antlr', name = 'antlr4-runtime', rev = '4.1').with_sources()
             ]
           )

jar_library(name = 'aopalliance',
            dependencies = [
              jar(org = 'aopalliance', name = 'aopalliance', rev = '1.0').with_sources()
            ]
          )

jar_library(name = 'args4j',
            dependencies = [ jar(org = 'args4j', name = 'args4j', rev = '2.0.16').with_sources() ])

jar_library(name = 'asm-commons',
            dependencies = [
              jar(org = 'org.ow2.asm', name = 'asm-commons', rev = '4.0'),
            ]
           )
jar_library(name = 'automaton',
            dependencies = [
              jar(org = 'dk.brics.automaton', name = 'automaton', rev = '1.11-8').with_sources(),
            ]
          )

jar_library(name = 'aws-java-sdk',
            dependencies = [
              jar(org = 'com.amazonaws', name = 'aws-java-sdk', rev = '1.3.27'),
            ]
           )

jar_library(name = 'cascading-core',
            dependencies = [ jar(org = 'cascading', name = 'cascading-core', rev = '1.2.3',
                             force = True )]
           ).with_description('''
This is cascading 1, only project depends upon this rule will likely to have confliction with
cascading 2 which is used by elephant bird, so depends upon this explicitly to force the right
version.
Notice: force is NOT transitive, so any indirectly depended package will not have forced version
           ''')

CASCADING2_REV = '2.1.6.t3'

jar_library(name = 'cascading2-core',
            dependencies = [
              jar(org = 'cascading', name = 'cascading-core', rev = CASCADING2_REV, force=True)
                .exclude(org = 'org.apache.hadoop', name="hadoop-core")
                .exclude(org = 'org.codehaus.janino', name = 'janino')
            ])

jar_library(name = 'dspace-geoip',
            dependencies = [
              jar(org = 'org.dspace.dependencies', name = 'dspace-geoip', rev = '1.2.3')
            ])

jar_library(name = 'maple',
            dependencies = [
              jar(org = 'com.twitter', name = 'maple', rev = '0.3.1').with_sources()
            ])

jar_library(name = 'cascading2-local',
            dependencies = [
              jar(org = 'cascading', name = 'cascading-local', rev = CASCADING2_REV, force=True)
                .exclude(org = 'org.apache.hadoop', name="hadoop-core")
                .exclude(org = 'org.codehaus.janino', name = 'janino')
            ])

jar_library(name = 'cascading2-hadoop',
            dependencies = [
              jar(org = 'cascading', name = 'cascading-hadoop', rev = CASCADING2_REV, force=True)
                .exclude(org = 'org.apache.hadoop', name="hadoop-core")
                .exclude(org = 'org.codehaus.janino', name = 'janino')
            ])


jar_library(name = 'cascading-thrift',
            dependencies = [
              jar(org = 'backtype', name = 'cascading-thrift', rev = '0.2.5')
                .exclude(org = 'org.apache.thrift')
            ])

jar_library(name = 'cascading2-test',
    dependencies = [ jar(org = 'cascading', name = 'cascading-test', rev = CASCADING2_REV, force=True) ]
  )

jar_library(name = 'cascading.jdbc',
            dependencies = [ jar(org = 'r0man', name = 'cascading.jdbc', rev = '1.2') ]
           )

jar_library(name = 'cglib',
            dependencies = [
              jar(org = 'cglib', name = 'cglib', rev = '2.2.2').with_sources()
            ]
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
              jar(org = 'commons-codec', name = 'commons-codec', rev = '1.6').with_sources()
            ]
           )

jar_library(name = 'commons-io',
            dependencies = [
              jar(org = 'commons-io', name = 'commons-io', rev = '2.1').with_sources()
            ]
           )

jar_library(name = 'commons-lang',
            dependencies = [
              jar(org = 'commons-lang', name = 'commons-lang', rev = '2.6').with_sources()
            ]
           )

jar_library(name = 'commons-logging',
            dependencies = [
              jar(org = 'commons-logging', name = 'commons-logging', rev = '1.1.1'),
            ]
           )

jar_library(name = 'commons-dbcp',
            dependencies = [
              jar(org = 'commons-dbcp', name = 'commons-dbcp', rev = '1.2.2'),
            ]
           )

jar_library(name = 'commons-validator',
            dependencies = [
              jar(org = 'commons-validator', name = 'commons-validator', rev = '1.4.0'),
            ]
           )

jar_library(name = 'dnsjava',
            dependencies = [
              jar(org = 'dnsjava', name = 'dnsjava', rev = '2.1.1').with_sources(),
            ])

# Common revs for finagle/util/twitter-server artifacts: these need to be bumped in sync
# Please keep the science netty version in sync with the version used by finagle.
NETTY_REV = '3.8.0.Final'
FINAGLE_REV = '6.12.2'
UTIL_REV = '6.12.1'
SERVER_REV = '1.5.2'

jar_library(name = 'netty',
            dependencies = [ jar(org = 'io.netty', name = 'netty', rev = NETTY_REV) ])

jar_library(name = 'finagle-exception',
            dependencies = [ jar(org = 'com.twitter', name = 'finagle-exception', rev = FINAGLE_REV) ])

jar_library(name = 'finagle-zipkin',
            dependencies = [ jar(org = 'com.twitter', name = 'finagle-zipkin', rev = FINAGLE_REV) ])

jar_library(name = 'finagle-core',
            dependencies = [
		          jar(org = 'com.twitter', name = 'finagle-core', rev = FINAGLE_REV)
		            .with_sources(),
              pants(':util-core'),
            ])

jar_library(name = 'finagle-http',
            dependencies = [ jar(org = 'com.twitter', name = 'finagle-http', rev = FINAGLE_REV) ])

jar_library(name = 'finagle-thrift',
            dependencies = [ jar(org = 'com.twitter', name = 'finagle-thrift', rev = FINAGLE_REV)
                                .withSources() ])

jar_library(name = 'finagle-serversets',
            dependencies = [
             jar(org = 'com.twitter', name = 'finagle-serversets', rev = FINAGLE_REV)
                .exclude(org='com.twitter.common')
                .exclude(org='com.twitter.common.zookeeper')
            ])

jar_library(name = 'finagle-memcached',
            dependencies = [
              jar(org = 'com.twitter', name = 'finagle-memcached', rev = FINAGLE_REV)
                .exclude(org='com.twitter.common.zookeeper')
            ])

jar_library(name = 'finagle-kestrel',
            dependencies = [
              jar(org = 'com.twitter', name = 'finagle-kestrel', rev = FINAGLE_REV)
                .exclude(org='com.twitter.common.zookeeper')
            ])

jar_library(name = 'finagle-commons-stats',
            dependencies = [
              jar(org = 'com.twitter', name = 'finagle-commons-stats', rev = FINAGLE_REV)
                .exclude(org='com.twitter.common')
            ])

jar_library(name = 'finagle-thriftmux',
            dependencies = [
              jar(org = 'com.twitter', name = 'finagle-thriftmux', rev = FINAGLE_REV).with_sources()
            ])

jar_library(name = 'finagle-mysql',
            dependencies = [
              jar(org = 'com.twitter', name = 'finagle-mysql', rev = FINAGLE_REV).with_sources()
            ])

jar_library(name = 'finagle-ostrich4',
            dependencies = [
              jar(org = 'com.twitter', name = 'finagle-ostrich4', rev = FINAGLE_REV).with_sources()
            ])

jar_library(name = 'finagle-redis',
            dependencies = [
              jar(org = 'com.twitter', name = 'finagle-redis', rev = FINAGLE_REV).with_sources()
            ])

jar_library(name = 'finagle-stream',
            dependencies = [
              jar(org = 'com.twitter', name = 'finagle-stream', rev = FINAGLE_REV).with_sources()
            ])

jar_library(name = 'finagle-stats',
            dependencies = [
              jar(org = 'com.twitter', name = 'finagle-stats', rev = FINAGLE_REV).with_sources()
            ])

jar_library(name = 'gdata-core',
            dependencies = [ jar(org = 'com.google.gdata', name = 'core', rev = '1.47.1') ])

jar_library(name = 'gson',
            dependencies = [
              jar(org = 'com.google.code.gson', name = 'gson', rev = '2.2.2').with_sources()
            ])

# common rev for all com.google.guava%guava* artifacts
GUAVA_REV = '16.0'

jar_library(name = 'guava',
            dependencies = [
              jar(
                org = 'com.google.guava', name = 'guava', rev = GUAVA_REV,
                apidocs = 'http://docs.guava-libraries.googlecode.com/git-history/v%s/javadoc/' % GUAVA_REV
              ).with_sources(),

              # Defined in provided scope so we provide here
              pants(':jsr305'),
            ]
           )

jar_library(name = 'guava-testlib',
            dependencies = [
              jar(
                org = 'com.google.guava', name = 'guava-testlib', rev = GUAVA_REV,
                apidocs = 'http://docs.guava-libraries.googlecode.com/git-history/v14.0.1/javadoc/'
              ).with_sources()
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

jar_library(name = 'guice-throwingproviders',
            dependencies = [
              jar(org = 'com.google.inject.extensions', name = 'guice-throwingproviders', rev = '3.0')
                .with_sources()
            ]
           )

jar_library(name = 'hsqldb',
            dependencies = [ jar(org = 'hsqldb', name = 'hsqldb', rev = '1.8.0.10') ])

jar_library(name = 'hsqldb-2.2',
            dependencies = [ jar(org = 'org.hsqldb', name = 'hsqldb', rev = '2.2.4') ])

jar_library(name = 'jetty',
            dependencies = [
              jar(org = 'org.mortbay.jetty', name = 'jetty', rev = '6.1.25').with_sources()
            ]
           )

jar_library(name = 'joda-money',
            dependencies = [
              jar(org = 'org.joda', name = 'joda-money', rev = '0.9').with_sources()
            ]
           )

jar_library(name = 'joda-time',
            dependencies = [
              jar(org = 'joda-time', name = 'joda-time', rev = '2.3'),
              jar(org = 'org.joda', name = 'joda-convert', rev = '1.5')
            ]
           )

jar_library(name = 'jsch',
            dependencies = [
              jar(org = 'com.jcraft', name = 'jsch', rev = '0.1.48')])

jar_library(name = 'json-simple',
            dependencies = [
              jar(org = 'com.googlecode.json-simple', name = 'json-simple', rev = '1.1')])

# Extract from the jsr166e proposal
# See http://github.com/twitter/jsr166e
jar_library(name = 'jsr166e',
            dependencies = [
              jar(org = 'com.twitter', name = 'jsr166e', rev = '1.0.0').with_sources()
            ]
           )

jar_library(name = 'jsr305',
            dependencies = [
              jar(org = 'com.google.code.findbugs', name = 'jsr305', rev = '1.3.9')
            ]
           )

jar_library(name = 'jvyaml',
            dependencies = [ jar(org = 'org.jvyaml', name = 'jvyaml', rev = '0.2.1') ])

jar_library(name = 'mimepull',
            dependencies = [ jar(org='org.jvnet.mimepull', name='mimepull', rev='1.8').with_sources() ])

jar_library(name = 'mustache',
            dependencies = [
              jar(org = 'com.github.spullara.mustache.java',
                  name = 'compiler',
                  rev = '0.8.4').with_sources()
              ]
            )

jar_library(name = 'ostrich',
            dependencies = [
              jar(org = 'com.twitter', name = 'ostrich', rev = '9.1.2').with_sources()
            ])

jar_library(name='passbird-thrift-only',
            dependencies=[
              thrift_jar(org='com.twitter', name='passbird-thrift-only', rev='5.0.3')
            ])

jar_library(name='promptbird-thrift-only',
            dependencies=[
              thrift_jar(org='com.twitter', name='promptbird-thrift-only', rev='3.17.0')
            ])

jar_library(name = 'protobuf-2.4.1',
            dependencies = [
              jar(org = 'com.google.protobuf', name = 'protobuf-java', rev = '2.4.1').with_sources()
            ]
           )

jar_library(name = 'protobuf-java',
            dependencies = [ pants(':protobuf-2.4.1') ])

jar_library(name = 'scala-compiler',
            dependencies = [
              jar(org='org.scala-lang', name='scala-compiler', rev='2.9.3').with_sources()
            ]
           )

jar_library(name = 'scala-library',
            dependencies = [
              jar(org='org.scala-lang', name='scala-library', rev='2.9.3').with_sources()
            ]
           )

jar_library(name = 'scalacheck',
            dependencies = [
              jar(org='org.scalacheck', name='scalacheck_2.9.3', rev='1.10.0').with_sources()
            ]
           )

# NOTE(rschonberger): Note that this library needs to be updated every scal compiler update.
jar_library(name = 'scalatest',
            dependencies = [
              jar(org='org.scalatest', name='scalatest_2.9.2', rev='1.9.1').with_sources()
            ]
           )

jar_library(name = 'scrooge-runtime',
            dependencies = [
              # used by scrooge-generator described in build-support/profiles/scrooge-gen.ivy.xml
              jar(org='com.twitter', name='scrooge-runtime', rev='3.1.2').with_sources()
            ]
          )

jar_library(name = 'jerkson',
            dependencies = [
              jar(org='com.codahale', name='jerkson_2.9.2', rev='0.5.0').with_sources()
            ]
           )

jar_library(name = 'servlet-api',
            dependencies = [
              jar(org = 'javax.servlet', name = 'servlet-api', rev = '2.5').with_sources()
            ]
           )

SLF4J_VERSION = '1.6.1'

jar_library(name = 'javacsv',
            dependencies = [
              jar(org = 'net.sourceforge.javacsv', name = 'javacsv', rev = '2.0').with_sources()
            ]
           )

jar_library(name = 'slf4j-api',
            dependencies = [
              jar(org = 'org.slf4j', name = 'slf4j-api', rev = SLF4J_VERSION).with_sources()
            ]
           )

jar_library(name = 'slf4j-jdk14',
            dependencies = [
              jar(org = 'org.slf4j', name = 'slf4j-jdk14', rev = SLF4J_VERSION).with_sources()
            ]
           )

jar_library(name = 'snakeyaml',
            dependencies = [
              jar(org = 'org.yaml', name = 'snakeyaml', rev = '1.12').with_sources()
            ]
           )

jar_library(name = 'snappy',
            dependencies = [
              jar(org = 'org.iq80.snappy', name = 'snappy', rev = '0.1').with_sources()
            ]
           )

jar_library(name = 'spy-memcached',
            dependencies = [
              jar(org = 'spy', name = 'spymemcached', rev = '2.8.1').with_sources()
            ]
           )

jar_library(name = 'stringtemplate',
            dependencies = [
              jar(org = 'org.antlr', name = 'stringtemplate', rev = '3.2.1').with_sources()
            ]
           )

jar_library(name = 'libthrift-0.5.0',
            dependencies = [
              jar(org = 'org.apache.thrift', name = 'libthrift', rev = '0.5.0-1').with_sources(),
            ]
           )

jar_library(name = 'thrift-0.5.0',
            dependencies = [
              pants(':commons-lang'),
              pants(':libthrift-0.5.0'),
              pants(':slf4j-api'),

              # TODO(John Sirois): Find a sane way to have thrift dependees who do not themselves
              # use slf4j get a concrete binding.
              pants(':slf4j-jdk14'),
            ]
           )

jar_library(name = 'thrift-0.5.0-finagle',
            dependencies = [
              pants(':thrift-0.5.0'),

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
            dependencies = [ pants(':thrift-0.5.0') ])

jar_library(name = 'twitter4j',
            dependencies = [
              jar(org = 'org.twitter4j', name = 'twitter4j-core', rev = '2.1.6-patched-1')
                .intransitive()
            ]
           ).with_description('Deprecated & broken - use hbc https://github.com/twitter/hbc')

# hbc-internal is stuck at 1.3.3 and the 1.3.3 series uses twitter4j 2.2.6.  As soon as hbc-internal
# comes up to hbc-* 1.4.0 external release parity, we should bump to twitter4j 3.x.
def twitter4j_lib(name):
  dep = jar(org='org.twitter4j', name=name, rev='2.2.6').with_sources()
  return jar_library(name=name, dependencies=[dep])

twitter4j_lib('twitter4j-core')
twitter4j_lib('twitter4j-stream')

jar_library(name = 'twitter-server',
            dependencies = [
              jar(org = 'com.twitter', name = 'twitter-server', rev = SERVER_REV)
                .with_sources()
                .exclude(org = 'com.twitter.common.zookeeper')
                .exclude(org = 'com.twitter.common_internal', name = 'zookeeper-utils'),
              pants('src/java/com/twitter/common/zookeeper:candidate'),
              pants('src/java/com/twitter/common/zookeeper:client'),
              pants('src/java/com/twitter/common/zookeeper:group'),
              pants('src/java/com/twitter/common/zookeeper:server-set'),
              pants('src/java/com/twitter/common/zookeeper:singleton-service'),
              pants('src/java/com/twitter/common_internal/zookeeper'),
            ])

jar_library(name = 'twitter-text',
            dependencies = [ jar(org = 'com.twitter', name = 'twitter-text', rev = '1.5.0') ])

jar_library(name = 'twitter-config-yaml',
            dependencies = [
              jar(org = 'com.twitter', name = 'twitter-config-yaml', rev = '1.2.0').with_sources()
            ])

jar_library(name = 'util',
            dependencies = [
              jar(org = 'com.twitter', name = 'util', rev = UTIL_REV)
                  .exclude(org = 'com.twitter.common.zookeeper')
            ])

jar_library(name = 'util-app',
            dependencies = [
                jar(org = 'com.twitter', name = 'util-app', rev = UTIL_REV).with_sources() ])

jar_library(name = 'util-core',
            dependencies = [
                jar(org = 'com.twitter', name = 'util-core', rev = UTIL_REV).with_sources() ])

jar_library(name = 'util-eval',
            dependencies = [
                jar(org = 'com.twitter', name = 'util-eval', rev = UTIL_REV).with_sources() ])

jar_library(name = 'util-jvm',
            dependencies = [ jar(org = 'com.twitter', name = 'util-jvm', rev = UTIL_REV) ])

jar_library(name = 'util-zk-common',
            dependencies = [
              jar(org = 'com.twitter', name = 'util-zk-common', rev = UTIL_REV)
                  .exclude(org='com.twitter.common.zookeeper')
            ])

jar_library(name = 'util-thrift',
            dependencies = [
              jar(org = 'com.twitter', name = 'util-thrift', rev = UTIL_REV)
            ])

jar_library(name = 'util-logging',
            dependencies = [
              jar(org = 'com.twitter', name = 'util-logging', rev = UTIL_REV)
            ])

jar_library(name = 'vertica',
            dependencies = [ jar(org = 'com.vertica', name = 'vertica', rev = '4.1.2') ])

jar_library(name = "xerces",
            dependencies = [
              jar(org = 'xerces', name = 'xercesImpl', rev = '2.9.1')
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
            dependencies = [
              jar(org = 'org.easymock', name = 'easymock', rev = '3.2').with_sources(),
              # Class extension is needed to support the old version of easymockclassextension that
              # is not dependend on by the easymock dependency
              jar(org = 'org.easymock', name = 'easymockclassextension', rev = '3.2').with_sources(),
            ]
           )

jar_library(name = 'powermock-junit4',
            dependencies = [
              jar(org = 'org.powermock', name = 'powermock-module-junit4', rev = '1.4.12').with_sources(),
            ]
           )

jar_library(name = 'powermock-easymock',
            dependencies = [
              jar(org = 'org.powermock', name = 'powermock-api-easymock', rev = '1.4.12').with_sources()
            ]
           )

jar_library(name = 'mockito',
            dependencies = [
              jar(org = 'org.mockito', name = 'mockito-all', rev = '1.9.0').with_sources()
            ]
           )

jar_library(name = 'hamcrest-core',
            dependencies = [ jar(org = 'org.hamcrest', name = 'hamcrest-core', rev = '1.2') ])

jar_library(name = 'junit',
            dependencies = [
              jar(org = 'junit', name = 'junit-dep', rev = '4.10').with_sources(),
              pants(':hamcrest-core')
            ]
           )

jar_library(name = 'jline',
            dependencies = [ jar(org = 'jline', name = 'jline', rev = '0.9.94') ])


jar_library(name = 'scalap',
            dependencies = [
              jar(org = 'org.scala-lang', name = 'scalap', rev = '2.9.3')
            ])

jar_library(name = 'specs_2.9',
            dependencies = [
              jar(org = 'org.scala-tools.testing', name = 'specs_2.9.3', rev = '1.6.9')
            ]
           )

jar_library(name = 'specs', dependencies = [ pants(':specs_2.9') ])

jar_library(name = 'tl4j',
            dependencies = [
              jar(org = 'com.google.testing', name = 'test-libraries-for-java', rev = '1.1.1')
                # tl4j by default pulls in junit-4.6, which conflicts with 4.10 needed by our
                # testing code (e.g. ConcurrentJunitRunner). So exclude it here so that the junit
                # target defined above is used instead. tl4j seems to be happy with junit-4.10.
                .exclude(org = 'junit', name='junit')
            ]
           )

###############
# Benchmark support
#
jar_library(name = 'caliper',
            dependencies = [
              # Warning: keep the library version aligned with
              # build-support/profiles/benchmark-caliper-0.5-ivy.xml
              jar(org = 'com.google.caliper', name = 'caliper', rev = '0.5-rc1').with_sources(),
            ]
           )
