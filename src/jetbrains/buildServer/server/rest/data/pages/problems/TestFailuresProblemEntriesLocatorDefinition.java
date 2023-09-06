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

package jetbrains.buildServer.server.rest.data.pages.problems;

import jetbrains.buildServer.server.rest.data.locator.BooleanValue;
import jetbrains.buildServer.server.rest.data.locator.Dimension;
import jetbrains.buildServer.server.rest.data.locator.Syntax;
import jetbrains.buildServer.server.rest.data.locator.definition.LocatorDefinition;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;

import static jetbrains.buildServer.server.rest.data.pages.problems.TestFailuresProblemEntriesCollector.SUPPORTED_ORDERS;

public class TestFailuresProblemEntriesLocatorDefinition implements LocatorDefinition {
  public static final Dimension AFFECTED_PROJECT = Dimension.ofName("affectedProject")
                                                            .syntax(Syntax.forLocator(LocatorName.PROJECT)).build();
  public static final Dimension CURRENTLY_INVESTIGATED = Dimension.ofName("currentlyInvestigated")
                                                                  .syntax(new BooleanValue()).build();
  public static final Dimension CURRENTLY_MUTED = Dimension.ofName("currentlyMuted")
                                                           .syntax(new BooleanValue()).build();
  public static final Dimension CURRENTLY_FAILING = Dimension.ofName("currentlyFailing")
                                                             .syntax(new BooleanValue()).build();
  public static final Dimension ASSIGNEE = Dimension.ofName("assignee")
                                                    .syntax(Syntax.forLocator(LocatorName.USER)).build();
  public static final Dimension BUILD_TYPE = Dimension.ofName("buildType")
                                                      .syntax(Syntax.forLocator(LocatorName.BUILD_TYPE)).build();
  public static final Dimension ORDER_BY = Dimension.ofName("orderBy")
                                                    .syntax(SUPPORTED_ORDERS.getSyntax()).build();
}
