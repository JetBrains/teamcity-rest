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

package jetbrains.buildServer.server.graphql.resolver;

import graphql.execution.DataFetcherResult;
import java.util.Arrays;
import java.util.Collections;
import jetbrains.buildServer.server.graphql.model.mutation.AssignProjectWithAgentPoolPayload;
import jetbrains.buildServer.server.graphql.model.mutation.UnassignProjectFromAgentPoolInput;
import jetbrains.buildServer.server.graphql.model.mutation.UnassignProjectFromAgentPoolPayload;
import jetbrains.buildServer.server.graphql.model.mutation.agentPool.*;
import jetbrains.buildServer.server.graphql.resolver.agentPool.AgentPoolMutation;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolCannotBeRenamedException;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolLimitsImpl;
import jetbrains.buildServer.serverSide.agentPools.NoSuchAgentPoolException;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class AgentPoolMutationTest extends BaseResolverTest {

  private AgentPoolMutation myMutation;
  private AgentPoolAccessCheckerForTests myChecker;

  @Override
  @BeforeMethod(alwaysRun = true)
  public void setUp() throws Exception {
    super.setUp();

    myChecker = new AgentPoolAccessCheckerForTests();
    myMutation = new AgentPoolMutation(myFixture.getAgentPoolManager(), myFixture.getProjectManager(), myChecker);
  }

  @Test
  public void testCreatePool() {
    final String name = "myTestPool";
    final int maxAgents = 2;
    CreateAgentPoolInput input = new CreateAgentPoolInput();
    input.setMaxAgents(2);
    input.setName(name);


    DataFetcherResult<CreateAgentPoolPayload> result = myMutation.createAgentPool(input);


    assertFalse(result.hasErrors());

    CreateAgentPoolPayload payload = result.getData();
    assertNotNull(payload);
    assertNotNull(payload.getAgentPool());

    assertEquals(name, payload.getAgentPool().getName());
    assertEquals(maxAgents, payload.getAgentPool().getMaxAgentsNumber());

    AgentPool createdPool = myFixture.getAgentPoolManager().findAgentPoolById(payload.getAgentPool().getId());
    assertNotNull(createdPool);
    assertEquals(createdPool.getName(), payload.getAgentPool().getName());
    assertEquals(createdPool.getMaxAgents(), payload.getAgentPool().getMaxAgentsNumber());
  }

  @Test
  public void testCreatePoolDefaultsToUnlimitedAgents() {
    final String name = "myTestPool";
    CreateAgentPoolInput input = new CreateAgentPoolInput();
    input.setName(name);

    DataFetcherResult<CreateAgentPoolPayload> result = myMutation.createAgentPool(input);


    AgentPool createdPool = myFixture.getAgentPoolManager().findAgentPoolById(result.getData().getAgentPool().getId());

    assertEquals(createdPool.getMaxAgents(), result.getData().getAgentPool().getMaxAgentsNumber());
  }

  @Test
  public void testRemovePool() throws AgentPoolCannotBeRenamedException {
    final String name = "myTestPool";
    AgentPool poolToDelete = myFixture.getAgentPoolManager().createNewAgentPool(name);

    RemoveAgentPoolInput input = new RemoveAgentPoolInput();
    input.setAgentPoolId(poolToDelete.getAgentPoolId());

    DataFetcherResult<RemoveAgentPoolPayload> result = myMutation.removeAgentPool(input);

    assertFalse(result.hasErrors());

    RemoveAgentPoolPayload payload = result.getData();
    assertNotNull(payload);
    assertNotNull(payload.getAgentPool());
    assertNull(myFixture.getAgentPoolManager().findAgentPoolById(payload.getAgentPool().getId()));
  }

  @Test
  public void testUpdatePool() throws AgentPoolCannotBeRenamedException {
    final String name1 = "myTestPool";
    final String name2 = "myTestPoolRenamed";
    final int maxAgents1 = 2;
    final int maxAgents2 = 3;
    AgentPool poolToUpdate = myFixture.getAgentPoolManager().createNewAgentPool(name1, new AgentPoolLimitsImpl(0, maxAgents1));

    UpdateAgentPoolInput input = new UpdateAgentPoolInput();
    input.setId(poolToUpdate.getAgentPoolId());
    input.setName(name2);
    input.setMaxAgents(maxAgents2);

    DataFetcherResult<UpdateAgentPoolPayload> result = myMutation.updateAgentPool(input);

    assertFalse(result.hasErrors());

    UpdateAgentPoolPayload payload = result.getData();
    assertNotNull(payload);
    assertNotNull(payload.getAgentPool());

    AgentPool updatedPool = myFixture.getAgentPoolManager().findAgentPoolById(poolToUpdate.getAgentPoolId());
    assertEquals(maxAgents2, updatedPool.getMaxAgents());
    assertEquals(name2, updatedPool.getName());
  }

  @Test
  public void testAddProjectToPool() throws AgentPoolCannotBeRenamedException {
    final String poolName = "testPool";
    final String projectName = "testProject";
    AgentPool pool = myFixture.getAgentPoolManager().createNewAgentPool(poolName);

    ProjectEx project = createProject(projectName);

    AssignProjectWithAgentPoolInput input = new AssignProjectWithAgentPoolInput();
    input.setAgentPoolId(pool.getAgentPoolId());
    input.setProjectId(project.getExternalId());

    DataFetcherResult<AssignProjectWithAgentPoolPayload> result = myMutation.assignProjectWithAgentPool(input);
    assertNotNull(result);
    assertFalse(result.hasErrors());
    assertNotNull(result.getData());

    AssignProjectWithAgentPoolPayload payload = result.getData();

    assertEquals(poolName, payload.getAgentPool().getName());
    assertEquals(projectName, payload.getProject().getName());

    assertContains(myFixture.getAgentPoolManager().getPoolProjects(pool.getAgentPoolId()), project.getProjectId());
  }

  @Test
  public void testRemoveProjectFromPool() throws AgentPoolCannotBeRenamedException, NoSuchAgentPoolException {
    final String poolName = "testPool";
    final String projectName = "testProject";
    AgentPool pool = myFixture.getAgentPoolManager().createNewAgentPool(poolName);

    ProjectEx project = createProject(projectName);

    myFixture.getAgentPoolManager().associateProjectsWithPool(pool.getAgentPoolId(), Collections.singleton(project.getProjectId()));

    UnassignProjectFromAgentPoolInput input = new UnassignProjectFromAgentPoolInput();
    input.setAgentPoolId(pool.getAgentPoolId());
    input.setProjectId(project.getExternalId());

    DataFetcherResult<UnassignProjectFromAgentPoolPayload> result = myMutation.unassignProjectFromAgentPool(input);
    assertNotNull(result);
    assertFalse(result.hasErrors());
    assertNotNull(result.getData());

    UnassignProjectFromAgentPoolPayload payload = result.getData();

    assertEquals(poolName, payload.getAgentPool().getName());
    assertEquals(projectName, payload.getProject().getName());

    assertNotContains(myFixture.getAgentPoolManager().getPoolProjects(pool.getAgentPoolId()), project.getProjectId());
  }

  @Test
  public void testBulkAddProjectsToPool() throws AgentPoolCannotBeRenamedException {
    final String poolName = "testPool";
    final String projectName1 = "testProject1";
    final String projectName2 = "testProject2";
    AgentPool pool = myFixture.getAgentPoolManager().createNewAgentPool(poolName);

    ProjectEx project1 = createProject(projectName1);
    ProjectEx project2 = createProject(projectName2);

    BulkAssignProjectWithAgentPoolInput input = new BulkAssignProjectWithAgentPoolInput();
    input.setAgentPoolId(pool.getAgentPoolId());
    input.setProjectIds(Arrays.asList(project1.getExternalId(), project2.getExternalId()));
    input.setExclusively(false);

    DataFetcherResult<BulkAssignProjectWithAgentPoolPayload> result = myMutation.bulkAssignProjectWithAgentPool(input);
    assertNotNull(result);
    assertFalse(result.hasErrors());
    assertNotNull(result.getData());

    BulkAssignProjectWithAgentPoolPayload payload = result.getData();
    assertEquals(poolName, payload.getAgentPool().getName());

    assertContains(myFixture.getAgentPoolManager().getPoolProjects(pool.getAgentPoolId()), project1.getProjectId(), project2.getProjectId());

    // Projects should be associated with default pool when exclusively == false
    assertContains(myFixture.getAgentPoolManager().getPoolProjects(AgentPool.DEFAULT_POOL_ID), project1.getProjectId(), project2.getProjectId());
  }

  @Test
  public void testBulkAddProjectsToPoolExclusively() throws AgentPoolCannotBeRenamedException {
    final String poolName = "testPool";
    final String projectName1 = "testProject1";
    final String projectName2 = "testProject2";
    AgentPool pool = myFixture.getAgentPoolManager().createNewAgentPool(poolName);

    ProjectEx project1 = createProject(projectName1);
    ProjectEx project2 = createProject(projectName2);

    BulkAssignProjectWithAgentPoolInput input = new BulkAssignProjectWithAgentPoolInput();
    input.setAgentPoolId(pool.getAgentPoolId());
    input.setProjectIds(Arrays.asList(project1.getExternalId(), project2.getExternalId()));
    input.setExclusively(true);

    DataFetcherResult<BulkAssignProjectWithAgentPoolPayload> result = myMutation.bulkAssignProjectWithAgentPool(input);
    assertNotNull(result);
    assertFalse(result.hasErrors());
    assertNotNull(result.getData());

    BulkAssignProjectWithAgentPoolPayload payload = result.getData();
    assertEquals(poolName, payload.getAgentPool().getName());

    assertContains(myFixture.getAgentPoolManager().getPoolProjects(pool.getAgentPoolId()), project1.getProjectId(), project2.getProjectId());

    // check that no other pool has association with said projects
    assertEquals(1, myFixture.getAgentPoolManager().getAgentPoolsWithProject(project1.getProjectId()).size());
    assertEquals(1, myFixture.getAgentPoolManager().getAgentPoolsWithProject(project2.getProjectId()).size());
  }
}
