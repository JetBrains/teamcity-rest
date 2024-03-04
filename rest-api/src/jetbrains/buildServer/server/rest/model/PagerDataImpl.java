/*
 * Copyright 2000-2024 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.finder.AbstractFinder;
import jetbrains.buildServer.server.rest.data.util.LocatorUtil;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 01.08.2009
 */
public class PagerDataImpl implements PagerData {
  @NotNull
  private final String myHref;
  @Nullable
  private final String myNextHref;
  @Nullable
  private final String myPrevHref;

  /**
   * @param uriBuilder           UriBuilder for the current Url
   * @param start                number of the starting item on the current page
   * @param count                count of the items on a page
   * @param currentPageRealCount number of items on the current page
   * @param locatorText          if specified, 'locatorQueryParameterName' should also be specified, replaces/adds start/count in the locator query parameter instead of the URL query parameters
   * @param locatorQueryParameterName
   */
  public PagerDataImpl(@NotNull final UriBuilder uriBuilder,
                       @NotNull final String contextPath,
                       @NotNull final PagedSearchResult<?> pagedResult,
                       @Nullable final String locatorText,
                       @Nullable final String locatorQueryParameterName) {
    final Long start = pagedResult.getStart();
    final Long count = pagedResult.getCount() == null ? null : Long.valueOf(pagedResult.getCount());
    long currentPageRealCount = pagedResult.getActualCount();

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

    if (pagedResult.getLookupLimit() != null && pagedResult.getLookupLimitReached()) {
      nextHref = adjustLookupLimitForNextPage(
        uriBuilder,
        start, count,
        pagedResult.getLookupLimit(),
        currentPageRealCount,
        locatorText,
        locatorQueryParameterName
      );
    }
    myNextHref = nextHref == null ? null : getRelativePath(nextHref.getBuilder().build(), contextPath);
    myPrevHref = prevHref == null ? null : getRelativePath(prevHref.build(), contextPath);
  }

  @NotNull
  private static UriModification adjustLookupLimitForNextPage(@NotNull UriBuilder uriBuilder,
                                                              @Nullable Long start,
                                                              @Nullable Long count,
                                                              long currentLookupLimit,
                                                              long currentPageRealCount,
                                                              @Nullable final String locatorText,
                                                              @Nullable final String locatorQueryParameterName) {
    UriModification nextHref;
    if (StringUtil.isEmpty(locatorQueryParameterName)) {
      throw new OperationException("TeamCity REST API implementation error: lookupLimit is passed while no locator parameter name is specified.");
    }
    // If no items were served - weshould just expand the lookup limit
    if (currentPageRealCount == 0) {
      nextHref = new UriModification(uriBuilder, locatorText);
    } else {
      // If some items were served - let's expand the lookup limit, but for the nextHref
      // we want to skip those which were returned in the current request, so adjust the start and keep the count unmodified.

      long nextStart = (start != null ? start : 0) + currentPageRealCount;

      nextHref = getModifiedBuilder(uriBuilder, nextStart, count, locatorText, locatorQueryParameterName);
    }
    final String newLocator = LocatorUtil.setDimension(nextHref.getCurrentLocatorText(), AbstractFinder.DIMENSION_LOOKUP_LIMIT, getNextLookUpLimit(currentLookupLimit));
    return new UriModification(nextHref.getBuilder().replaceQueryParam(locatorQueryParameterName, Util.encodeUrlParamValue(newLocator)), newLocator);
  }

  private static long getNextLookUpLimit(final long currentLookupLimit) {
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

  @NotNull
  private static UriModification getModifiedBuilder(@NotNull final UriBuilder baseUriBuilder, final long start, @Nullable final Long count,
                                             @Nullable final String locatorText, @Nullable final String locatorQueryParameterName) {
    final UriBuilder newBuilder = baseUriBuilder.clone();
    if (StringUtil.isEmpty(locatorQueryParameterName)) {
      final UriBuilder startPatched = newBuilder.replaceQueryParam(PagerData.START, start);
      final UriBuilder result = count == null ? startPatched : startPatched.replaceQueryParam(PagerData.COUNT, count);
      return new UriModification(result, null);
    }
    newBuilder.replaceQueryParam(PagerData.START, null).replaceQueryParam(PagerData.COUNT, null);
    final String locatorWithStart = LocatorUtil.setDimension(locatorText, PagerData.START, start);
    String newLocator = count == null ? locatorWithStart : LocatorUtil.setDimension(locatorWithStart, PagerData.COUNT, count);
    return new UriModification(newBuilder.replaceQueryParam(locatorQueryParameterName, Util.encodeUrlParamValue(newLocator)), newLocator);
  }

  private static class UriModification {
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

  @Override
  @NotNull
  public String getHref() {
    return myHref;
  }

  @Override
  @Nullable
  public String getNextHref() {
    return myNextHref;
  }

  @Override
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
      sb.append('?').append(Util.humanReadableUrlParamValue(uri.getRawQuery()));
    }
    if (uri.getFragment() != null) {
      sb.append('#').append(Util.humanReadableUrlParamValue(uri.getRawFragment()));
    }
    return sb.toString();
  }
}