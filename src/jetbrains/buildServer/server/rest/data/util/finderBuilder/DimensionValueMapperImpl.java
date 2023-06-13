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

package jetbrains.buildServer.server.rest.data.util.finderBuilder;

import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Maps given single dimension value of the locator to an item.
 *
 * @param <TYPE> type of produced items.
 */
public class DimensionValueMapperImpl<TYPE> implements DimensionValueMapper<TYPE> {
  @NotNull private final Function<String, TYPE> myRetriever;
  @Nullable private String myLocatorTypeDescription;

  public DimensionValueMapperImpl(@NotNull final Function<String, TYPE> retriever) {
    myRetriever = retriever;
  }

  @Override
  @Nullable
  public String getLocatorTypeDescription() {
    return myLocatorTypeDescription;
  }

  @Override
  @Nullable
  public TYPE get(@NotNull final String dimensionValue) {
    return myRetriever.apply(dimensionValue);
  }

  /**
   * Human-readable type description of locator dimension values accepted by this mapper.
   * E.g. number, boolean, etc
   */
  @NotNull
  public DimensionValueMapperImpl<TYPE> acceptingType(@NotNull String locatorDimensionTypeDescription) {
    myLocatorTypeDescription = locatorDimensionTypeDescription;
    return this;
  }
}
