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

import jetbrains.buildServer.server.rest.data.finder.AbstractFinder;
import jetbrains.buildServer.server.rest.data.locator.Dimension;
import jetbrains.buildServer.server.rest.data.locator.EnumValue;
import jetbrains.buildServer.server.rest.data.locator.PlainValue;
import jetbrains.buildServer.server.rest.data.locator.Syntax;
import jetbrains.buildServer.server.rest.data.locator.definition.FinderLocatorDefinition;
import jetbrains.buildServer.server.rest.model.buildType.ProblemTarget;
import jetbrains.buildServer.server.rest.model.problem.Resolution;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorDimensionDataType;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;

@LocatorResource(value = LocatorName.MUTE,
  extraDimensions = AbstractFinder.DIMENSION_ITEM,
  baseEntity = "Mute",
  examples = {
    "`project:<projectLocator>` - find muted problem under project found by `projectLocator`.",
    "`type:test` - find last 100 muted tests."
  }
)
public class MuteDimensions implements FinderLocatorDefinition {
  public static final Dimension ID = Dimension.ofName("id").description("Internal mute id.")
                                              .syntax(PlainValue.int64()).build();
  public static final Dimension AFFECTED_PROJECT = Dimension.ofName("affectedProject").description("Project affected by the mutes.")
                                                            .syntax(Syntax.forLocator(LocatorName.PROJECT)).build();
  public static final Dimension PROJECT = Dimension.ofName("project").description("Project in which mute is assigned.")
                                                   .syntax(Syntax.forLocator(LocatorName.PROJECT)).build(); //differs from investigation: assignmentProject
  public static final Dimension CREATION_DATE = Dimension.ofName("creationDate").description("Mute creation time, yyyyMMddTHHmmss+ZZZZ.")
                                                         .syntax(Syntax.TODO(LocatorDimensionDataType.TIMESTAMP)).build();  //differs from investigation: sinceDate
  public static final Dimension UNMUTE_DATE = Dimension.ofName("unmuteDate").description("Automatic unmute time, yyyyMMddTHHmmss+ZZZZ.")
                                                       .syntax(Syntax.TODO(LocatorDimensionDataType.TIMESTAMP)).build();  //differs from investigation: sinceDate
  public static final Dimension REPORTER = Dimension.ofName("reporter").description("User who muted this test/problem.")  //todo: review naming?
                                                    .syntax(Syntax.forLocator(LocatorName.USER)).build();
  public static final Dimension TYPE = Dimension.ofName("type").description("What is muted.")
                                                .syntax(EnumValue.of(ProblemTarget.getKnownTypesForMute())).build();
  public static final Dimension RESOLUTION = Dimension.ofName("resolution").description("Unmute condition.")
                                                      .syntax(EnumValue.of(Resolution.ResolutionType.class)).build();
  public static final Dimension TEST = Dimension.ofName("test").description("test for which mute is assigned")
                                                .syntax(Syntax.forLocator(LocatorName.TEST)).build();
  public static final Dimension PROBLEM = Dimension.ofName("problem").description("Problem for which mute is assigned.")
                                                   .syntax(Syntax.forLocator(LocatorName.PROBLEM)).build();

  //public static final Dimension BUILD_TYPE = Dimension.ofName("buildType"); //todo: add assignmentBuildType
}
