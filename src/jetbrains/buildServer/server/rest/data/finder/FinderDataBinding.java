/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.finder;

import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.util.DuplicateChecker;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 06/06/2016
 */
public interface FinderDataBinding<ITEM> {

  @Nullable
  Long getDefaultPageItemsCount();

  @Nullable
  Long getDefaultLookupLimit();

  @NotNull
  String[] getKnownDimensions();

  @NotNull
  String[] getHiddenDimensions();

  @Nullable
  Locator.DescriptionProvider getLocatorDescriptionProvider();

  /**
   * @return the item found or null if this is not single item locator
   * @throws NotFoundException when the locator is for single item, but the item does not exist / is not accessible for the current user
   */
  @Nullable
  ITEM findSingleItem(@NotNull final Locator locator);

  @NotNull
  LocatorDataBinding<ITEM> getLocatorDataBinding(@NotNull final Locator locator);

  @NotNull
  String getItemLocator(@NotNull final ITEM item);

  /**
   * Returns new duplicate checker. Is used for "unique" dimension processing.
   * @return null if "unique" dimension is not supported.
   */
  @Nullable
  DuplicateChecker<ITEM> createDuplicateChecker();

}
