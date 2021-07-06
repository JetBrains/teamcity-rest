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

import com.intellij.openapi.diagnostic.Logger;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.kickstart.tools.GraphQLMutationResolver;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.graphql.model.AgentRunPolicy;
import jetbrains.buildServer.server.graphql.model.mutation.*;
import jetbrains.buildServer.server.graphql.util.TeamCityGraphQLErrorType;
import jetbrains.buildServer.server.rest.data.AgentFinder;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeManager;
import jetbrains.buildServer.util.Action;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Mutation implements GraphQLMutationResolver {
  private static final Logger LOG = Logger.getInstance(Mutation.class.getName());

  @Autowired
  @NotNull
  private AgentFinder myAgentFinder;

  @Autowired
  @NotNull
  private ProjectFinder myProjectFinder;

  @Autowired
  @NotNull
  private BuildTypeFinder myBuildTypeFinder;

  @Autowired
  @NotNull
  private AgentTypeManager myAgentTypeManager;

  @NotNull
  public CreateAgentPoolPayload createAgentPool(@NotNull CreateAgentPoolInput input) {
    return null;
  }

  @NotNull
  public UpdateAgentPoolPayload updateAgentPool(@NotNull UpdateAgentPoolInput input) {
    return null;
  }

  @NotNull
  public RemoveAgentPoolPayload removeAgentPool(@NotNull RemoveAgentPoolInput input) {
    return null;
  }

  @NotNull
  public SetAgentRunPolicyPayload setAgentRunPolicy(@NotNull SetAgentRunPolicyInput input) {
    /*return performSafe(
      input.getAgentId(),
      (agent) -> {
        BuildAgentManager.RunConfigurationPolicy policy = input.getAgentRunPolicy() == AgentRunPolicy.ALL ?
                                                          BuildAgentManager.RunConfigurationPolicy.ALL_COMPATIBLE_CONFIGURATIONS :
                                                          BuildAgentManager.RunConfigurationPolicy.SELECTED_COMPATIBLE_CONFIGURATIONS;

        myAgentTypeManager.setRunConfigurationPolicy(agent.getAgentTypeId(), policy);
      },
      "Exception while setting agent run scope."
    );
     */

    return null;
  }

  @NotNull
  public MoveAgentToAgentPoolPayload moveAgentToAgentPool(@NotNull MoveAgentToAgentPoolInput input) {
    return null;
  }

  @NotNull
  public MoveCloudImageToAgentPoolPayload moveCloudImageToAgentPool(@NotNull MoveCloudImageToAgentPoolInput input) {
    return null;
  }

  @NotNull
  public AssignProjectWithAgentPoolPayload assignProjectWithAgentPool(@NotNull AssignProjectWithAgentPoolInput input) {
    return null;
  }

  @NotNull
  public UnassignProjectFromAgentPoolPayload unassignProjectFromAgentPool(@NotNull UnassignProjectFromAgentPoolInput input) {
    return null;
  }

  @NotNull
  public AssignBuildTypeWithAgentPayload assignBuildTypeWithAgent(@NotNull AssignBuildTypeWithAgentInput input) {
    return null;
    /*
    return performSafe(
      input.getAgentId(),
      (agent) -> {
        SBuildType bt = myBuildTypeFinder.getItem("id:" + input.getBuildTypeId()).getBuildType();
        myAgentTypeManager.includeRunConfigurationsToAllowed(agent.getAgentTypeId(), new String[] { bt.getInternalId() });
      },
      "Exception while assigning build type to an agent."
    );
    */
  }

  @NotNull
  public UnassignBuildTypeFromAgentPayload unassignBuildTypeFromAgent(@NotNull UnassignBuildTypeFromAgentInput input) {
    /*
    return performSafe(
      input.getAgentId(),
      (agent) -> {
        SBuildType bt = myBuildTypeFinder.getItem("id:" + input.getBuildTypeId()).getBuildType();
        myAgentTypeManager.excludeRunConfigurationsFromAllowed(agent.getAgentTypeId(), new String[]{bt.getInternalId()});
      },
      "Exception while unassigning build type from an agent."
    );
     */

    return null;
  }

  @NotNull
  public AssignProjectBuildTypesWithAgentPayload assignProjectBuildTypesWithAgent(@NotNull AssignProjectBuildTypesWithAgentInput input) {
    /*
    return performSafe(
      input.getAgentId(),
      (agent) -> {
        SProject project = myProjectFinder.getItem("id:" + input.getProjectId());
        String[] bts = project.getBuildTypes().stream().map(bt -> bt.getInternalId()).collect(Collectors.toSet()).toArray(new String[0]);
        myAgentTypeManager.includeRunConfigurationsToAllowed(agent.getAgentTypeId(), bts);
      },
      "Exception while assigning build type to an agent."
    );

     */
    return null;
  }

  @NotNull
  public UnassignProjectBuildTypesFromAgentPayload unassignProjectBuildTypesFromAgent(@NotNull UnassignProjectBuildTypesFromAgentInput input) {
    return null;
    /*
    return performSafe(
      input.getAgentId(),
      (agent) -> {
        SProject project = myProjectFinder.getItem("id:" + input.getProjectId());
        List<String> bts = project.getBuildTypes().stream().map(bt -> bt.getInternalId()).collect(Collectors.toList());
        myAgentTypeManager.excludeRunConfigurationsFromAllowed(agent.getAgentTypeId(), bts.toArray(new String[0]));
      },
      "Exception while unassigning build type from an agent."
    );

     */
  }

  @NotNull
  public UnassignAllAgentBuildTypesPayload unassignAllAgentBuildTypes(@NotNull UnassignAllAgentBuildTypesInput input) {
    /*
    return performSafe(
      input.getAgentId(),
      (agent) -> {
        Set<String> assignedBuildTypes = AgentFinder.getAssignedBuildTypes(agent);

        myAgentTypeManager.excludeBuildTypesFromAllowed(agent.getAgentTypeId(), assignedBuildTypes);
      },
      "Exception while unassigning all build types from an agent."
    );*/
    return null;
  }

  @NotNull
  private DataFetcherResult<Boolean> performSafe(@NotNull String agentId, @NotNull Action<SBuildAgent> action, @NotNull String exceptionMessage) {
    SBuildAgent agent = myAgentFinder.findSingleItem(Locator.locator("id:" + agentId));

    if(agent == null) {
      throw new NotFoundException(String.format("Agent with id=%s does not exist", agentId));
    }

    action.apply(agent);
    return DataFetcherResult.<Boolean>newResult().data(true).build();
  }
}
