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
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.ParameterCondition;
import jetbrains.buildServer.server.rest.data.ValueCondition;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestScopeFilterImpl implements TestScopeFilter {
  public static final String[] SUPPORTED_DIMENSIONS = {"suite", "package", "class", "buildType", "affectedProject"};

  @NotNull
  private final List<Filter<STestRun>> myConditions;

  private final String mySuiteConditionDef;
  private final String myPackageConditionDef;
  private final String myClassConditionDef;

  public TestScopeFilterImpl(@Nullable String locator) {
    this(Locator.createPotentiallyEmptyLocator(locator));
  }

  public TestScopeFilterImpl(@NotNull Locator locator) {
    locator.addSupportedDimensions(SUPPORTED_DIMENSIONS);

    myConditions = new ArrayList<>();

    mySuiteConditionDef = locator.getSingleDimensionValue("suite");
    if(mySuiteConditionDef != null) {
      ValueCondition condition = ParameterCondition.createValueCondition(mySuiteConditionDef);
      myConditions.add(item -> condition.matches(item.getTest().getName().getSuite()));
    }
    myPackageConditionDef = locator.getSingleDimensionValue("package");
    if(myPackageConditionDef != null) {
      ValueCondition condition = ParameterCondition.createValueCondition(myPackageConditionDef);
      myConditions.add(item -> condition.matches(item.getTest().getName().getPackageName()));
    }
    myClassConditionDef = locator.getSingleDimensionValue("class");
    if(myClassConditionDef != null) {
      ValueCondition condition = ParameterCondition.createValueCondition(myClassConditionDef);
      myConditions.add(item -> condition.matches(item.getTest().getName().getClassName()));
    }
  }

  @NotNull
  @Override
  public String getLocatorString() {
    return StringUtil.join(",", mySuiteConditionDef, myPackageConditionDef, myClassConditionDef);
  }

  @Override
  public boolean test(@NotNull STestRun item) {
    return myConditions.stream().allMatch(f -> f.accept(item));
  }
}