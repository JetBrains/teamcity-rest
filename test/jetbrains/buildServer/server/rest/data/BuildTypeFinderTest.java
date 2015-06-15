/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.SProject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 09.09.2014
 */
public class BuildTypeFinderTest extends BaseFinderTest<BuildTypeOrTemplate> {

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    final ProjectFinder projectFinder = new ProjectFinder(myProjectManager);
    final AgentFinder agentFinder = new AgentFinder(myAgentManager);

    setFinder(new BuildTypeFinder(myProjectManager, projectFinder, agentFinder, myServer));
  }

  @Test
  public void testBuildTypeTemplates() throws Exception {
    final BuildTypeOrTemplate template = new BuildTypeOrTemplate(createBuildTypeTemplate("template"));
    final BuildTypeOrTemplate buildConf = new BuildTypeOrTemplate(registerTemplateBasedBuildType("buildConf"));
    //noinspection ConstantConditions
    buildConf.getBuildType().attachToTemplate(template.getTemplate());

    check("template:(id:" + template.getTemplate().getExternalId() + "),templateFlag:false", buildConf);

    check("name:" + buildConf.getName() + ",templateFlag:false", buildConf);
    check("name:" + buildConf.getName() + ",templateFlag:true");
    check("name:" + template.getName() + ",templateFlag:false");
    check("name:" + template.getName() + ",templateFlag:true", template);
  }

  @Test
  public void testBuildTypes1() throws Exception {
    myBuildType.remove();

    final SProject project10 = createProject("p10");
    final SProject project10_10 = project10.createProject("p10_10", "p10_10");
    final SProject project10_20 = project10.createProject("p10_20", "p10_20");
    final SProject project10_10_10 = project10_10.createProject("p10_10_10", "p10_10_10");
    final SProject project20 = createProject("p20");
    final SProject project20_10 = project20.createProject("p20_10", "p20_10");
    final SProject project20_20 = project20.createProject("p20_20", "p20_20");
    final SProject project20_10_10 = project20_10.createProject("p20_10_10", "p20_10_10");
    final SProject project30 = createProject("p30");

    final BuildTypeOrTemplate p10_bt10 = new BuildTypeOrTemplate(project10.createBuildType("p10_bt10", "xxx"));
    final BuildTypeOrTemplate p10_bt20 = new BuildTypeOrTemplate(project10.createBuildType("p10_bt20", "p10_bt20"));
    final BuildTypeOrTemplate p10_10_bt10 = new BuildTypeOrTemplate(project10_10.createBuildType("p10_10_bt10", "xxx"));
    final BuildTypeOrTemplate p10_10_bt20 = new BuildTypeOrTemplate(project10_10.createBuildType("p10_10_bt20", "p10_10_bt20"));
    final BuildTypeOrTemplate p10_10_10_bt10 = new BuildTypeOrTemplate(project10_10_10.createBuildType("p10_10_10_bt10", "xxx"));
    final BuildTypeOrTemplate p10_10_10_bt20 = new BuildTypeOrTemplate(project10_10_10.createBuildType("p10_10_10_bt20", "p10_10_10_bt20"));

    final BuildTypeOrTemplate p20_bt10 = new BuildTypeOrTemplate(project20.createBuildType("p20_bt10", "p20_bt10"));
    final BuildTypeOrTemplate p20_bt20 = new BuildTypeOrTemplate(project20.createBuildType("p20_bt20", "p20_bt20"));
    final BuildTypeOrTemplate p20_10_bt10 = new BuildTypeOrTemplate(project20_10.createBuildType("p20_10_bt10", "xxx"));
    final BuildTypeOrTemplate p20_10_bt20 = new BuildTypeOrTemplate(project20_10.createBuildType("p20_10_bt20", "p20_10_bt20"));
    final BuildTypeOrTemplate p20_10_10_bt10 = new BuildTypeOrTemplate(project20_10_10.createBuildType("p20_10_10_bt10", "xxx"));
    final BuildTypeOrTemplate p20_10_10_bt20 = new BuildTypeOrTemplate(project20_10_10.createBuildType("p20_10_10_bt20", "p20_10_10_bt20"));

    check(null, p10_bt20, p10_bt10, p10_10_bt20, p10_10_bt10, p10_10_10_bt20, p10_10_10_bt10, p20_bt10, p20_bt20, p20_10_bt20, p20_10_bt10, p20_10_10_bt20, p20_10_10_bt10);

    check("affectedProject:(id:" + project10_10.getExternalId() + ")", p10_10_bt20, p10_10_bt10, p10_10_10_bt20, p10_10_10_bt10);
    check("project:(id:" + project10_10.getExternalId() + ")", p10_10_bt20, p10_10_bt10);

    check("name:xxx", p10_bt10, p10_10_bt10, p10_10_10_bt10, p20_10_bt10, p20_10_10_bt10);
    check("name:xxx,affectedProject:(id:" + project10_10.getExternalId() + ")", p10_10_bt10, p10_10_10_bt10);
    check("name:xxx,project:(id:" + project10_10.getExternalId() + ")", p10_10_bt10);

    check("name:p10_10_10_bt20,affectedProject:(id:" + project10_10.getExternalId() + ")", p10_10_10_bt20);
    check("name:p10_10_10_bt10,project:(id:" + project10_10.getExternalId() + ")");

    check("name:xxx,affectedProject:(id:" + project20.getExternalId() + ")", p20_10_bt10, p20_10_10_bt10);
    check("name:xxx,project:(id:" + project20.getExternalId() + ")"); //change comparing to 9.0 when searching for single item ???

    //case sensitivity
    check("name:xxX", p10_bt10, p10_10_bt10, p10_10_10_bt10, p20_10_bt10, p20_10_10_bt10);
    check("name:P10_10_10_bt20,affectedProject:(id:" + project10_10.getExternalId() + ")", p10_10_10_bt20);

    checkExceptionOnItemSearch(BadRequestException.class, "xxx");
    check("p10_10_10_bt20", p10_10_10_bt20);
  }
}
