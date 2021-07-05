/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.server.graphql.model.agentPool;

public class AgentPoolPermissions {
  private final boolean myAuthorizeAgents;
  private final boolean myManageProjects;
  private final boolean myEnableAgents;
  private final boolean myManageAgents;

  public AgentPoolPermissions(boolean authorizeAgents, boolean manageProjects, boolean enableAgents, boolean manageAgents) {
    myAuthorizeAgents = authorizeAgents;
    myManageProjects = manageProjects;
    myEnableAgents = enableAgents;
    myManageAgents = manageAgents;
  }

  public boolean isAuthorizeAgents() {
    return myAuthorizeAgents;
  }

  public boolean isManageProjects() {
    return myManageProjects;
  }

  @Deprecated
  public boolean isManage() {
    return isManageProjects();
  }

  public boolean isEnableAgents() {
    return myEnableAgents;
  }

  public boolean isManageAgents() {
    return myManageAgents;
  }
}
