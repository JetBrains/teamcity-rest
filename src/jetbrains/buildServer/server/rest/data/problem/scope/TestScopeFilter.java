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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.ParameterCondition;
import jetbrains.buildServer.server.rest.data.ValueCondition;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.util.filters.Filter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestScopeFilter implements Predicate<STestRun> {
  private static final String[] SUPPORTED_DIMENSIONS = {"suite", "package", "class" };

  @NotNull
  private final List<Filter<STestRun>> myConditions;

  public TestScopeFilter(@Nullable String definition) {
    Locator locator = Locator.createPotentiallyEmptyLocator(definition);
    locator.addSupportedDimensions(SUPPORTED_DIMENSIONS);

    myConditions = new ArrayList<>();

    String suiteConditionDef = locator.getSingleDimensionValue("suite");
    if(suiteConditionDef != null) {
      ValueCondition condition = ParameterCondition.createValueCondition(suiteConditionDef);
      myConditions.add(item -> condition.matches(item.getTest().getName().getSuite()));
    }
    String packageConditionDef = locator.getSingleDimensionValue("package");
    if(packageConditionDef != null) {
      ValueCondition condition = ParameterCondition.createValueCondition(packageConditionDef);
      myConditions.add(item -> condition.matches(item.getTest().getName().getPackageName()));
    }
    String classConditionDef = locator.getSingleDimensionValue("class");
    if(classConditionDef != null) {
      ValueCondition condition = ParameterCondition.createValueCondition(classConditionDef);
      myConditions.add(item -> condition.matches(item.getTest().getName().getClassName()));
    }

    locator.checkLocatorFullyProcessed();
  }

  @Override
  public boolean test(@NotNull STestRun item) {
    return myConditions.stream().allMatch(f -> f.accept(item));
  }
}