/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest;

import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.server.rest.model.agent.Agent;
import jetbrains.buildServer.server.rest.request.*;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 14.11.2009
 */
public class ApiUrlBuilder {
  private final PathTransformer myPathTransformer;

  public ApiUrlBuilder(@NotNull final PathTransformer pathTransformer) {
    myPathTransformer = pathTransformer;
  }

  @Nullable
  public String getHref(@NotNull final SBuildAgent agent) {
    if (agent.getId() == Agent.UNKNOWN_AGENT_ID) return null;
    return myPathTransformer.transform(AgentRequest.getAgentHref(agent));
  }

  public String getHref(@NotNull final AgentPool agent) {
    return myPathTransformer.transform(AgentPoolRequest.getAgentPoolHref(agent));
  }


  public String getHref(@NotNull final SBuild build) {
    return myPathTransformer.transform(BuildRequest.getBuildHref(build));
  }

  public String getHref(@NotNull final BuildPromotion build) {
    return myPathTransformer.transform(BuildRequest.getBuildHref(build));
  }

  public String getHref(@NotNull final SQueuedBuild build) {
    return myPathTransformer.transform(BuildQueueRequest.getQueuedBuildHref(build));
  }

  public String getHref(@NotNull final BuildTypeOrTemplate buildType) {
    return myPathTransformer.transform(BuildTypeRequest.getBuildTypeHref(buildType));
  }

  public String getBuildsHref(final SBuildType buildType) {
    return myPathTransformer.transform(BuildTypeRequest.getBuildsHref(buildType));
  }

  public String getHref(final SVcsModification modification) {
    return myPathTransformer.transform(ChangeRequest.getChangeHref(modification));
  }

  public String getBuildIssuesHref(final SBuild build) {
    return myPathTransformer.transform(BuildRequest.getBuildIssuesHref(build));
  }

  public String getHref(final UserGroup userGroup) {
    return myPathTransformer.transform(GroupRequest.getGroupHref(userGroup));
  }

  public String getHref(final RoleEntry roleEntry, final UserGroup group, @Nullable final String project) {
    return myPathTransformer.transform(GroupRequest.getRoleAssignmentHref(group, roleEntry, project));
  }

  public String getHref(final RoleEntry roleEntry, final SUser user, @Nullable final String scopeParam) {
    return myPathTransformer.transform(UserRequest.getRoleAssignmentHref(user, roleEntry, scopeParam));
  }

  public String getHref(final SProject project) {
    return myPathTransformer.transform(ProjectRequest.getProjectHref(project));
  }

  public String getHref(final User user) {
    return myPathTransformer.transform(UserRequest.getUserHref(user));
  }

  public String getHref(final SVcsRoot root) {
    return myPathTransformer.transform(VcsRootRequest.getVcsRootHref(root));
  }

  public String getHref(final VcsRootInstance root) {
    return myPathTransformer.transform(VcsRootInstanceRequest.getVcsRootInstanceHref(root));
  }

  public String getGlobalWadlHref() {
    return myPathTransformer.transform( Constants.API_URL + Constants.EXTERNAL_APPLICATION_WADL_NAME);
  }

  public String transformRelativePath(final String internalRelativePath) {
    return myPathTransformer.transform(internalRelativePath);
  }
}