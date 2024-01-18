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

package jetbrains.buildServer.server.graphql.model;

import jetbrains.buildServer.server.graphql.util.ObjectIdentificationNode;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;

public class Project implements ObjectIdentificationNode {
  @NotNull
  private final SProject myRealProject;

  public Project(@NotNull SProject project) {
    myRealProject = project;
  }

  @NotNull
  public String getRawId() {
    return myRealProject.getExternalId();
  }

  @NotNull
  public String getName() {
    return myRealProject.getName();
  }

  public boolean isArchived() {
    return myRealProject.isArchived();
  }

  public boolean isVirtual() { return myRealProject.isVirtual(); }

  @NotNull
  public SProject getRealProject() {
    return myRealProject;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myRealProject.equals(((Project)o).myRealProject);
  }

  @Override
  public int hashCode() {
    return myRealProject.hashCode();
  }

  @Override
  public String toString() {
    return "GQL Project: " + getRawId();
  }
}