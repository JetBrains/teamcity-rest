/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.UriBuilder;
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

  @Nullable
  private URI myNextHref;
  @Nullable
  private URI myPrevHref;
  private String myContextPath;

  public PagerData() {
  }

  /**
   * @param uriBuilder           UriBuilder for the current Url
   * @param request              Request in the scope of which the pagerData will be used
   * @param start                number of the starting item on the current page
   * @param count                count of the items on a page
   * @param currentPageRealCount number of items on the current page
   */
  public PagerData(@NotNull final UriBuilder uriBuilder,
                   @NotNull final HttpServletRequest request,
                   @Nullable final Long start,
                   @Nullable final Integer count,
                   long currentPageRealCount) {
    //todo: set start and count in locator, if specified
    myContextPath = request.getContextPath();
    if (start == null || start == 0) {
      myPrevHref = null;
      if (count == null || currentPageRealCount < count) {
        myNextHref = null;
      } else {
        myNextHref = uriBuilder.replaceQueryParam("start", 0 + count).replaceQueryParam("count", count).build();
      }
    } else {
      if (count == null) {
        myNextHref = null;

        myPrevHref = uriBuilder.replaceQueryParam("start", 0).replaceQueryParam("count", start).build();
      } else {
        if (currentPageRealCount < count) {
          myNextHref = null;
        } else {
          myNextHref = uriBuilder.replaceQueryParam("start", start + count).replaceQueryParam("count", count).build();
        }
        final long itemsFromStart = start - count;
        if (itemsFromStart < 0) {
          myPrevHref = uriBuilder.replaceQueryParam("start", 0).replaceQueryParam("count", start).build();
        } else {
          myPrevHref = uriBuilder.replaceQueryParam("start", itemsFromStart).replaceQueryParam("count", count).build();
        }
      }
    }
  }

  @Nullable
  public String getNextHref() {
    return myNextHref == null ? null : getRelativePath(myNextHref, myContextPath);
  }

  @Nullable
  public String getPrevHref() {
    return myPrevHref == null ? null : getRelativePath(myPrevHref, myContextPath);
  }

  private static String getRelativePath(@NotNull final URI uri, @Nullable final String pathPrefixToExclude) {
    String path = uri.getPath();
    assert path != null;
    StringBuffer sb = new StringBuffer();

    if (pathPrefixToExclude != null && path.startsWith(pathPrefixToExclude)) {
      path = path.substring(pathPrefixToExclude.length());
    }
    sb.append(path);
    if (uri.getQuery() != null) {
      sb.append('?').append(uri.getQuery());
    }
    if (uri.getFragment() != null) {
      sb.append('#').append(uri.getFragment());
    }
    return sb.toString();
  }
}
