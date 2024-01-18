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

import java.util.Arrays;
import java.util.stream.Collectors;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.server.rest.data.locator.*;
import jetbrains.buildServer.server.rest.data.locator.definition.LocatorDefinition;

public class ValueCondition implements LocatorDefinition {
  public static final Dimension SINGLE_VALUE = CommonLocatorDimensions.SINGLE_VALUE("Value for 'equals' match.");
  public static final Dimension VALUE = Dimension.ofName("value").syntax(PlainValue.string()).build();
  public static final Dimension MATCH_TYPE = Dimension.ofName("matchType")
                                                      .syntax(
                                                        EnumValue.of(Arrays.stream(RequirementType.values()).map(RequirementType::getName).collect(Collectors.toList()))
                                                      ).build();
  public static final Dimension IGNORE_CASE = Dimension.ofName("ignoreCase").syntax(new BooleanValue()).build();
}
