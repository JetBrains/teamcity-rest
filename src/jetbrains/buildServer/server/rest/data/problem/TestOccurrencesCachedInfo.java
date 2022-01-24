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

package jetbrains.buildServer.server.rest.data.problem;

import jetbrains.buildServer.serverSide.ShortStatistics;
import org.jetbrains.annotations.Nullable;

public class TestOccurrencesCachedInfo {
  @Nullable
  private final ShortStatistics myShortStatistics;
  private final boolean myFilteringRequired;

  public static TestOccurrencesCachedInfo empty() {
    return new TestOccurrencesCachedInfo(null, false);
  }

  public TestOccurrencesCachedInfo(@Nullable ShortStatistics shortStatistics, boolean filteringRequired) {
    myShortStatistics = shortStatistics;
    myFilteringRequired = filteringRequired;
  }

  @Nullable
  public ShortStatistics getShortStatistics() {
    return myShortStatistics;
  }

  public boolean filteringRequired() {
    return myFilteringRequired;
  }
}
