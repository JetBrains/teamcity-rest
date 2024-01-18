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

package jetbrains.buildServer.server.graphql.model.mutation.agentPool;

import java.util.List;
import org.jetbrains.annotations.NotNull;

public class BulkAssignProjectWithAgentPoolInput {
  @NotNull
  private List<String> myProjectRawIds;
  private int myAgentPoolRawId;
  private boolean myExclusively;

  public void setProjectRawIds(@NotNull List<String> projectRawIds) {
    myProjectRawIds = projectRawIds;
  }

  public void setAgentPoolRawId(int agentPoolRawId) {
    myAgentPoolRawId = agentPoolRawId;
  }

  public void setExclusively(boolean exclusively) {myExclusively = exclusively; }

  @NotNull
  public List<String> getProjectRawIds() {
    return myProjectRawIds;
  }

  public int getAgentPoolRawId() {
    return myAgentPoolRawId;
  }

  public boolean getExclusively() { return myExclusively; }
}