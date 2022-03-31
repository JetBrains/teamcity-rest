/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.DataFetchingFieldSelectionSetImpl;
import java.util.*;
import jetbrains.buildServer.clouds.server.CloudManager;
import jetbrains.buildServer.server.graphql.model.agentPool.AgentPool;
import jetbrains.buildServer.server.graphql.model.agentPool.AgentPoolPermissions;
import jetbrains.buildServer.server.graphql.model.connections.ProjectsConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolAgentsConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolProjectsConnection;
import jetbrains.buildServer.server.graphql.model.filter.ProjectsFilter;
import jetbrains.buildServer.server.graphql.resolver.agentPool.AbstractAgentPoolResolver;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.SimpleParameter;
import jetbrains.buildServer.serverSide.agentPools.*;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.Permissions;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.impl.MockAuthorityHolder;
import jetbrains.buildServer.serverSide.impl.MockBuildAgent;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.impl.auth.SecuredProjectManager;
import jetbrains.buildServer.serverSide.impl.projects.ProjectImpl;
import jetbrains.buildServer.users.SUser;
import org.jmock.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class AbstractAgentPoolResolverTest extends BaseResolverTest {
  private AbstractAgentPoolResolver myResolver;
  private AgentPoolAccessCheckerForTests myActionChecker;

  @Override
  @BeforeMethod(alwaysRun = true)
  public void setUp() throws Exception {
    super.setUp();

    myActionChecker = new AgentPoolAccessCheckerForTests();
    SecuredProjectManager projectManager = new SecuredProjectManager(myFixture.getSecurityContext());
    projectManager.setDelegate(myProjectManager);
    Mock cloudManagerMock = mock(CloudManager.class);

    myResolver = new AbstractAgentPoolResolver(
      projectManager,
      myActionChecker,
      (CloudManager) cloudManagerMock.proxy(), // not actually used in tests
      myFixture.getAgentTypeFinder(),
      myServer.getSecurityContext()
    );

    myFixture.getServerSettings().setPerProjectPermissionsEnabled(true);
  }

  public void basicProjectsConnection() throws Throwable {
    jetbrains.buildServer.serverSide.agentPools.AgentPool realPool = myFixture.getAgentPoolManager().createNewAgentPool("testAgentPool");

    Set<String> allProjectIds = new HashSet<>();
    Set<String> visibleProjectNames = new HashSet<>();

    MockAuthorityHolder mockUser = new MockAuthorityHolder();
    Permissions viewProjectPermissions = new Permissions(Permission.VIEW_PROJECT);
    for (int i = 0; i < 5; i++) {
      ProjectEx project = createProject("visibleProject" + i);
      mockUser.projectPerms.put(project.getProjectId(), viewProjectPermissions);

      allProjectIds.add(project.getProjectId());
      visibleProjectNames.add(project.getName());
    }

    List<ProjectEx> invisibleProjects = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      ProjectEx project = createProject("invisibleProject" + i);
      allProjectIds.add(project.getProjectId());
      invisibleProjects.add(project);
    }
    myFixture.getAgentPoolManager().associateProjectsWithPool(realPool.getAgentPoolId(), allProjectIds);


    Mock fieldSelectionSetMock = mock(DataFetchingFieldSelectionSet.class);
    fieldSelectionSetMock.stubs().method("contains").with(eq("excludedCount")).will(returnValue(true));
    myDataFetchingEnvironment.setSelectionSet((DataFetchingFieldSelectionSet) fieldSelectionSetMock.proxy());


    AgentPoolProjectsConnection connection = myFixture.getSecurityContext().runAs(
      mockUser,
      () -> myResolver.projects(new AgentPool(realPool), new ProjectsFilter(), myDataFetchingEnvironment)
    );

    connection.getEdges().getData().forEach(edge -> {
      String name = edge.getNode().getData().getName();
      assertTrue("Project '" + name + "' is visible, but shouldn't be.", visibleProjectNames.contains(name));
    });

    assertEquals(visibleProjectNames.size(), connection.getCount());
    assertEquals(new Integer(invisibleProjects.size()), connection.getExcludedCount());
  }

  public void projectsConnectionDefaultsToNonVirtualProjectsOnly() throws Exception {
    jetbrains.buildServer.serverSide.agentPools.AgentPool realPool = myFixture.getAgentPoolManager().createNewAgentPool("testAgentPool");

    SProject virtual = createProject("virtualProject");
    virtual.addParameter(new SimpleParameter(ProjectImpl.TEAMCITY_VIRTUAL_PROJECT_PARAM, "true"));
    SProject regular = createProject("regularProject");

    MockDataFetchingEnvironment dfe = new MockDataFetchingEnvironment();
    dfe.setSelectionSet((DataFetchingFieldSelectionSet)mock(DataFetchingFieldSelectionSet.class).proxy());

    myFixture.getAgentPoolManager().associateProjectsWithPool(realPool.getAgentPoolId(), Collections.singleton(virtual.getProjectId()));
    myFixture.getAgentPoolManager().associateProjectsWithPool(realPool.getAgentPoolId(), Collections.singleton(regular.getProjectId()));

    Mock fieldSelectionSetMock = mock(DataFetchingFieldSelectionSet.class);
    fieldSelectionSetMock.stubs().method("contains").with(eq("excludedCount")).will(returnValue(false));
    myDataFetchingEnvironment.setSelectionSet((DataFetchingFieldSelectionSet) fieldSelectionSetMock.proxy());

    AgentPoolProjectsConnection connection = myResolver.projects(new AgentPool(realPool), new ProjectsFilter(), myDataFetchingEnvironment);

    assertEquals(1, connection.getEdges().getData().size());
    assertEquals(regular.getExternalId(), connection.getEdges().getData().get(0).getNode().getData().getRawId());
  }


  public void basicAgentsConnection() throws AgentPoolCannotBeRenamedException, NoSuchAgentPoolException, AgentTypeCannotBeMovedException, PoolQuotaExceededException {
    AgentPoolManager manager = myFixture.getAgentPoolManager();
    jetbrains.buildServer.serverSide.agentPools.AgentPool evenAgents = manager.createNewAgentPool("evenAgents");
    jetbrains.buildServer.serverSide.agentPools.AgentPool oddAgents  = manager.createNewAgentPool("oddAgents");

    final int num = 2;
    for(int i = 0; i < num * 2; i++) {
      MockBuildAgent agent = myFixture.createEnabledAgent("agent_" + i);
      registerAndEnableAgent(agent);

      manager.moveAgentToPool(
        (i % 2 == 0) ? evenAgents.getAgentPoolId() : oddAgents.getAgentPoolId(),
        agent
      );
    }

    AgentPoolAgentsConnection evenConnection = myResolver.agents(new AgentPool(evenAgents), myDataFetchingEnvironment);
    assertEquals(num, evenConnection.getCount());
    for(AgentPoolAgentsConnection.AgentPoolAgentsConnectionEdge edge : evenConnection.getEdges().getData()) {
      assertEquals(evenAgents.getAgentPoolId(), edge.getNode().getData().getRealAgent().getAgentPoolId());
    }

    AgentPoolAgentsConnection oddConnection = myResolver.agents(new AgentPool(oddAgents), myDataFetchingEnvironment);
    assertEquals(num, oddConnection.getCount());
    for(AgentPoolAgentsConnection.AgentPoolAgentsConnectionEdge edge : oddConnection.getEdges().getData()) {
      assertEquals(oddAgents.getAgentPoolId(), edge.getNode().getData().getRealAgent().getAgentPoolId());
    }
  }

  public void agentPoolPermissionsAuthorizeAgents() throws Throwable {
    AgentPoolManager poolManager = myFixture.getAgentPoolManager();
    jetbrains.buildServer.serverSide.agentPools.AgentPool pool = poolManager.createNewAgentPool("testPool");
    ProjectEx project = createProject("testPrj");
    poolManager.associateProjectsWithPool(pool.getAgentPoolId(), Collections.singleton(project.getProjectId()));

    SUser user = createUser("testUser");
    assertFalse("Test precondition failed.", AuthUtil.hasPermissionToAuthorizeAgentsInPool(user, pool));

    AgentPool poolModel = new AgentPool(pool);

    myFixture.getSecurityContext().runAs(user, () -> {
      AgentPoolPermissions permissions = myResolver.permissions(poolModel, myDataFetchingEnvironment);
      assertFalse("User is not allowed to authorize agents in pool without explicit permission", permissions.isAuthorizeAgents());
    });


    user.addRole(RoleScope.globalScope(), getTestRoles().createRole(Permission.AUTHORIZE_AGENT));
    myFixture.getSecurityContext().runAs(user, () -> {
      AgentPoolPermissions permissions = myResolver.permissions(poolModel, myDataFetchingEnvironment);
      assertTrue("User is allowed to authorize agents in pool with global permission", permissions.isAuthorizeAgents());
    });

    user.removeRoles(RoleScope.globalScope());
    user.addRole(RoleScope.projectScope(project.getProjectId()), getTestRoles().createRole(Permission.AUTHORIZE_AGENT_FOR_PROJECT));
    myFixture.getSecurityContext().runAs(user, () -> {
      AgentPoolPermissions permissions = myResolver.permissions(poolModel, myDataFetchingEnvironment);
      assertTrue("User is allowed to authorize agents in pool with permission for each project", permissions.isAuthorizeAgents());
    });

    ProjectEx project2 = createProject("testPrj2");
    poolManager.associateProjectsWithPool(pool.getAgentPoolId(), Collections.singleton(project2.getProjectId()));
    myFixture.getSecurityContext().runAs(user, () -> {
      AgentPoolPermissions permissions = myResolver.permissions(poolModel, myDataFetchingEnvironment);
      assertFalse("User is not allowed to authorize agents in pool without permission for each project", permissions.isAuthorizeAgents());
    });
  }

  public void agentPoolPermissionsManagePoolSettings() throws Throwable {
    AgentPoolManager poolManager = myFixture.getAgentPoolManager();
    jetbrains.buildServer.serverSide.agentPools.AgentPool pool = poolManager.createNewAgentPool("testPool");

    SUser user = createUser("testUser");
    assertFalse("Test precondition failed.", AuthUtil.hasGlobalPermission(user, Permission.MANAGE_AGENT_POOLS));

    AgentPool poolModel = new AgentPool(pool);

    myFixture.getSecurityContext().runAs(user, () -> {
      AgentPoolPermissions permissions = myResolver.permissions(poolModel, myDataFetchingEnvironment);
      assertFalse("User is not allowed to manage pool settings without explicit permission", permissions.isManage());
    });

    user.addRole(RoleScope.globalScope(), getTestRoles().createRole(Permission.MANAGE_AGENT_POOLS));
    myFixture.getSecurityContext().runAs(user, () -> {
      AgentPoolPermissions permissions = myResolver.permissions(poolModel, myDataFetchingEnvironment);
      assertTrue("User is allowed to manage pool with global permission", permissions.isManage());
    });
  }

  public void agentPoolPermissionsEnableAgents() throws Throwable {
    AgentPoolManager poolManager = myFixture.getAgentPoolManager();
    jetbrains.buildServer.serverSide.agentPools.AgentPool pool = poolManager.createNewAgentPool("testPool");
    ProjectEx project = createProject("testPrj");
    poolManager.associateProjectsWithPool(pool.getAgentPoolId(), Collections.singleton(project.getProjectId()));

    SUser user = createUser("testUser");
    assertFalse("Test precondition failed.", AuthUtil.hasPermissionToEnableAgentsInPool(user, pool));

    AgentPool poolModel = new AgentPool(pool);

    myFixture.getSecurityContext().runAs(user, () -> {
      AgentPoolPermissions permissions = myResolver.permissions(poolModel, myDataFetchingEnvironment);
      assertFalse("User is not allowed to enable agents in pool without explicit permission", permissions.isEnableAgents());
    });

    user.addRole(RoleScope.globalScope(), getTestRoles().createRole(Permission.ENABLE_DISABLE_AGENT));
    myFixture.getSecurityContext().runAs(user, () -> {
      AgentPoolPermissions permissions = myResolver.permissions(poolModel, myDataFetchingEnvironment);
      assertTrue("User is allowed to enable agents in pool with global permission", permissions.isEnableAgents());
    });

    user.removeRoles(RoleScope.globalScope());
    user.addRole(RoleScope.projectScope(project.getProjectId()), getTestRoles().createRole(Permission.ENABLE_DISABLE_AGENT_FOR_PROJECT));
    myFixture.getSecurityContext().runAs(user, () -> {
      AgentPoolPermissions permissions = myResolver.permissions(poolModel, myDataFetchingEnvironment);
      assertTrue("User is allowed to enable agents in pool with per-project permission", permissions.isEnableAgents());
    });

    ProjectEx project2 = createProject("testPrj2");
    poolManager.associateProjectsWithPool(pool.getAgentPoolId(), Collections.singleton(project2.getProjectId()));
    myFixture.getSecurityContext().runAs(user, () -> {
      AgentPoolPermissions permissions = myResolver.permissions(poolModel, myDataFetchingEnvironment);
      assertFalse("User is not allowed to enable agents in pool with permission for each project", permissions.isEnableAgents());
    });
  }

  @Test(dataProvider="allBooleans")
  public void agentPoolPermissionsManageProjectsUsesAgentPoolAccessChecker(boolean isAllowed) throws Throwable {
    AgentPoolManager poolManager = myFixture.getAgentPoolManager();
    jetbrains.buildServer.serverSide.agentPools.AgentPool pool = poolManager.createNewAgentPool("testPool");
    AgentPool poolModel = new AgentPool(pool);

    myActionChecker.setCanManageProjectsInPool(isAllowed);

    AgentPoolPermissions permissions = myResolver.permissions(poolModel, myDataFetchingEnvironment);
    assertEquals("Returned value does not correspond to action checker.", isAllowed, permissions.isManageProjects());
  }

  @Test(dataProvider="allBooleans")
  public void agentPoolPermissionsManageAgentsUsesAgentPoolAccessChecker(boolean isAllowed) throws Throwable {
    AgentPoolManager poolManager = myFixture.getAgentPoolManager();
    jetbrains.buildServer.serverSide.agentPools.AgentPool pool = poolManager.createNewAgentPool("testPool");
    AgentPool poolModel = new AgentPool(pool);

    myActionChecker.setCanManageAgentsInPool(isAllowed);

    AgentPoolPermissions permissions = myResolver.permissions(poolModel, myDataFetchingEnvironment);
    assertEquals("Returned value does not correspond to action checker.", isAllowed, permissions.isManageAgents());
  }

  @org.testng.annotations.DataProvider(name = "allBooleans")
  public static Object[] allBooleans() {
    return new Object[] {true, false};
  }
}
