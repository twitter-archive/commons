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

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Lists;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Adam Samet
 */
public class UrlTokenizerUtilTest {

  @Test
  public void testGetReversedDomainParts() {
    List<String> list1 = Lists.newArrayList();
    String url1 = "www.twitter.com";
    list1.add("com");
    assertEquals(list1, UrlTokenizerUtil.getReversedDomainParts(url1, 1));
    list1.add("twitter");
    assertEquals(list1, UrlTokenizerUtil.getReversedDomainParts(url1, 2));
    list1.add("www");
    assertEquals(list1, UrlTokenizerUtil.getReversedDomainParts(url1, 3));
    list1.add("");
    assertEquals(list1, UrlTokenizerUtil.getReversedDomainParts(url1, 4));
    list1.add("");
    assertEquals(list1, UrlTokenizerUtil.getReversedDomainParts(url1, 5));

    List<String> list2 = Lists.newArrayList();
    String url2 = "www.twitter.co.uk";
    list2.add("co.uk");
    assertEquals(list2, UrlTokenizerUtil.getReversedDomainParts(url2, 1));
    list2.add("twitter");
    assertEquals(list2, UrlTokenizerUtil.getReversedDomainParts(url2, 2));
    list2.add("www");
    assertEquals(list2, UrlTokenizerUtil.getReversedDomainParts(url2, 3));
    list2.add("");
    assertEquals(list2, UrlTokenizerUtil.getReversedDomainParts(url2, 4));
    list2.add("");
    assertEquals(list2, UrlTokenizerUtil.getReversedDomainParts(url2, 5));

    List<String> list3 = Lists.newArrayList();
    String url3= "www.twitter.co.ukNOT";
    list3.add("ukNOT");
    assertEquals(list3, UrlTokenizerUtil.getReversedDomainParts(url3, 1));
    list3.add("co");
    assertEquals(list3, UrlTokenizerUtil.getReversedDomainParts(url3, 2));
    list3.add("twitter");
    assertEquals(list3, UrlTokenizerUtil.getReversedDomainParts(url3, 3));
    list3.add("www");
    assertEquals(list3, UrlTokenizerUtil.getReversedDomainParts(url3, 4));
    list3.add("");
    assertEquals(list3, UrlTokenizerUtil.getReversedDomainParts(url3, 5));

    assertEquals(Arrays.asList("co.jp", "google"),
                 UrlTokenizerUtil.getReversedDomainParts("news.google.co.jp", 2));
    assertEquals(Arrays.asList("co.jp", "google"),
                 UrlTokenizerUtil.getReversedDomainParts("news.google.co.jp", 2));
    assertEquals(Arrays.asList("com", "google"),
                 UrlTokenizerUtil.getReversedDomainParts("news.google.com", 2));
    assertEquals(Arrays.asList("com", "google", "news"),
                 UrlTokenizerUtil.getReversedDomainParts("news.google.com", 3));
  }

  @Test
  public void testIsTLD() throws Exception {
    assertTrue(UrlTokenizerUtil.isTLD("com.cn", false));
    assertTrue(UrlTokenizerUtil.isTLD("com", false));
    assertTrue(UrlTokenizerUtil.isTLD("co.jp", false));
    assertTrue(UrlTokenizerUtil.isTLD("co.uk", false));
    assertTrue(UrlTokenizerUtil.isTLD("uk.co", true));
    assertTrue(UrlTokenizerUtil.isTLD("cn.com", true));
    assertFalse(UrlTokenizerUtil.isTLD("google.co.uk", false));
    assertFalse(UrlTokenizerUtil.isTLD("google.jp", false));
  }
}
