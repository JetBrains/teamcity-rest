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

package jetbrains.buildServer.server.graphql.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.server.graphql.model.connections.ProjectsConnection;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class ParentsFetcher {
  @NotNull
  public static ProjectsConnection getAncestors(@Nullable SProject self, boolean includeSelf) {
    if(self == null) {
      return new ProjectsConnection(Collections.emptyList());
    }

    List<SProject> reversedAncestors = self.getProjectPath();
    List<SProject> result = new ArrayList<>();

    int firstIdx = includeSelf ? reversedAncestors.size() - 1 : reversedAncestors.size() - 2;
    for(int i = firstIdx; i >= 0; i--)
      result.add(reversedAncestors.get(i));

    return new ProjectsConnection(result);
  }
}
