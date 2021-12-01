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

package jetbrains.buildServer.server.graphql.resolver.agentPool;

import graphql.schema.DataFetchingEnvironment;
import jetbrains.buildServer.server.graphql.model.Project;
import jetbrains.buildServer.server.graphql.model.agentPool.AgentPoolPermissions;
import jetbrains.buildServer.server.graphql.model.agentPool.ProjectAgentPool;
import jetbrains.buildServer.server.graphql.model.agentPool.actions.AgentPoolActionStatus;
import jetbrains.buildServer.server.graphql.model.agentPool.actions.ProjectAgentPoolActions;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolAgentsConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolCloudImagesConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolProjectsConnection;
import jetbrains.buildServer.server.graphql.model.filter.ProjectsFilter;
import jetbrains.buildServer.server.graphql.util.ModelResolver;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentPools.ProjectAgentPoolImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class ProjectAgentPoolResolver extends ModelResolver<ProjectAgentPool> {
  private final AbstractAgentPoolResolver myDelegate;
  private final ProjectManager myProjectManager;

  public ProjectAgentPoolResolver(@NotNull AbstractAgentPoolResolver delegate, @NotNull ProjectManager projectManager) {
    myDelegate = delegate;
    myProjectManager = projectManager;
  }

  @NotNull
  public AgentPoolAgentsConnection agents(@NotNull ProjectAgentPool pool, @NotNull DataFetchingEnvironment env) {
    return myDelegate.agents(pool, env);
  }

  @NotNull
  public AgentPoolProjectsConnection projects(@NotNull ProjectAgentPool pool, @NotNull ProjectsFilter filter, @NotNull DataFetchingEnvironment env) {
    return myDelegate.projects(pool, filter, env);
  }

  @NotNull
  public AgentPoolPermissions permissions(@NotNull ProjectAgentPool pool, @NotNull DataFetchingEnvironment env) {
    return myDelegate.permissions(pool, env);
  }

  @NotNull
  public AgentPoolCloudImagesConnection cloudImages(@NotNull ProjectAgentPool pool, @NotNull DataFetchingEnvironment env) {
    return myDelegate.cloudImages(pool, env);
  }

  @NotNull
  public Project project(@NotNull ProjectAgentPool pool, @NotNull DataFetchingEnvironment env) {
    AgentPool realPool = pool.getRealPool();

    if(!realPool.isProjectPool()) {
      throw new RuntimeException(String.format("Pool id=%d is not a project pool.", realPool.getAgentPoolId()));
    }

    String projectId = ((ProjectAgentPoolImpl) realPool).getProjectId();
    SProject project = myProjectManager.findProjectById(projectId);
    if(project == null) {
      throw new RuntimeException(String.format("ProjectAgentPool id=%d does not have a project.", realPool.getAgentPoolId()));
    }

    return new Project(project);
  }

  @NotNull
  public ProjectAgentPoolActions actions(@NotNull ProjectAgentPool pool) {
    return new ProjectAgentPoolActions(AgentPoolActionStatus.unavailable(null));
  }

  @Override
  public String getIdPrefix() {
    return ProjectAgentPool.class.getSimpleName();
  }

  @Override
  public ProjectAgentPool findById(String id) {
    return null;
  }
}
