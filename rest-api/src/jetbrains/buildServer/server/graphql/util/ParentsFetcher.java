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

package jetbrains.buildServer.server.graphql.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class ParentsFetcher {
  @NotNull
  public static List<SProject> getAncestors(@Nullable SProject self) {
    if(self == null) {
      return Collections.emptyList();
    }

    List<SProject> reversedAncestors = self.getProjectPath();
    List<SProject> result = new ArrayList<>();

    // Skip self
    for(int i = reversedAncestors.size() - 2; i >= 0; i--)
      result.add(reversedAncestors.get(i));

    return result;
  }

  @NotNull
  public static List<SProject> getAncestors(@Nullable SBuildType self) {
    if(self == null) {
      return Collections.emptyList();
    }

    List<SProject> reversedAncestors = self.getProject().getProjectPath();
    List<SProject> result = new ArrayList<>();

    for(int i = reversedAncestors.size() - 1; i >= 0; i--)
      result.add(reversedAncestors.get(i));

    return result;
  }
}