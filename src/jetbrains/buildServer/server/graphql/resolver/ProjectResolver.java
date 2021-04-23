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

package jetbrains.buildServer.server.graphql.resolver;

import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.graphql.GraphQLContext;
import jetbrains.buildServer.server.graphql.model.AgentPool;
import jetbrains.buildServer.server.graphql.model.ProjectPermissions;
import jetbrains.buildServer.server.graphql.model.connections.*;
import jetbrains.buildServer.server.graphql.model.Project;
import jetbrains.buildServer.server.graphql.model.filter.ProjectsFilter;
import jetbrains.buildServer.server.graphql.util.ParentsFetcher;
import jetbrains.buildServer.server.rest.data.AgentPoolFinder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.Permissions;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class ProjectResolver implements GraphQLResolver<Project> {
  @NotNull
  private final ProjectFinder myProjectFinder;
  @NotNull
  private final AgentPoolFinder myPoolFinder;

  public ProjectResolver(@NotNull ProjectFinder projectFinder, @NotNull AgentPoolFinder poolFinder) {
    myProjectFinder = projectFinder;
    myPoolFinder = poolFinder;
  }

  @NotNull
  public BuildTypesConnection buildTypes(@NotNull Project source, @NotNull DataFetchingEnvironment env) {
    SProject self = env.getLocalContext();

    if(self == null) {
      self = myProjectFinder.findSingleItem(Locator.locator("id:" + source.getId()));
      if(self == null) {
        throw new BadRequestException("Malformed source project id");
      }
    }

    return new BuildTypesConnection(self.getBuildTypes());
  }

  @NotNull
  public ProjectsConnection ancestorProjects(@NotNull Project source, @Nullable ProjectsFilter filter, @NotNull DataFetchingEnvironment env) throws Exception {
    // TODO: get it from local context
    SProject self = myProjectFinder.findSingleItem(Locator.locator("id:" + source.getId()));
    if(self == null) {
      throw new BadRequestException("Malformed source project id");
    }

    // TODO: apply filter
    return ParentsFetcher.getAncestors(self, false);
  }

  @NotNull
  public ProjectPermissions permissions(@NotNull Project source, @NotNull DataFetchingEnvironment env) {
    GraphQLContext ctx = env.getContext();

    SUser user = ctx.getUser();
    if(user == null) {
      return new ProjectPermissions(false);
    }

    Permissions permissions = user.getPermissionsGrantedForProject(source.getId());

    return new ProjectPermissions(
      permissions.contains(Permission.MANAGE_AGENT_POOLS_FOR_PROJECT)
    );
  }

  @NotNull
  public ProjectAgentPoolsConnection agentPools(@NotNull Project source, @NotNull DataFetchingEnvironment env) {
    // TODO: get it from local context
    SProject self = myProjectFinder.findSingleItem(Locator.locator("id:" + source.getId()));

    List<AgentPool> pools;
    if(self != null) {
      pools = myPoolFinder.getPoolsForProject(self).stream()
                          .map(p -> new AgentPool(p))
                          .collect(Collectors.toList());
    } else {
      pools = new ArrayList<>();
    }

    return new ProjectAgentPoolsConnectionBuilder(pools).get(env);
  }
}
