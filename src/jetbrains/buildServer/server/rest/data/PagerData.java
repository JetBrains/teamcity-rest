/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data;

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
  private String myNextHref;
  @Nullable
  private String myPrevHref;

  /**
   * @param allHref              relative URL for listing all items.
   * @param start                number of the starting item on the current page
   * @param count                count of the items on a page
   * @param currentPageRealCount number of items on the current page
   */
  public PagerData(final String allHref, @Nullable final Long start, @Nullable final Long count, long currentPageRealCount) {
    if (start == null || start == 0) {
      myPrevHref = null;
      if (count == null || currentPageRealCount < count) {
        myNextHref = null;
      } else {
        myNextHref = addQueryParam(addQueryParam(allHref, "start", Long.toString(0 + count)), "count", Long.toString(count));
      }
    } else {
      if (count == null) {
        myNextHref = null;

        myPrevHref = addQueryParam(allHref, "start", Long.toString(0));
        myPrevHref = addQueryParam(myPrevHref, "count", Long.toString(start));
      } else {
        if (currentPageRealCount < count) {
          myNextHref = null;
        } else {
          myNextHref = addQueryParam(addQueryParam(allHref, "start", Long.toString(start + count)), "count", Long.toString(count));
        }
        final long itemsFromStart = start - count;
        if (itemsFromStart < 0) {
          myPrevHref = addQueryParam(allHref, "start", Long.toString(0));
          myPrevHref = addQueryParam(myPrevHref, "count", Long.toString(start));
        } else {
          myPrevHref = addQueryParam(allHref, "start", Long.toString(itemsFromStart));
          myPrevHref = addQueryParam(myPrevHref, "count", Long.toString(count));
        }
      }
    }
  }

  //todo: use some util to support quoting, etc.
  // should replace params if they are already there
  private static String addQueryParam(String baseUrl, final String paramName, final String paramValue) {
    String result = baseUrl;
    if (!result.contains("?")) {
      result += "?";
    } else if (!result.endsWith("?")) {
      result += "&";
    }
    result = result + paramName + "=" + paramValue;
    return result;
  }

  @Nullable
  public String getNextHref() {
    return myNextHref;
  }

  @Nullable
  public String getPrevHref() {
    return myPrevHref;
  }
}
