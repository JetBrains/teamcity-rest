/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.ws.rs.core.UriBuilder;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

/**
 * @author Yegor.Yarko
 *         Date: 30/11/2015
 */
public class PagerDataTest extends BaseServerTestCase {

  @Test
  public void testNothingFound() throws URISyntaxException {
    final List<String> items = new ArrayList<String>();
    check("/smth", null, new PagedSearchResult<String>(items, null, null, null, null, false, null), "/smth", null, null);
    check("/smth", "a:b", new PagedSearchResult<String>(items, null, null, null, null, false, null), "/smth?locator=a:b", null, null);
    check("/smth", "start:0,a:b", new PagedSearchResult<String>(items, 0L, null, null, null, false, null), "/smth?locator=start:0,a:b", null, null);
    check("/smth", "start:5,a:b", new PagedSearchResult<String>(items, 5L, null, null, null, false, null), "/smth?locator=start:5,a:b", null, "/smth?locator=start:0,a:b,count:5");
    check("/smth", "start:0,count:3,a:b", new PagedSearchResult<String>(items, 0L, 3, null, null, false, null), "/smth?locator=start:0,count:3,a:b", null, null);
    check("/smth", "start:5,count:3,a:b", new PagedSearchResult<String>(items, 5L, 3, null, null, false, null), "/smth?locator=start:5,count:3,a:b", null, "/smth?locator=start:2,count:3,a:b");
    check("/smth", "count:3,a:b", new PagedSearchResult<String>(items, null, 3, null, null, false, null), "/smth?locator=count:3,a:b", null, null);
  }

  @Test
  public void testSimple() throws URISyntaxException {
    final List<String> items = Arrays.<String>asList("a", "b");
    check("/smth", null, new PagedSearchResult<String>(items, null, null, null, null, false, null), "/smth", null, null);
    check("/smth", "a:b", new PagedSearchResult<String>(items, null, null, null, null, false, null), "/smth?locator=a:b", null, null);
    check("/smth", "start:0,a:b", new PagedSearchResult<String>(items, 0L, null, null, null, false, null), "/smth?locator=start:0,a:b", null, null);
    check("/smth", "start:5,a:b", new PagedSearchResult<String>(items, 5L, null, null, null, false, null), "/smth?locator=start:5,a:b", null, "/smth?locator=start:0,a:b,count:5");
    check("/smth", "start:0,count:3,a:b", new PagedSearchResult<String>(items, 0L, 3, null, null, false, null), "/smth?locator=start:0,count:3,a:b", null, null);
    check("/smth", "start:0,count:2,a:b", new PagedSearchResult<String>(items, 0L, 2, null, null, false, null), "/smth?locator=start:0,count:2,a:b", "/smth?locator=start:2,count:2,a:b", null);
    check("/smth", "start:5,count:3,a:b", new PagedSearchResult<String>(items, 5L, 3, null, null, false, null), "/smth?locator=start:5,count:3,a:b", null, "/smth?locator=start:2,count:3,a:b");
    check("/smth", "start:5,count:2,a:b", new PagedSearchResult<String>(items, 5L, 2, null, null, false, null), "/smth?locator=start:5,count:2,a:b", "/smth?locator=start:7,count:2,a:b", "/smth?locator=start:3,count:2,a:b");
    check("/smth", "count:3,a:b", new PagedSearchResult<String>(items, null, 3, null, null, false, null), "/smth?locator=count:3,a:b", null, null);
    check("/smth", "count:2,a:b", new PagedSearchResult<String>(items, null, 2, null, null, false, null), "/smth?locator=count:2,a:b", "/smth?locator=count:2,a:b,start:2", null);
 }

  @Test
  public void testLookupLimit() throws URISyntaxException {
    List<String> items = new ArrayList<String>();
    check("/smth", null, new PagedSearchResult<String>(items, null, null, null, 10L, false, null), "/smth", null, null);
    check("/smth", null, new PagedSearchResult<String>(items, null, null, null, 10L, true, null), "/smth", "/smth?locator=lookupLimit:20", null);
    check("/smth", "a:b", new PagedSearchResult<String>(items, null, null, null, 10L, false, null), "/smth?locator=a:b", null, null);
    check("/smth", "a:b", new PagedSearchResult<String>(items, null, null, null, 10L, true, null), "/smth?locator=a:b", "/smth?locator=a:b,lookupLimit:20", null);
    check("/smth", "start:0,a:b", new PagedSearchResult<String>(items, 0L, null, null, 10L, false, null), "/smth?locator=start:0,a:b", null, null);
    check("/smth", "start:0,a:b", new PagedSearchResult<String>(items, 0L, null, null, 10L, true, null), "/smth?locator=start:0,a:b", "/smth?locator=start:0,a:b,lookupLimit:20", null);
    check("/smth", "start:5,a:b", new PagedSearchResult<String>(items, 5L, null, null, 10L, false, null), "/smth?locator=start:5,a:b", null, "/smth?locator=start:0,a:b,count:5");
    check("/smth", "start:5,a:b", new PagedSearchResult<String>(items, 5L, null, null, 10L, true, null), "/smth?locator=start:5,a:b", "/smth?locator=start:5,a:b,lookupLimit:20", "/smth?locator=start:0,a:b,count:5");
    check("/smth", "start:0,count:3,a:b", new PagedSearchResult<String>(items, 0L, 3, null, 10L, false, null), "/smth?locator=start:0,count:3,a:b", null, null);
    check("/smth", "start:0,count:3,a:b", new PagedSearchResult<String>(items, 0L, 3, null, 10L, true, null), "/smth?locator=start:0,count:3,a:b", "/smth?locator=start:0,count:3,a:b,lookupLimit:20", null);
    check("/smth", "start:5,count:3,a:b", new PagedSearchResult<String>(items, 5L, 3, null, 10L, false, null), "/smth?locator=start:5,count:3,a:b", null, "/smth?locator=start:2,count:3,a:b");
    check("/smth", "start:5,count:3,a:b", new PagedSearchResult<String>(items, 5L, 3, null, 10L, true, null), "/smth?locator=start:5,count:3,a:b", "/smth?locator=start:5,count:3,a:b,lookupLimit:20", "/smth?locator=start:2,count:3,a:b");
    check("/smth", "count:3,a:b", new PagedSearchResult<String>(items, null, 3, null, 10L, false, null), "/smth?locator=count:3,a:b", null, null);
    check("/smth", "count:3,a:b", new PagedSearchResult<String>(items, null, 3, null, 10L, true, null), "/smth?locator=count:3,a:b", "/smth?locator=count:3,a:b,lookupLimit:20", null);
    check("/smth", "count:3,lookupLimit:5,a:b", new PagedSearchResult<String>(items, null, 3, null, 5L, true, null), "/smth?locator=count:3,lookupLimit:5,a:b", "/smth?locator=count:3,lookupLimit:10,a:b", null);

    items = Arrays.<String>asList("a", "b");
    check("/smth", "start:5,count:3,a:b", new PagedSearchResult<String>(items, 5L, 3, null, 10L, false, null), "/smth?locator=start:5,count:3,a:b", null, "/smth?locator=start:2,count:3,a:b");
    check("/smth", "start:5,count:3,a:b", new PagedSearchResult<String>(items, 5L, 3, null, 10L, true, null), "/smth?locator=start:5,count:3,a:b", "/smth?locator=start:7,count:3,a:b,lookupLimit:20", "/smth?locator=start:2,count:3,a:b");
    check("/smth", "start:5,count:2,a:b", new PagedSearchResult<String>(items, 5L, 2, null, 10L, false, null), "/smth?locator=start:5,count:2,a:b", "/smth?locator=start:7,count:2,a:b", "/smth?locator=start:3,count:2,a:b");
    check("/smth", "start:5,count:2,a:b", new PagedSearchResult<String>(items, 5L, 2, null, 10L, true, null), "/smth?locator=start:5,count:2,a:b", "/smth?locator=start:7,count:2,a:b,lookupLimit:20", "/smth?locator=start:3,count:2,a:b");

    check("/smth", "count:3,a:b", new PagedSearchResult<String>(items, null, 3, null, 10L, false, null), "/smth?locator=count:3,a:b", null, null);
    check("/smth", "count:3,a:b", new PagedSearchResult<String>(items, null, 3, null, 10L, true, null), "/smth?locator=count:3,a:b", "/smth?locator=count:3,a:b,start:2,lookupLimit:20", null);
    check("/smth", "count:2,a:b", new PagedSearchResult<String>(items, null, 2, null, 10L, false, null), "/smth?locator=count:2,a:b", "/smth?locator=count:2,a:b,start:2", null);
    check("/smth", "count:2,a:b", new PagedSearchResult<String>(items, null, 2, null, 10L, true, null), "/smth?locator=count:2,a:b", "/smth?locator=count:2,a:b,start:2,lookupLimit:20", null);
  }


  private void check(@NotNull final String currentHrefPath, @Nullable final String currentLocator, @NotNull final PagedSearchResult<String> pagedResult,
                     @Nullable final String href, @Nullable final Object nextHref, @Nullable final Object prevHref) throws URISyntaxException {
    final PagerData pagerData =
      new PagerData(UriBuilder.fromUri(new URI("http://some.url:8111/teamcity" + currentHrefPath + (currentLocator != null ? "?locator=" + currentLocator : ""))), "/teamcity",
                    pagedResult, currentLocator, "locator");
    assertEquals("href is different", href, pagerData.getHref());
    assertEquals("nextHref is different", nextHref, pagerData.getNextHref());
    assertEquals("prevHref is different", prevHref, pagerData.getPrevHref());
  }
}
