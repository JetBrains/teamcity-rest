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

package jetbrains.buildServer.server.rest.data;

import java.util.List;
import jetbrains.buildServer.server.rest.data.finder.Finder;
import jetbrains.buildServer.server.rest.data.util.ItemFilter;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 05/07/2016
 */
public class FinderSearchMatcher<T> {
  protected static final String SEARCH = "search";
  protected static final String MATCH = "match";

  @NotNull private final Finder<T> myFinder;
  @NotNull private final String mySearch;
  @Nullable private final String myMatch;

  public FinderSearchMatcher(@NotNull final String collectionLocatorText, @NotNull final Finder<T> finder) {
    myFinder = finder;
    final Locator locator = new Locator(collectionLocatorText, SEARCH, MATCH);
    locator.processHelpRequest();
    String searchDimension = locator.getSingleDimensionValue(SEARCH);
    if (searchDimension == null) {
      throw new BadRequestException("Wrong locator '" + collectionLocatorText + "': dimension '" + SEARCH + "' should be specified");
    }
    mySearch = searchDimension;
    myMatch = locator.getSingleDimensionValue(MATCH);
    locator.checkLocatorFullyProcessed();
  }

  public boolean matches(@Nullable final String searchDefaultLocatorText) {
    String mergedSearchLocatorText = Locator.merge(mySearch, searchDefaultLocatorText);

    List<T> found = myFinder.getItems(mergedSearchLocatorText).getEntries();
    if (found.isEmpty()) { // nothing found by "search"
      return false;
    }
    if (myMatch == null) {
      return found.size() > 0; // found anything when "match" is empty
    }

    final ItemFilter<T> filter = myFinder.getFilter(myMatch);
    for (T t : found) {
      if (!filter.isIncluded(t)) return false; // found result which doe snot match the "match" condition
    }
    return true; // all results match the "match" condition
  }
}