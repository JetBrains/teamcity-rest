/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.request.*;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.vcs.VcsModification;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 14.11.2009
 */
public class ApiUrlBuilder {
  private PathTransformer myPathTransformer;

  public ApiUrlBuilder(@NotNull final PathTransformer pathTransformer) {
    myPathTransformer = pathTransformer;
  }

  public String getHref(@NotNull final SBuildAgent agent) {
    return myPathTransformer.transform(AgentRequest.getAgentHref(agent));
  }


  public String getHref(@NotNull final SBuild build) {
    return myPathTransformer.transform(BuildRequest.getBuildHref(build));
  }

  public String getHref(@NotNull final BuildTypeOrTemplate buildType) {
    return myPathTransformer.transform(BuildTypeRequest.getBuildTypeHref(buildType));
  }

  public String getBuildsHref(final SBuildType buildType) {
    return myPathTransformer.transform(BuildTypeRequest.getBuildsHref(buildType));
  }

  public String getHref(final VcsModification modification) {
    return myPathTransformer.transform(ChangeRequest.getChangeHref(modification));
  }

  public String getBuildChangesHref(final SBuild build) {
    return myPathTransformer.transform(ChangeRequest.getBuildChangesHref(build));
  }

  public String getHref(final UserGroup userGroup) {
    return myPathTransformer.transform(GroupRequest.getGroupHref(userGroup));
  }

  public String getHref(final RoleEntry roleEntry, final UserGroup group) {
    return myPathTransformer.transform(GroupRequest.getRoleAssignmentHref(roleEntry, group));
  }

  public String getHref(final RoleEntry roleEntry, final SUser user) {
    return myPathTransformer.transform(UserRequest.getRoleAssignmentHref(roleEntry, user));
  }

  public String getHref(final SProject project) {
    return myPathTransformer.transform(ProjectRequest.getProjectHref(project));
  }

  public String getHref(final User user) {
    return myPathTransformer.transform(UserRequest.getUserHref(user));
  }

  public String getHref(final VcsRoot root) {
    return myPathTransformer.transform(VcsRootRequest.getVcsRootHref(root));
  }

  public String getHref(final VcsRootInstance root) {
    return myPathTransformer.transform(VcsRootRequest.getVcsRootInstanceHref(root));
  }

  public String getGlobalWadlHref() {
    return myPathTransformer.transform( Constants.API_URL + Constants.EXTERNAL_APPLICATION_WADL_NAME);
  }

  public String transformRelativePath(final String internalRelativePath) {
    return myPathTransformer.transform(internalRelativePath);
  }
}