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

package jetbrains.buildServer.server.graphql.model.buildType.incompatibility;

import org.jetbrains.annotations.Nullable;

public class UndefinedRunParameterAgentBuildTypeIncompatibility implements AgentBuildTypeIncompatibility {
  @Nullable
  private String myName;

  @Nullable
  private String myOrigin;

  public UndefinedRunParameterAgentBuildTypeIncompatibility() {
  }

  public UndefinedRunParameterAgentBuildTypeIncompatibility(@Nullable String name, @Nullable String origin) {
    myName = name;
    myOrigin = origin;
  }

  @Nullable
  public String getName() {
    return myName;
  }

  public void setName(@Nullable String name) {
    myName = name;
  }

  @Nullable
  public String getOrigin() {
    return myOrigin;
  }

  public void setOrigin(@Nullable String origin) {
    myOrigin = origin;
  }
}
