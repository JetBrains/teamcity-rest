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
import jetbrains.buildServer.server.graphql.model.buildType.BuildType;
import jetbrains.buildServer.server.graphql.model.connections.BuildTypesConnection;
import jetbrains.buildServer.server.graphql.model.connections.PaginationArgumentsProviderImpl;
import jetbrains.buildServer.server.graphql.model.connections.ProjectsConnection;
import jetbrains.buildServer.server.graphql.resolver.agentPool.AbstractAgentPoolFactory;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ProjectResolverTest extends BaseResolverTest {
  private ProjectResolver myResolver;
  private ProjectEx mySubProject;
  private ProjectEx mySubSubProject;
  private BuildTypeEx myBuildType1;
  private BuildTypeEx myBuildType2;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    myResolver = new ProjectResolver(myFixture.getAgentPoolManager(), new PaginationArgumentsProviderImpl(), new AbstractAgentPoolFactory());
    mySubProject = myProject.createProject(myProject.getExternalId() + "_subproject", "subproject");
    mySubSubProject = mySubProject.createProject(mySubProject.getExternalId() + "_subproject", "subsubproject");

    myBuildType1 = mySubProject.createBuildType("bt1");
    myBuildType2 = mySubProject.createBuildType("bt2");
  }

  @Test
  public void testAncestors() throws Exception {
    Project subsubproject = new Project(mySubSubProject);
    myDataFetchingEnvironment.setLocalContext(mySubSubProject);
    myDataFetchingEnvironment.setArgument("first", -1);

    ProjectsConnection ancestors = myResolver.ancestorProjects(subsubproject, myDataFetchingEnvironment);

    assertEquals(3, ancestors.getCount());
    assertExtensibleEdges(ancestors.getEdges().getData(),
                new Project(mySubProject),
                new Project(myProject),
                new Project(myProject.getParentProject())
    );
  }

  @Test
  public void testBuildTypes() throws Exception {
    BuildTypesConnection buildTypes = myResolver.buildTypes(new Project(mySubProject), null, null, myDataFetchingEnvironment);

    assertEquals(2, buildTypes.getCount());
    assertExtensibleEdges(buildTypes.getEdges().getData(), new BuildType(myBuildType1), new BuildType(myBuildType2)
    );
  }
}
