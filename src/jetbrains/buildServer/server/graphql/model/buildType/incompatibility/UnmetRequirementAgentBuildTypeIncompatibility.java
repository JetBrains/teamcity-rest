/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.server.graphql.model.buildType.incompatibility;

import org.jetbrains.annotations.Nullable;

public class UnmetRequirementAgentBuildTypeIncompatibility implements AgentBuildTypeIncompatibility {
  @Nullable
  private String myPropertyName;

  @Nullable
  private String myPropertyValue;

  @Nullable
  private String myType;

  public UnmetRequirementAgentBuildTypeIncompatibility() {
  }

  public UnmetRequirementAgentBuildTypeIncompatibility(@Nullable String propertyName, @Nullable String propertyValue, @Nullable String type) {
    myPropertyName = propertyName;
    myPropertyValue = propertyValue;
    myType = type;
  }

  @Nullable
  public String getPropertyName() {
    return myPropertyName;
  }

  public void setPropertyName(@Nullable String propertyName) {
    myPropertyName = propertyName;
  }

  @Nullable
  public String getPropertyValue() {
    return myPropertyValue;
  }

  public void setPropertyValue(@Nullable String propertyValue) {
    myPropertyValue = propertyValue;
  }

  @Nullable
  public String getType() {
    return myType;
  }

  public void setType(@Nullable String type) {
    myType = type;
  }
}
