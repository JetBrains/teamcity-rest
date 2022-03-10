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

package jetbrains.buildServer.server.rest.model;

import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.SimpleParameter;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.impl.projects.ProjectImpl;
import org.testng.annotations.Test;

public class ProjectTest extends BaseFinderTest<SProject> {

  @Test
  public void testVirtualProject() {
    ProjectEx project = createProject("virtualProject");
    project.addParameter(new SimpleParameter(ProjectImpl.TEAMCITY_VIRTUAL_PROJECT_PARAM, "true"));

    Project projectModel;

    projectModel = new Project(project, new Fields("virtual"), getBeanContext(myFixture));
    assertTrue("Must return Project.virtual=true if requested ny name", projectModel.isVirtual());

    projectModel = new Project(project, Fields.LONG, getBeanContext(myFixture));
    assertTrue("Must return Project.virtual=true if $long", projectModel.isVirtual());

    projectModel = new Project(project, Fields.SHORT, getBeanContext(myFixture));
    assertNull(projectModel.isVirtual());
  }

  @Test
  public void testNonVirtualProject() {
    ProjectEx project = createProject("nonVirtualProject");
    project.addParameter(new SimpleParameter(ProjectImpl.TEAMCITY_VIRTUAL_PROJECT_PARAM, "false"));

    Project projectModel;

    projectModel = new Project(project, new Fields("virtual"), getBeanContext(myFixture));
    assertFalse("Must return Project.virtual=false if requested ny name", projectModel.isVirtual());

    projectModel = new Project(project, Fields.LONG, getBeanContext(myFixture));
    assertFalse("Must return Project.virtual=false if $long", projectModel.isVirtual());

    projectModel = new Project(project, Fields.SHORT, getBeanContext(myFixture));
    assertNull(projectModel.isVirtual());
  }
}
