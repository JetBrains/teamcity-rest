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

package jetbrains.buildServer.server.rest.data.problem.scope;

import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.server.rest.data.util.ItemFilter;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.ParameterCondition;
import jetbrains.buildServer.server.rest.data.ValueCondition;
import jetbrains.buildServer.server.rest.data.finder.impl.BuildTypeFinder;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class TestScopeFilterProducer {
  public static final String[] SUPPORTED_DIMENSIONS = {
    "suite",
    "package",
    "class",
    "buildType"
  };
  @NotNull
  private final BuildTypeFinder myBuildTypefinder;

  public TestScopeFilterProducer(@NotNull BuildTypeFinder buildTypeFinder) {
    myBuildTypefinder = buildTypeFinder;
  }

  public TestScopeFilter createFromLocatorString(@NotNull String locator) {
    return createFromLocator(new Locator(locator));
  }

  public TestScopeFilter createFromLocator(@NotNull Locator locator) {
    locator.addSupportedDimensions(SUPPORTED_DIMENSIONS);

    List<Filter<STestRun>> conditions = new ArrayList<>();

    String suiteConditionDef = locator.getSingleDimensionValue("suite");
    if(suiteConditionDef != null) {
      ValueCondition condition = ParameterCondition.createValueCondition(suiteConditionDef);
      conditions.add(item -> condition.matches(item.getTest().getName().getSuite()));
    }

    String packageConditionDef = locator.getSingleDimensionValue("package");
    if(packageConditionDef != null) {
      ValueCondition condition = ParameterCondition.createValueCondition(packageConditionDef);
      conditions.add(item -> condition.matches(item.getTest().getName().getPackageName()));
    }

    String classConditionDef = locator.getSingleDimensionValue("class");
    if(classConditionDef != null) {
      ValueCondition condition = ParameterCondition.createValueCondition(classConditionDef);
      conditions.add(item -> condition.matches(item.getTest().getName().getClassName()));
    }

    String buildTypeConditionDef = locator.getSingleDimensionValue("buildType");
    if(buildTypeConditionDef != null) {
      ItemFilter<BuildTypeOrTemplate> filter = myBuildTypefinder.getFilter(buildTypeConditionDef);

      conditions.add(item -> {
        SBuildType bt = item.getBuild().getBuildType();
        if(bt == null)
          return false;

        return filter.isIncluded(new BuildTypeOrTemplate(bt));
      });
    }

    return new TestScopeFilterImpl(conditions, StringUtil.join(",", suiteConditionDef, packageConditionDef, classConditionDef, buildTypeConditionDef));
  }
}
