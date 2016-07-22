/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.request;

import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.SProjectFeatureDescriptor;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.impl.ProjectFeatureDescriptorFactory;
import jetbrains.buildServer.util.CollectionsUtil;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 22/07/2016
 */
public class ProjectRequestTest extends BaseFinderTest<SProject> {
  private ProjectRequest myRequest;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myRequest = ProjectRequest.createForTests(BaseFinderTest.getBeanContext(myFixture));
  }

  @Test
  void testProjectFeaturesParameters() {
    ProjectEx project10 = createProject("project10", "project10");
    SProjectFeatureDescriptor feature10 =
      myFixture.getSingletonService(ProjectFeatureDescriptorFactory.class).createNewProjectFeature("feature_type", CollectionsUtil.asMap("a", "b"), project10);
    project10.addFeature(feature10);
    {
      String newValue = myRequest.getFeatures("id:" + project10.getExternalId()).getParametersSubResource(feature10.getId(), "$long").setParameterValue("a", "B");
      assertEquals("B", newValue);
      assertEquals(1, project10.getAvailableFeatures().size());
      assertEquals("B", project10.findFeatureById(feature10.getId()).getParameters().get("a"));
    }
    {
      String newValue = myRequest.getFeatures("id:" + project10.getExternalId()).getParametersSubResource("id:" + feature10.getId(), "$long").setParameterValue("a", "X");
      assertEquals("X", newValue);
      assertEquals(1, project10.getAvailableFeatures().size());
      assertEquals("X", project10.findFeatureById(feature10.getId()).getParameters().get("a"));
      assertEquals(1, project10.findFeatureById(feature10.getId()).getParameters().size());
    }
    {
      String newValue = myRequest.getFeatures("id:" + project10.getExternalId()).getParametersSubResource("id:" + feature10.getId(), "$long").setParameterValue("b", "Y");
      assertEquals("Y", newValue);
      assertEquals(1, project10.getAvailableFeatures().size());
      assertEquals(2, project10.findFeatureById(feature10.getId()).getParameters().size());
      assertEquals("Y", project10.findFeatureById(feature10.getId()).getParameters().get("b"));
    }
    {
      myRequest.getFeatures("id:" + project10.getExternalId()).getParametersSubResource("id:" + feature10.getId(), "$long").deleteParameter("b");
      assertEquals(1, project10.getAvailableFeatures().size());
      assertEquals(1, project10.findFeatureById(feature10.getId()).getParameters().size());
      assertEquals(null , project10.findFeatureById(feature10.getId()).getParameters().get("b"));
      assertEquals("X", project10.findFeatureById(feature10.getId()).getParameters().get("a"));
    }
  }
}
