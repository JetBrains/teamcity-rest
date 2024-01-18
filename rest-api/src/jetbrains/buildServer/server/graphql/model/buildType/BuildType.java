/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

package jetbrains.buildServer.server.graphql.model.buildType;

import jetbrains.buildServer.server.graphql.util.ObjectIdentificationNode;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;

public class BuildType implements ObjectIdentificationNode {
  @NotNull
  private final String myId;
  @NotNull
  private final String myName;
  @NotNull
  private final BuildTypeType myType;

  public BuildType(@NotNull SBuildType buildType) {
    myId = buildType.getExternalId();
    myName = buildType.getName();

    if(buildType.isCompositeBuildType()) {
      myType = BuildTypeType.COMPOSITE;
    } else if(buildType.isDeployment()) {
      myType = BuildTypeType.DEPLOYMENT;
    } else {
      myType = BuildTypeType.REGULAR;
    }
  }

  public BuildType(@NotNull String id, @NotNull String name, @NotNull BuildTypeType type) {
    myId = id;
    myName = name;
    myType = type;
  }

  @NotNull
  public String getRawId() {
    return myId;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public BuildTypeType getType() {
    return myType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BuildType buildType = (BuildType)o;

    if (!myId.equals(buildType.myId)) return false;
    return myName.equals(buildType.myName);
  }

  @Override
  public int hashCode() {
    int result = myId.hashCode();
    result = 31 * result + myName.hashCode();
    return result;
  }
}