/*
 * Copyright 2000-2022 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.server.rest.model;

import java.util.Map;
import jetbrains.buildServer.log.Loggable;
import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.serverSide.MockParameter;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 25/04/2016
 */
public class PropertiesTest extends BaseServerTestCase {

  @Test
  public void testBasic() {
    check(CollectionsUtil.asMap("aaa", "xxx", "bbb", "yyy", "aAaa", "xXx"), "$long", 3,
          CollectionsUtil.asMap("aaa", "xxx", "aAaa", "xXx", "bbb", "yyy"));
  }

  @Test
  public void testLocators() {
    check(CollectionsUtil.asMap("a", "b", "aaa", "xxx", "bbb", "yyy", "aAaa", "xXx", "aAa", "xXx"), "$long,$locator(name:aaa)", 1,
          CollectionsUtil.asMap("aaa", "xxx"));

    check(CollectionsUtil.asMap("a", "b", "aaa", "xxx", "bbb", "yyy", "aAaa", "xXx"), "$long,$locator(value:xxx)", 1,
          CollectionsUtil.asMap("aaa", "xxx"));

    check(CollectionsUtil.asMap("a", "b", "aaaa", "xxx", "bbb", "xXx", "aAaaa", "xxx", "aa", "xxx.", "aaa", "xxx"),
          "$long,$locator(name:(value:a.*a,matchType:matches),value:xxx,matchType:equals)", 3,
          CollectionsUtil.asMap("aaa", "xxx", "aaaa", "xxx", "aAaaa", "xxx"));
  }

  @Test
  public void testSecure() {
    Property property = new Property(new MockParameter("aaa", "bbb", "password"), false, Fields.LONG, myFixture);
    assertEquals("aaa", property.name);
    assertNull(property.value);
    assertEquals("password", property.type.rawValue);
  }

  private void check(final Map<String, String> input,
                     @NotNull final String fields, @Nullable final Integer outputCount,
                     @Nullable final Map<String, String> output) {
    Properties result = new Properties(input, null, new Fields(fields), BaseFinderTest.getBeanContext(myFixture));

    assertEquals("Count does not match for " + describeProperties(result),outputCount == null ? null : Integer.valueOf(outputCount), result.count);
    if (output != null) {
      assertNotNull(result.properties);
      assertEquals(describeProperties(result), output.size(), result.properties.size());
      int i = 0;
      for (Map.Entry<String, String> stringStringEntry : output.entrySet()) {
        assertEquals("Name differs at position " + (i+1) + "\n" + describeProperties(result), stringStringEntry.getKey(), result.properties.get(i).name);
        assertEquals("Value differs at position " + (i+1) + "\n" + describeProperties(result), stringStringEntry.getValue(), result.properties.get(i).value);
        i++;
      }
    } else {
      assertNull(result.properties);
    }
  }

  @NotNull
  private String describeProperties(final Properties result) {
    return LogUtil.describe(CollectionsUtil.convertCollection(result.properties, new Converter<Loggable, Property>() {
      public Loggable createFrom(@NotNull final Property source) {
        return new Loggable() {
          @NotNull
          public String describe(final boolean verbose) {
            return source.name + "=" + source.value;
          }
        };
      }
    }), "\n", "", "");
  }
}
