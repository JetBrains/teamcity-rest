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

package jetbrains.buildServer.server.graphql.resolver.agentPool;

import java.util.Collections;
import jetbrains.buildServer.server.graphql.resolver.BaseResolverTest;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.users.SUser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class AgentPoolAccessCheckerImplTest extends BaseResolverTest {
  private AgentPoolActionsAccessCheckerImpl myActionChecker;

  @Override
  @BeforeMethod(alwaysRun = true)
  public void setUp() throws Exception {
    super.setUp();

    myActionChecker = new AgentPoolActionsAccessCheckerImpl(myFixture.getSecurityContext());
    myActionChecker.setAgentPoolManager(myFixture.getAgentPoolManager());
    myActionChecker.setAgentTypeStorage(myFixture.getAgentTypeManager()); // ?
    myActionChecker.setProjectManager(myFixture.getProjectManager());

    myFixture.getServerSettings().setPerProjectPermissionsEnabled(true);
  }

  public void testManageProjectsInPool() throws Throwable {
    AgentPoolManager poolManager = myFixture.getAgentPoolManager();
    jetbrains.buildServer.serverSide.agentPools.AgentPool pool = poolManager.createNewAgentPool("testPool");
    ProjectEx project = createProject("someproject");
    poolManager.associateProjectsWithPool(pool.getAgentPoolId(), Collections.singleton(project.getProjectId()));

    SUser user = createUser("user");

    myFixture.getSecurityContext().runAs(user, () -> {
      assertFalse("User is not allowed to manage projects without pemissions.", myActionChecker.canManageProjectsInPool(pool.getAgentPoolId()));
    });

    user.addRole(RoleScope.globalScope(), getTestRoles().createRole(Permission.MANAGE_AGENT_POOLS));
    myFixture.getSecurityContext().runAs(user, () -> {
      assertTrue("User is allowed to manage projects with global pemission.", myActionChecker.canManageProjectsInPool(pool.getAgentPoolId()));
    });

    user.removeRoles(RoleScope.globalScope());
    user.addRole(RoleScope.projectScope(project.getProjectId()), getTestRoles().createRole(Permission.MANAGE_AGENT_POOLS_FOR_PROJECT));
    myFixture.getSecurityContext().runAs(user, () -> {
      assertTrue("User is allowed to manage projects with per project pemission.", myActionChecker.canManageProjectsInPool(pool.getAgentPoolId()));
    });

    ProjectEx project2 = createProject("anotherproject");
    poolManager.associateProjectsWithPool(pool.getAgentPoolId(), Collections.singleton(project2.getProjectId()));
    myFixture.getSecurityContext().runAs(user, () -> {
      assertFalse("User is not allowed to manage projects without per project pemission on each project.", myActionChecker.canManageProjectsInPool(pool.getAgentPoolId()));
    });
  }

  public void testManageProjectsInProjectPool() throws Throwable {
    AgentPoolManager poolManager = myFixture.getAgentPoolManager();
    ProjectEx project = createProject("someproject");
    AgentPool pool = poolManager.getOrCreateProjectPool(project.getProjectId());
    poolManager.associateProjectsWithPool(pool.getAgentPoolId(), Collections.singleton(project.getProjectId()));

    SUser user = createUser("user");

    myFixture.getSecurityContext().runAs(user, () -> {
      assertFalse("Users are not allowed to manage projects in project pool.", myActionChecker.canManageProjectsInPool(pool.getAgentPoolId()));
    });

    user.addRole(RoleScope.globalScope(), getTestRoles().createRole(Permission.MANAGE_AGENT_POOLS));
    myFixture.getSecurityContext().runAs(user, () -> {
      assertFalse("Users are not allowed to manage projects in project pool.", myActionChecker.canManageProjectsInPool(pool.getAgentPoolId()));
    });

    user.removeRoles(RoleScope.globalScope());
    user.addRole(RoleScope.projectScope(project.getProjectId()), getTestRoles().createRole(Permission.MANAGE_AGENT_POOLS_FOR_PROJECT));
    myFixture.getSecurityContext().runAs(user, () -> {
      assertFalse("Users are not allowed to manage projects in project pool.", myActionChecker.canManageProjectsInPool(pool.getAgentPoolId()));
    });
  }

  public void testManageAgentsInPool() throws Throwable {
    AgentPoolManager poolManager = myFixture.getAgentPoolManager();
    jetbrains.buildServer.serverSide.agentPools.AgentPool pool = poolManager.createNewAgentPool("testPool");
    ProjectEx project = createProject("someproject");
    poolManager.associateProjectsWithPool(pool.getAgentPoolId(), Collections.singleton(project.getProjectId()));

    SUser user = createUser("user");

    myFixture.getSecurityContext().runAs(user, () -> {
      assertFalse("User is not allowed to manage agents without pemissions.", myActionChecker.canManageAgentsInPool(pool));
    });

    user.addRole(RoleScope.globalScope(), getTestRoles().createRole(Permission.MANAGE_AGENT_POOLS));
    myFixture.getSecurityContext().runAs(user, () -> {
      assertTrue("User is allowed to manage projects with global pemission.", myActionChecker.canManageAgentsInPool(pool));
    });

    user.removeRoles(RoleScope.globalScope());
    user.addRole(RoleScope.projectScope(project.getProjectId()), getTestRoles().createRole(Permission.MANAGE_AGENT_POOLS_FOR_PROJECT));
    myFixture.getSecurityContext().runAs(user, () -> {
      assertTrue("User is allowed to manage projects with per project pemission.", myActionChecker.canManageAgentsInPool(pool));
    });

    ProjectEx project2 = createProject("anotherproject");
    poolManager.associateProjectsWithPool(pool.getAgentPoolId(), Collections.singleton(project2.getProjectId()));
    myFixture.getSecurityContext().runAs(user, () -> {
      assertFalse("User is not allowed to manage projects without per project pemission on each project.", myActionChecker.canManageAgentsInPool(pool));
    });
  }

  public void testManageAgentsInProjectPool() throws Throwable {
    ProjectEx project = createProject("someproject");
    jetbrains.buildServer.serverSide.agentPools.AgentPool pool = myFixture.getAgentPoolManager().getOrCreateProjectPool(project.getProjectId());

    SUser user = createUser("user");

    myFixture.getSecurityContext().runAs(user, () -> {
      assertFalse("Users are not allowed to manage agent associations with project pool.", myActionChecker.canManageAgentsInPool(pool));
    });

    user.addRole(RoleScope.globalScope(), getTestRoles().createRole(Permission.MANAGE_AGENT_POOLS));
    myFixture.getSecurityContext().runAs(user, () -> {
      assertFalse("Users are not allowed to manage agent associations with project pool.", myActionChecker.canManageAgentsInPool(pool));
    });

    user.removeRoles(RoleScope.globalScope());
    user.addRole(RoleScope.projectScope(project.getProjectId()), getTestRoles().createRole(Permission.MANAGE_AGENT_POOLS_FOR_PROJECT));
    myFixture.getSecurityContext().runAs(user, () -> {
      assertFalse("Users are not allowed to manage agent associations with project pool.", myActionChecker.canManageAgentsInPool(pool));
    });
  }
}