/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.data.AbstractFinder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.serverSide.TeamCityProperties;
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
    final Long count = pagedResult.myCount == null ? null : Long.valueOf(pagedResult.myCount);
    long currentPageRealCount = pagedResult.myActualCount;

    myHref = getRelativePath(uriBuilder.build(), contextPath); //todo: investigate a way to preserve order of the parameters
    UriModification nextHref;
    UriBuilder prevHref;
    if (start == null || start == 0) {
      prevHref = null;
      if (count == null || currentPageRealCount < count) {
        nextHref = null;
      } else {
        nextHref = getModifiedBuilder(uriBuilder, 0 + count, count, locatorText, locatorQueryParameterName);
      }
    } else {
      if (count == null) {
        nextHref = null;

        prevHref = getModifiedBuilder(uriBuilder, 0, start, locatorText, locatorQueryParameterName).getBuilder();
      } else {
        if (currentPageRealCount < count) {
          nextHref = null;
        } else {
          nextHref = getModifiedBuilder(uriBuilder, start + count, count, locatorText, locatorQueryParameterName);
        }
        final long itemsFromStart = start - count;
        if (itemsFromStart < 0) {
          prevHref = getModifiedBuilder(uriBuilder, 0, start, locatorText, locatorQueryParameterName).getBuilder();
        } else {
          prevHref = getModifiedBuilder(uriBuilder, itemsFromStart, count, locatorText, locatorQueryParameterName).getBuilder();
        }
      }
    }

    if (pagedResult.myLookupLimit != null && pagedResult.myLookupLimitReached) {
      if (StringUtil.isEmpty(locatorQueryParameterName)) {
        throw new OperationException("TeamCity REST API implementation error: lookupLimit is passed while no locator parameter name is specified.");
      }
      if (currentPageRealCount == 0) {
        nextHref = new UriModification(uriBuilder, locatorText);
      } else {
        nextHref = getModifiedBuilder(uriBuilder, (start != null ? start : 0) + currentPageRealCount, (count != null ? count : 0), locatorText, locatorQueryParameterName);
      }
      final String newLocator = Locator.setDimension(nextHref.getCurrentLocatorText(), AbstractFinder.DIMENSION_LOOKUP_LIMIT, getNextLookUpLimit(pagedResult.myLookupLimit));
      nextHref = new UriModification(nextHref.getBuilder().replaceQueryParam(locatorQueryParameterName, newLocator), newLocator);
    }
    myNextHref = nextHref == null ? null : getRelativePath(nextHref.getBuilder().build(), contextPath);
    myPrevHref = prevHref == null ? null : getRelativePath(prevHref.build(), contextPath);
  }

  private long getNextLookUpLimit(final long currentLookupLimit) {
    final long exponentialNextStep = Math.round((double)currentLookupLimit * TeamCityProperties.getFloat("rest.page.nextLookupLimitMultiplier", 2.0f)) - currentLookupLimit;
    final long maxNextStep = TeamCityProperties.getLong("rest.page.nextLookupLimitMaxStep", 5000);
    if (exponentialNextStep > maxNextStep) {
      if (maxNextStep < 1) {
        return currentLookupLimit + 1; //protection against looping
      }
      return currentLookupLimit + maxNextStep;
    }
    if (currentLookupLimit < 1) {
      return currentLookupLimit + 1; //protection against looping
    }
    return currentLookupLimit + exponentialNextStep;
  }

  private UriModification getModifiedBuilder(@NotNull final UriBuilder baseUriBuilder, final long start, @Nullable final Long count,
                                             @Nullable final String locatorText, @Nullable final String locatorQueryParameterName) {
    final UriBuilder newBuilder = baseUriBuilder.clone();
    if (StringUtil.isEmpty(locatorQueryParameterName)) {
      final UriBuilder startPatched = newBuilder.replaceQueryParam(START, start);
      final UriBuilder result = count == null ? startPatched : startPatched.replaceQueryParam(COUNT, count);
      return new UriModification(result, null);
    }
    newBuilder.replaceQueryParam(START, null).replaceQueryParam(COUNT, null);
    final String locatorWithStart = Locator.setDimension(locatorText, START, start);
    String newLocator = count == null ? locatorWithStart : Locator.setDimension(locatorWithStart, COUNT, count);
    return new UriModification(newBuilder.replaceQueryParam(locatorQueryParameterName, newLocator), newLocator);
  }

  class UriModification {
    @NotNull private final UriBuilder myBuilder;
    @Nullable private final String myCurrentLocatorText;

    public UriModification(@NotNull final UriBuilder builder, @Nullable final String currentLocatorText) {
      myBuilder = builder;
      myCurrentLocatorText = currentLocatorText;
    }

    @NotNull
    public UriBuilder getBuilder() {
      return myBuilder;
    }

    @Nullable
    public String getCurrentLocatorText() {
      return myCurrentLocatorText;
    }
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
