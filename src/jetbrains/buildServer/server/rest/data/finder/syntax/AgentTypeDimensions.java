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

import jetbrains.buildServer.server.rest.data.locator.Dimension;
import jetbrains.buildServer.server.rest.data.locator.PlainValue;
import jetbrains.buildServer.server.rest.data.locator.Syntax;
import jetbrains.buildServer.server.rest.data.locator.definition.FinderLocatorDefinition;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;

@LocatorResource(value = LocatorName.AGENT_TYPE, baseEntity = "AgentType")
public class AgentTypeDimensions implements FinderLocatorDefinition {
  public static final Dimension SINGLE_VALUE = CommonLocatorDimensions.SINGLE_VALUE("id of the AgentType.");

  public static final Dimension ID = Dimension.ofName("id").description("id of the AgentType.")
                                              .syntax(PlainValue.int64()).build();

  public static final Dimension AGENT = Dimension.ofName("agent").description("AgentType of the given agent.")
                                                 .syntax(Syntax.forLocator(LocatorName.AGENT)).build();

}
