/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import jetbrains.buildServer.server.rest.errors.OperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 06/06/2016
 */
public class DelegatingFinder<ITEM> implements Finder<ITEM> {
  private Finder<ITEM> myDelegate;

  public DelegatingFinder() {
  }

  public DelegatingFinder(@NotNull final Finder<ITEM> delegate) {
    myDelegate = delegate;
  }

  public void setDelegate(@NotNull final Finder<ITEM> delegate) {
    if (myDelegate != null) throw new OperationException("Delegate is already set");
    myDelegate = delegate;
  }

  @NotNull
  @Override
  public String getCanonicalLocator(@NotNull final ITEM item) {
    return myDelegate.getCanonicalLocator(item);
  }

  @NotNull
  @Override
  public PagedSearchResult<ITEM> getItems(@Nullable final String locatorText) {
    return myDelegate.getItems(locatorText);
  }

  @NotNull
  @Override
  public ITEM getItem(@Nullable final String locatorText) {
    return myDelegate.getItem(locatorText);
  }

  @NotNull
  @Override
  public ItemFilter<ITEM> getFilter(@NotNull final String locatorText) {
    return myDelegate.getFilter(locatorText);
  }

  @NotNull
  @Override
  public String getName() {
    return myDelegate.getName();
  }
}
