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

package jetbrains.buildServer.server.graphql.model;

import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;

public class Project {
  @NotNull
  private final String myId;
  @NotNull
  private final String myName;
  private final boolean myArchived;

  public Project(@NotNull SProject project) {
    myId = project.getExternalId();
    myName = project.getName();
    myArchived = project.isArchived();
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public boolean isArchived() {
    return myArchived;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Project project = (Project)o;

    if (!myId.equals(project.myId)) return false;
    return myName.equals(project.myName);
  }

  @Override
  public int hashCode() {
    int result = myId.hashCode();
    result = 31 * result + myName.hashCode();
    return result;
  }
}
