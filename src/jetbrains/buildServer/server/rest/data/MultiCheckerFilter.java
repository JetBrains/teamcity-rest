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

package jetbrains.buildServer.server.rest.data;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 09.09.2009
 */
public class MultiCheckerFilter<T> implements ItemFilter<T> {
  @NotNull private final List<FilterConditionChecker<T>> myCheckers;

  public MultiCheckerFilter() {
    myCheckers = new ArrayList<FilterConditionChecker<T>>();
  }

  public MultiCheckerFilter<T> add(FilterConditionChecker<T> checker) {
    myCheckers.add(checker);
    return this;
  }

  public int getSubFiltersCount(){
    return myCheckers.size();
  }

  public boolean isIncluded(@NotNull T item) {
    for (FilterConditionChecker<T> checker : myCheckers) {
      if (!checker.isIncluded(item)) {
        return false;
      }
    }
    return true;
  }

  public boolean shouldStop(@NotNull final T item) {
    return false;
  }
}
