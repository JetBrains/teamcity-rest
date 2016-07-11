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

package jetbrains.buildServer.server.rest.model;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.server.rest.model.agent.Agent;
import jetbrains.buildServer.server.rest.model.agent.AgentEnabledInfo;
import jetbrains.buildServer.server.rest.request.AgentRequest;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.impl.MockBuildAgent;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 03/12/2015
 */
public class AgentTest extends BaseFinderTest<SBuildAgent> {
  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void testEnabledComment() throws ParseException {
    final MockBuildAgent agent1 = myFixture.createEnabledAgent("agent1", "runType");
    agent1.setEnabled(false, null, "test");
    assertFalse(agent1.isEnabled());
    Agent.setFieldValue(agent1, "enabled", "true", myFixture);
    assertTrue(agent1.isEnabled());

    AgentRequest resource = AgentRequest.createForTests(getBeanContext(myFixture));
    {
      AgentEnabledInfo enabledInfo = resource.getEnabledInfo("id:" + agent1.getId(), "$long");
      assertEquals(Boolean.TRUE, enabledInfo.status);
      assertEquals(null, enabledInfo.comment.text);
      assertEquals(null, enabledInfo.statusSwitchTime);
    }
    AgentEnabledInfo newEnabledInfo = new AgentEnabledInfo();
    newEnabledInfo.status = Boolean.FALSE;
    newEnabledInfo.comment = new Comment();
    newEnabledInfo.comment.text = "custom comment";
    newEnabledInfo.statusSwitchTime = "+10m";
    resource.setEnabledInfo("id:" + agent1.getId(), newEnabledInfo, "$long");
    {
      AgentEnabledInfo enabledInfo = resource.getEnabledInfo("id:" + agent1.getId(), "$long");
      assertEquals(Boolean.FALSE, enabledInfo.status);
      assertEquals("custom comment", enabledInfo.comment.text);
      assertTrue(new SimpleDateFormat("yyyyMMdd'T'HHmmssZ").parse(enabledInfo.statusSwitchTime).getTime() - new Date().getTime() - 10 * 60 * 60 * 1000 < 1000);
    }
  }
}