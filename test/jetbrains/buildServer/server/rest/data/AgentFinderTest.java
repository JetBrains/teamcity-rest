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

import java.util.List;
import jetbrains.buildServer.serverSide.BuildAgentEx;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.MockBuildAgent;
import jetbrains.buildServer.util.StringUtil;
import org.junit.BeforeClass;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 17.10.2014
 */
@Test
public class AgentFinderTest extends BaseServerTestCase {
  private AgentFinder myAgentFinder;
  private MockBuildAgent myAgent1;
  private MockBuildAgent myAgent2;
  private MockBuildAgent myAgent3;
  private MockBuildAgent myAgent4;

  @Override
  @BeforeClass
  public void setUp() throws Exception {
    super.setUp();

    myAgentFinder = new AgentFinder(myAgentManager);

    final List<BuildAgentEx> currentAgents = myAgentManager.getAllAgents(true);
    assertEquals(1, currentAgents.size());

    myAgent1 = (MockBuildAgent)currentAgents.get(0);

    myAgent2 = myFixture.createEnabledAgent("agent2", "Ant");
    myAgent2.setIsAvailable(false);
    myAgentManager.unregisterAgent(myAgent2.getId());

    myAgent3 = myFixture.createEnabledAgent("agent3", "Ant");
    myAgent3.setAuthorized(false, null, "test");

    myAgent4 = myFixture.createEnabledAgent("agent4", "Ant");
    myAgent2.setIsAvailable(false);
    myAgentManager.unregisterAgent(myAgent4.getId());
    myAgent4.setAuthorized(false, null, "test");
  }

  @Test
  public void testEmptyLocator() {
    checkAgents(null, myAgent1, myAgent2, myAgent3, myAgent4);
  }

  @Test
  public void testLocatorConnected() {
    checkAgents("connected:true", myAgent1, myAgent3);
    checkAgents("connected:false", myAgent2, myAgent4);
    checkAgents("connected:any", myAgent1, myAgent2, myAgent3, myAgent4);
  }

  @Test
  public void testLocatorAuthorized() {
    checkAgents("authorized:true", myAgent1, myAgent2);
    checkAgents("authorized:false", myAgent3, myAgent4);
    checkAgents("authorized:any", myAgent1, myAgent2, myAgent3, myAgent4);
  }

  @Test
  public void testLocatorMixed1() {
    checkAgents("connected:true,authorized:true", myAgent1);
    checkAgents("connected:false,authorized:true", myAgent2);
    checkAgents("connected:true,authorized:false", myAgent3);
    checkAgents("connected:false,authorized:false", myAgent4);
  }


  private void checkAgents(final String locatorText, final SBuildAgent... agents) {
    final PagedSearchResult<SBuildAgent> items = myAgentFinder.getItems(locatorText);

    // do assertContains(items.myEntries, agents); just compare agents by id
    for (SBuildAgent agent : agents) {
      boolean found = false;
      for (SBuildAgent entry : items.myEntries) {
        if (agent.getId() == entry.getId()){
          found = true;
          break;
        }
      }
      if (!found){
        fail("Agent \"" + agent.toString() + "\" is not found in the collection " + StringUtil.join(", ", items.myEntries));
      }
    }

    assertEquals(agents.length, items.myActualCount);
  }
}
