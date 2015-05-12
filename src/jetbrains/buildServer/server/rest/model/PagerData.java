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

import com.intellij.openapi.util.text.StringUtil;
import java.net.URI;
import javax.ws.rs.core.UriBuilder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 01.08.2009
 */

/**
 * Provides Href for next and previous pages of paged data
 */
public class PagerData {

  public static final String START = "start";
  public static final String COUNT = "count";
  @NotNull
  private final String myHref;
  @Nullable
  private final String myNextHref;
  @Nullable
  private final String myPrevHref;

  /**
   * Constructs an object without prev/next links
   * @param href relative not transformed URL
   */
  public PagerData(@NotNull String href){
    myHref = href;
    myNextHref = null;
    myPrevHref = null;
  }

  /**
   * @param uriBuilder           UriBuilder for the current Url
   * @param start                number of the starting item on the current page
   * @param count                count of the items on a page
   * @param currentPageRealCount number of items on the current page
   * @param locatorText          if specified, 'locatorQueryParameterName' should also be specified, replaces/adds start/count in the locator query parameter instead of the URL query parameters
   * @param locatorQueryParameterName
   */
  public PagerData(@NotNull final UriBuilder uriBuilder,
                   @NotNull final String contextPath,
                   @NotNull final PagedSearchResult pagedResult,
                   @Nullable final String locatorText, @Nullable final String locatorQueryParameterName) {
    final Long start = pagedResult.myStart;
    final Integer count = pagedResult.myCount;
    long currentPageRealCount = pagedResult.myActualCount;

    myHref = getRelativePath(uriBuilder.build(), contextPath); //todo: investigate a way to preserve order of the parameters
    URI nextHref;
    URI prevHref;
    if (start == null || start == 0) {
      prevHref = null;
      if (count == null || currentPageRealCount < count) {
        nextHref = null;
      } else {
        nextHref = getModifiedHref(uriBuilder, 0 + count, count, locatorText, locatorQueryParameterName);
      }
    } else {
      if (count == null) {
        nextHref = null;

        prevHref = getModifiedHref(uriBuilder, 0, start, locatorText, locatorQueryParameterName);
      } else {
        if (currentPageRealCount < count) {
          nextHref = null;
        } else {
          nextHref = getModifiedHref(uriBuilder, start + count, count, locatorText, locatorQueryParameterName);
        }
        final long itemsFromStart = start - count;
        if (itemsFromStart < 0) {
          prevHref = getModifiedHref(uriBuilder, 0, start, locatorText, locatorQueryParameterName);
        } else {
          prevHref = getModifiedHref(uriBuilder, itemsFromStart, count, locatorText, locatorQueryParameterName);
        }
      }
    }
    myNextHref = nextHref == null ? null : getRelativePath(nextHref, contextPath);
    myPrevHref = prevHref== null ? null : getRelativePath(prevHref, contextPath);
  }

  private URI getModifiedHref(@NotNull final UriBuilder uriBuilder, final long start, final long count,
                              @Nullable final String locatorText, @Nullable final String locatorQueryParameterName) {
    if (StringUtil.isEmpty(locatorText) || StringUtil.isEmpty(locatorQueryParameterName)) {
      return uriBuilder.replaceQueryParam(START, start).replaceQueryParam(COUNT, count).build();
    }
    String newLocator = Locator.setDimension(Locator.setDimension(locatorText, START, start), COUNT, count);
    return uriBuilder.replaceQueryParam(locatorQueryParameterName, newLocator).build();
  }

  @NotNull
  public String getHref() {
    return myHref;
  }

  @Nullable
  public String getNextHref() {
    return myNextHref;
  }

  @Nullable
  public String getPrevHref() {
    return myPrevHref;
  }

  @NotNull
  private static String getRelativePath(@NotNull final URI uri, @Nullable final String pathPrefixToExclude) {
    String path = uri.getRawPath();
    assert path != null;
    StringBuffer sb = new StringBuffer();

    if (pathPrefixToExclude != null && path.startsWith(pathPrefixToExclude)) {
      path = path.substring(pathPrefixToExclude.length());
    }
    sb.append(path);
    if (uri.getQuery() != null) {
      sb.append('?').append(uri.getRawQuery());
    }
    if (uri.getFragment() != null) {
      sb.append('#').append(uri.getRawFragment());
    }
    return sb.toString();
  }
}
