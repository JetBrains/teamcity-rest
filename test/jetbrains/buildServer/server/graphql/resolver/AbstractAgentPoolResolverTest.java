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
import java.util.*;
import jetbrains.buildServer.clouds.server.CloudManager;
import jetbrains.buildServer.server.graphql.model.agentPool.AgentPool;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolProjectsConnection;
import jetbrains.buildServer.server.graphql.model.filter.ProjectsFilter;
import jetbrains.buildServer.server.graphql.resolver.agentPool.AbstractAgentPoolResolver;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.Permissions;
import jetbrains.buildServer.serverSide.impl.MockAuthorityHolder;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.impl.auth.SecuredProjectManager;
import org.jmock.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

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
  }

  @Test
  public void projectsConnection() throws Throwable {
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
}
