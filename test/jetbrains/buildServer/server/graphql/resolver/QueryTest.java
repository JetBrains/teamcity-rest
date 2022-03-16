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

import jetbrains.buildServer.server.graphql.model.connections.PaginationArgumentsProviderImpl;
import jetbrains.buildServer.server.graphql.model.connections.ProjectsConnection;
import jetbrains.buildServer.server.graphql.model.filter.ProjectsFilter;
import jetbrains.buildServer.server.graphql.resolver.agentPool.AbstractAgentPoolFactory;
import jetbrains.buildServer.server.rest.data.Finder;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.SimpleParameter;
import jetbrains.buildServer.serverSide.impl.projects.ProjectImpl;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;


@Test
public class QueryTest extends BaseResolverTest {
  private Query myResolver;

  @Override
  @BeforeMethod(alwaysRun = true)
  public void setUp() throws Exception {
    super.setUp();

    Finder<SBuildAgent> agentFinderMock = (Finder<SBuildAgent>) mock(Finder.class).proxy();

    myResolver = new Query();
    myResolver.initForTests(
      agentFinderMock,
      myFixture.getProjectManager(),
      myFixture.getAgentPoolManager(),
      new PaginationArgumentsProviderImpl(),
      new AbstractAgentPoolFactory()
    );
  }

  public void testProjectsVisibleOnlyDefault() {
    ProjectsFilter filter = new ProjectsFilter();
    MockDataFetchingEnvironment mockDFE = new MockDataFetchingEnvironment();

    SProject virtual = createProject("virtualProject");
    virtual.addParameter(new SimpleParameter(ProjectImpl.TEAMCITY_VIRTUAL_PROJECT_PARAM, "true"));
    SProject regular = createProject("regularProject");



    ProjectsConnection result = myResolver.projects(filter, null, null, mockDFE);

    for(ProjectsConnection.ProjectsConnectionEdge edge : result.getEdges().getData()) {
      assertFalse("Only regular projects must be returned by default.", edge.getNode().getData().isVirtual());
    }
  }

  public void testProjectsInvisibleWhenRequested() {
    ProjectsFilter filter = new ProjectsFilter();
    filter.setVirtual(true);
    MockDataFetchingEnvironment mockDFE = new MockDataFetchingEnvironment();

    SProject virtual = createProject("virtualProject");
    virtual.addParameter(new SimpleParameter(ProjectImpl.TEAMCITY_VIRTUAL_PROJECT_PARAM, "true"));
    SProject regular = createProject("regularProject");


    ProjectsConnection result = myResolver.projects(filter, null, null, mockDFE);

    for(ProjectsConnection.ProjectsConnectionEdge edge : result.getEdges().getData()) {
      assertTrue("Only virtual projects must be returned when requested.", edge.getNode().getData().isVirtual());
    }
  }

  public void testProjectsAnyVisibilityWhenRequested() {
    ProjectsFilter filter = new ProjectsFilter();
    filter.setVirtual(null);
    MockDataFetchingEnvironment mockDFE = new MockDataFetchingEnvironment();

    SProject virtual = createProject("virtualProject");
    virtual.addParameter(new SimpleParameter(ProjectImpl.TEAMCITY_VIRTUAL_PROJECT_PARAM, "true"));
    SProject regular = createProject("regularProject");


    ProjectsConnection result = myResolver.projects(filter, null, null, mockDFE);

    boolean seenVirtual = false;
    boolean seenRegular = false;
    for(ProjectsConnection.ProjectsConnectionEdge edge : result.getEdges().getData()) {
      if(edge.getNode().getData().isVirtual()) {
        seenVirtual = true;
      } else {
        seenRegular = true;
      }
    }

    assertTrue("Virtual projects must be returned when visibility filter is not set.", seenVirtual);
    assertTrue("Regular projects must be returned when visibility filter is not set.", seenRegular);
  }
}
