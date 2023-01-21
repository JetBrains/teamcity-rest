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

package jetbrains.buildServer.server.rest.data.util;

import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

public class UnwrappingFilter<NAKED, WRAPPED> implements ItemFilter<WRAPPED> {
  private final ItemFilter<NAKED> myNakedFilter;
  private final Function<WRAPPED, NAKED> myUnwrapper;

  public UnwrappingFilter(@NotNull ItemFilter<NAKED> nakedFilter, @NotNull Function<WRAPPED, NAKED> unwrapper) {
    myNakedFilter = nakedFilter;
    myUnwrapper = unwrapper;
  }

  @Override
  public boolean isIncluded(@NotNull WRAPPED item) {
    return myNakedFilter.isIncluded(myUnwrapper.apply(item));
  }

  @Override
  public boolean shouldStop(@NotNull WRAPPED item) {
    return myNakedFilter.shouldStop(myUnwrapper.apply(item));
  }
}
