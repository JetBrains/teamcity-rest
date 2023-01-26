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

package jetbrains.buildServer.server.rest.model.changeLog;

import javax.ws.rs.core.UriBuilder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.util.Pager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChangeLogPagerData implements PagerData {
  public static final String PAGE_DIMENSION = "page";
  public static final String RECORDS_PER_PAGE_DIMENSION = "pageSize";
  private final UriBuilder myUriBuilder;
  private final Locator myLocator;
  private final Pager myPager;

  public ChangeLogPagerData(@NotNull final UriBuilder uriBuilder, @NotNull final Locator logLocator, @NotNull final Pager pager) {
    myUriBuilder = uriBuilder.clone();
    myLocator = logLocator;
    myPager = pager;
  }

  @NotNull
  @Override
  public String getHref() {
    return myUriBuilder.clone().path(myLocator.toString()).build().toString();
  }

  @Nullable
  @Override
  public String getNextHref() {
    int curPage = myLocator.getSingleDimensionValueAsLong(PAGE_DIMENSION, 1L).intValue();
    int curRecordsPerPage = myLocator.getSingleDimensionValueAsLong(RECORDS_PER_PAGE_DIMENSION, (long) myPager.getRecordsPerPage()).intValue();

    if(!myPager.isUnknownNumberOfRecords() && curPage >= myPager.getPageCount()) {
      return null;
    }

    Locator resultingLocator = new Locator(myLocator)
      .setDimension(PAGE_DIMENSION, Integer.toString(curPage + 1))
      .setDimension(RECORDS_PER_PAGE_DIMENSION, Integer.toString(curRecordsPerPage));

    return myUriBuilder.clone().path(resultingLocator.toString()).build().toString();
  }

  @Nullable
  @Override
  public String getPrevHref() {
    int curPage = myLocator.getSingleDimensionValueAsLong(PAGE_DIMENSION, 1L).intValue();
    if(curPage <= 1) {
      return null;
    }

    int curRecordsPerPage = myLocator.getSingleDimensionValueAsLong(RECORDS_PER_PAGE_DIMENSION, (long) myPager.getRecordsPerPage()).intValue();
    int prevPage = curPage > myPager.getPageCount() ? myPager.getPageCount() : curPage - 1;

    Locator resultingLocator = new Locator(myLocator)
      .setDimension(PAGE_DIMENSION, Integer.toString(prevPage))
      .setDimension(RECORDS_PER_PAGE_DIMENSION, Integer.toString(curRecordsPerPage));

    return myUriBuilder.clone().path(resultingLocator.toString()).build().toString();
  }
}
