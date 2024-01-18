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

package jetbrains.buildServer.server.rest.data.finder.syntax;

import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.server.rest.data.locator.Dimension;
import jetbrains.buildServer.server.rest.data.locator.EnumValue;
import jetbrains.buildServer.server.rest.data.locator.Syntax;
import jetbrains.buildServer.server.rest.data.locator.definition.FinderLocatorDefinition;
import jetbrains.buildServer.server.rest.model.buildType.ProblemTarget;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorDimensionDataType;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;

@LocatorResource(value = LocatorName.INVESTIGATION,
  baseEntity = "Investigation",
  examples = {
    "`assignee:John Smith` - find investigations assigned to `John Smith`.",
    "`state:taken` - find investigations which are currently in work."
  }
)
public class InvestigationDimensions implements FinderLocatorDefinition {
  public static final Dimension PROBLEM_DIMENSION = Dimension.ofName("problem").description("Problem locator.")
                                                             .syntax(Syntax.forLocator(LocatorName.PROBLEM)).build();
  public static final Dimension TEST_DIMENSION = Dimension.ofName("test").description("Test locator.")
                                                          .syntax(Syntax.forLocator(LocatorName.TEST)).build();
  // TODO: Consider adding assignmentBuildType.
  public static final Dimension ASSIGNMENT_PROJECT = Dimension.ofName("assignmentProject").description("Project (direct parent) locator.")
                                                              .syntax(Syntax.forLocator(LocatorName.PROJECT)).build();
  public static final Dimension AFFECTED_PROJECT = Dimension.ofName("affectedProject").description("Project (direct or indirect parent) locator.")
                                                            .syntax(Syntax.forLocator(LocatorName.PROJECT)).build();
  public static final Dimension ASSIGNEE = Dimension.ofName("assignee").description("User locator.")
                                                    .syntax(Syntax.forLocator(LocatorName.USER)).build();
  public static final Dimension SINCE_DATE = Dimension.ofName("sinceDate").description("yyyyMMddTHHmmss+ZZZZ")
                                                      .syntax(Syntax.forLocator(LocatorDimensionDataType.TIMESTAMP)).build();
  public static final Dimension STATE = Dimension.ofName("state")
                                                 .syntax(EnumValue.of(ResponsibilityEntry.State.class)).build();
  public static final Dimension RESOLUTION = Dimension.ofName("resolution")
                                                      .syntax(EnumValue.of(ResponsibilityEntry.RemoveMethod.class)).build();
  public static final Dimension TYPE = Dimension.ofName("type")
                                                .syntax(EnumValue.of(ProblemTarget.getKnownTypesForInvestigation())).build();
  public static final Dimension REPORTER = Dimension.ofName("reporter").description("User locator.")
                                                    .syntax(Syntax.forLocator(LocatorName.USER)).build();
  public static final Dimension BUILD_TYPE = Dimension.ofName("buildType").description("Build type locator.")
                                                      .syntax(Syntax.forLocator(LocatorName.BUILD_TYPE)).build();
}
