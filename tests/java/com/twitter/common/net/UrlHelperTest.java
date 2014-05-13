// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.net;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.net.URISyntaxException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author John Sirois
 */
public class UrlHelperTest {
  @Test
  public void testGetDomain() {
    assertEquals("www.twitter.com", UrlHelper.getDomain("www.twitter.com"));
    assertEquals("www.twitter.com", UrlHelper.getDomain("www.twitter.com/a/b/c?foo=bar&bar=baz"));

    assertEquals("www.bign.com",
        UrlHelper.getDomain("www.bign.com/davidvandiverhttp://cli.gs/da0e0"));
    assertEquals("www.thesun.co.uk",
        UrlHelper.getDomain("www.thesun.co.uk/sol/homepa-http://dragtotop.com/thesun.co.uk"));
    assertEquals("www.formspring.me",
        UrlHelper.getDomain("www.formspring.me/chuuworangerrhttp://bit.ly/7pydt3"));
    assertEquals("www.bign.com",
        UrlHelper.getDomain("www.bign.com/davidvandiverhttp://cli.gs/da0e0"));
    assertEquals("www.roundplace.com",
        UrlHelper.getDomain("www.roundplace.com/en/watch/108/3/"
                            + "baltimore-rave-http://dragtotop.com/patriots_vs_ravens"));
    assertEquals("www.bign.com",
        UrlHelper.getDomain("www.bign.com/davidvandiverhttp://cli.gs/da0e0"));
    assertEquals(null, UrlHelper.getDomain("http://?idonthaveadomain=true"));
    assertEquals(null, UrlHelper.getDomain(":::<<<<<::IAMNOTAVALIDURI“"));
  }

  @Test
  public void testGetDomainChecked() throws Exception {
    assertEquals("www.twitter.com", UrlHelper.getDomainChecked("http://www.twitter.com"));
    assertEquals("www.twitter.com", UrlHelper.getDomainChecked("https://www.twitter.com/?a=b"));
    assertEquals(null, UrlHelper.getDomainChecked("http://?idonthaveadomain=true"));
    try {
      UrlHelper.getDomainChecked(":::<<<<<::IAMNOTAVALIDURI“");
      fail();
    } catch (URISyntaxException e) {
      // Expected
    }
  }

  @Test
  public void testGetPath() {
    assertEquals("", UrlHelper.getPath("www.twitter.com"));
    assertEquals("/", UrlHelper.getPath("www.twitter.com/"));
    assertEquals("/foo", UrlHelper.getPath("http://www.twitter.com/foo"));
    assertEquals("/bar", UrlHelper.getPath("https://www.twitter.com/bar"));
    assertEquals("/a/b/c", UrlHelper.getPath("www.twitter.com/a/b/c"));

    assertEquals("/davidvandiverhttp://cli.gs/da0e0",
        UrlHelper.getPath("www.bign.com/davidvandiverhttp://cli.gs/da0e0"));
    assertEquals("/sol/homepa-http://dragtotop.com/thesun.co.uk",
        UrlHelper.getPath("www.thesun.co.uk/sol/homepa-http://dragtotop.com/thesun.co.uk"));
    assertEquals("/chuuworangerrhttp://bit.ly/7pydt3",
        UrlHelper.getPath("www.formspring.me/chuuworangerrhttp://bit.ly/7pydt3"));
    assertEquals("/davidvandiverhttp://cli.gs/da0e0",
        UrlHelper.getPath("www.bign.com/davidvandiverhttp://cli.gs/da0e0"));
    assertEquals("/en/watch/10855/3/baltimore-rave-http://dragtotop.com/patriots_vs_ravens",
        UrlHelper.getPath("www.roundplace.com/en/watch/10855/3/"
                            + "baltimore-rave-http://dragtotop.com/patriots_vs_ravens"));
    assertEquals("/davidvandiverhttp://cli.gs/da0e0",
        UrlHelper.getPath("www.bign.com/davidvandiverhttp://cli.gs/da0e0"));
  }

  @Test
  public void testAddProtocol() {
    assertEquals("http://www.twitter.com", UrlHelper.addProtocol("www.twitter.com"));
    assertEquals("http://www.twitter.com", UrlHelper.addProtocol("http://www.twitter.com"));
    assertEquals("https://www.twitter.com", UrlHelper.addProtocol("https://www.twitter.com"));

    assertEquals("http://www.twitter.com/this/is/a/http://stange/but/valid/path",
        UrlHelper.addProtocol("http://www.twitter.com/this/is/a/http://stange/but/valid/path"));
    assertEquals("http://www.twitter.com/this/is/a/http://stange/but/valid/path",
        UrlHelper.addProtocol("www.twitter.com/this/is/a/http://stange/but/valid/path"));
  }

  @Test
  public void testStripUrlParameters() {
    assertEquals("www.twitter.com", UrlHelper.stripUrlParameters("www.twitter.com"));
    assertEquals("www.twitter.com", UrlHelper.stripUrlParameters("www.twitter.com?foo-bar"));
    assertEquals("www.twitter.com/a/b/",
        UrlHelper.stripUrlParameters("www.twitter.com/a/b/?foo-bar"));

    assertEquals("http://www.twitter.com", UrlHelper.stripUrlParameters("http://www.twitter.com"));
    assertEquals("http://www.twitter.com",
        UrlHelper.stripUrlParameters("http://www.twitter.com?foo=bar"));
    assertEquals("http://www.twitter.com/a",
        UrlHelper.stripUrlParameters("http://www.twitter.com/a?foo=bar"));
  }

  @Test
  public void testGetDomainLevels() {
    assertEquals(ImmutableList.of("www.fred", "fred"), UrlHelper.getDomainLevels("fred"));
    assertEquals(ImmutableList.of("www.twitter.com", "twitter.com", "com"),
        UrlHelper.getDomainLevels("www.twitter.com"));
    assertEquals(ImmutableList.of("www.twitter.co.uk", "twitter.co.uk", "co.uk", "uk"),
        UrlHelper.getDomainLevels("twitter.co.uk"));
  }
}
