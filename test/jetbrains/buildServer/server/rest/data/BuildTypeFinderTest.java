/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.server.rest.request.BuildTypeRequest;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.BuildTypeTemplateEx;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 09.09.2014
 */
public class BuildTypeFinderTest extends BaseServerTestCase {
  private BuildTypeFinder myBuildTypeFinder;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    final ProjectFinder projectFinder = new ProjectFinder(myProjectManager);
    final AgentFinder agentFinder = new AgentFinder(myAgentManager);

    myBuildTypeFinder = new BuildTypeFinder(myProjectManager, projectFinder, agentFinder, myServer);
  }

  @Test
  public void testBuildTypeTemplates() throws Exception {
    final BuildTypeTemplateEx template = createBuildTypeTemplate("template");
    final BuildTypeImpl buildConf = registerTemplateBasedBuildType("buildConf");
    buildConf.attachToTemplate(template);

    final PagedSearchResult<BuildTypeOrTemplate> result = myBuildTypeFinder.getItems("template:(id:" + template.getExternalId() + "),templateFlag:false");

    assertEquals(1, result.myEntries.size());
    final BuildTypeOrTemplate buildTypeOrTemplate = result.myEntries.get(0);
    assertEquals(true, buildTypeOrTemplate.isBuildType());
    assertEquals(buildConf, buildTypeOrTemplate.getBuildType());
  }

}
