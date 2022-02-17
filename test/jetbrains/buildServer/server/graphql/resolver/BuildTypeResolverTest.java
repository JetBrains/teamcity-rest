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

import jetbrains.buildServer.server.graphql.model.Project;
import jetbrains.buildServer.server.graphql.model.connections.ProjectsConnection;
import jetbrains.buildServer.server.rest.data.AgentFinder;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class BuildTypeResolverTest extends BaseResolverTest {
  private BuildTypeResolver myResolver;

  @Override
  @BeforeMethod(alwaysRun = true)
  public void setUp() throws Exception {
    super.setUp();

    myResolver = new BuildTypeResolver();

    myResolver.setBuildTypeFinder(
      new BuildTypeFinder(
        myFixture.getProjectManager(),
        myProjectFinder,
        new AgentFinder(myAgentManager, myFixture),
        myPermissionChecker,
        myServer
      )
    );
  }

  public void basicAncestorProjects() throws Exception {
    ProjectEx p0 = myFixture.createProject("p0");
    ProjectEx p1 = myFixture.createProject("p1", p0);
    ProjectEx p2 = myFixture.createProject("p2", p1);
    ProjectEx p3 = myFixture.createProject("p3", p2);
    BuildTypeImpl realBuildType = myFixture.createBuildType(p3, "testBT", "test");

    ProjectsConnection ancestors = myResolver.ancestorProjects(
      new jetbrains.buildServer.server.graphql.model.buildType.BuildType(realBuildType),
      myDataFetchingEnvironment
    );

    assertEquals(5, ancestors.getCount()); //_Root, p0 .. p3

    // Ancestors must be from closest to _Root
    assertExtensibleEdges(
      ancestors.getEdges().getData(),
      new Project(p3),
      new Project(p2),
      new Project(p1),
      new Project(p0),
      new Project(myFixture.getProjectManager().getRootProject())
    );

  }
}
