/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.problem.scope;

import java.util.List;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.util.filters.Filter;
import org.jetbrains.annotations.NotNull;

public class TestScopeFilterImpl implements TestScopeFilter {
  public static final String[] SUPPORTED_DIMENSIONS = {"suite", "package", "class", "buildType", "affectedProject"};

  @NotNull
  private final List<Filter<STestRun>> myConditions;
  @NotNull
  private final String myLocatorString;

  public TestScopeFilterImpl(@NotNull List<Filter<STestRun>> conditions, @NotNull String locatorString) {
    myConditions = conditions;
    myLocatorString = locatorString;
  }

  @NotNull
  @Override
  public String getLocatorString() {
    return myLocatorString;
  }

  @Override
  public boolean test(@NotNull STestRun item) {
    return myConditions.stream().allMatch(f -> f.accept(item));
  }
}