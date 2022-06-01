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

package jetbrains.buildServer.server.rest.request;

import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.nodes.Nodes;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.NodeResponsibility;
import jetbrains.buildServer.serverSide.TeamCityNode;
import jetbrains.buildServer.serverSide.TeamCityNodes;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.db.Heartbeat;
import jetbrains.buildServer.serverSide.db.TestDB;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class NodesRequestTest extends BaseFinderTest<TeamCityNode> {
  private NodesRequest myRequest;
  private final List<Heartbeat> myHeartbeats = new ArrayList<>();

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    final BeanContext beanContext = BaseFinderTest.getBeanContext(myFixture);
    myRequest = NodesRequest.createForTests(beanContext);

    Heartbeat mainHeartbeat = myFixture.getSingletonService(Heartbeat.class);
    mainHeartbeat.capture();
    myHeartbeats.add(mainHeartbeat);
  }

  @AfterMethod(alwaysRun = true)
  @Override
  protected void tearDown() throws Exception {
    releaseHeartbeats();
    super.tearDown();
  }

  private void releaseHeartbeats() {
    for (Heartbeat hb: myHeartbeats) {
      hb.release();
    }
    myHeartbeats.clear();
  }

  public void nodes_no_authorized_user() {
    Nodes nodes = myRequest.nodes(Fields.SHORT.getFieldsSpec());
    assertEquals(1, nodes.nodes.size());
    assertEquals(1, (int)nodes.count);

    Heartbeat hb = createHeartbeat("node-2");
    hb.capture();

    waitForAssert(() -> myRequest.nodes(Fields.SHORT.getFieldsSpec()).count == 2);

    nodes = myRequest.nodes(Fields.SHORT.getFieldsSpec());
    assertEquals(2, nodes.nodes.size());
    assertTrue(nodes.nodes.get(0).current); // the current node is always first
    assertEquals("main_node", nodes.nodes.get(0).role);
    assertTrue(nodes.nodes.get(0).online);
    assertNull(nodes.nodes.get(0).url);
    assertNull(nodes.nodes.get(0).enabledResponsibilities);
    assertNull(nodes.nodes.get(0).effectiveResponsibilities);

    assertFalse(nodes.nodes.get(1).current);
    assertEquals("secondary_node", nodes.nodes.get(1).role);
    assertTrue(nodes.nodes.get(1).online);
    assertNull(nodes.nodes.get(1).url);
    assertNull(nodes.nodes.get(1).enabledResponsibilities);
    assertNull(nodes.nodes.get(1).effectiveResponsibilities);
  }

  public void nodes_authorized_user() {
    SUser user = createAdmin("admin");
    assertTrue(user.isPermissionGrantedGlobally(Permission.VIEW_SERVER_SETTINGS));

    TeamCityNodes teamCityNodes = myFixture.getTeamCityNodes();

    makeLoggedIn(user);

    Heartbeat hb = createHeartbeat("node-2");
    hb.capture();

    waitForAssert(() -> teamCityNodes.getNodes().size() == 2);
    teamCityNodes.setEnabled(NodeResponsibility.CAN_PROCESS_BUILD_MESSAGES, teamCityNodes.getNodes().get(1), true);

    waitForAssert(() -> myRequest.nodes(Fields.SHORT.getFieldsSpec()).count == 2);

    Nodes nodes = myRequest.nodes(Fields.LONG.getFieldsSpec());
    assertEquals(2, nodes.nodes.size());
    assertTrue(nodes.nodes.get(0).current); // the current node is always first
    assertEquals("main_node", nodes.nodes.get(0).role);
    assertTrue(nodes.nodes.get(0).online);
    assertNotNull(nodes.nodes.get(0).url);
    assertNotNull(nodes.nodes.get(0).enabledResponsibilities);
    assertNotNull(nodes.nodes.get(0).effectiveResponsibilities);
    assertTrue(nodes.nodes.get(0).enabledResponsibilities.responsibilities.stream().map(n -> n.name).collect(Collectors.toSet()).contains(NodeResponsibility.MAIN_NODE.name()));
    assertNull(nodes.nodes.get(0).disabledResponsibilities.responsibilities);

    assertFalse(nodes.nodes.get(1).current);
    assertEquals("secondary_node", nodes.nodes.get(1).role);
    assertTrue(nodes.nodes.get(1).online);
    assertContains(nodes.nodes.get(1).url, "http://some.url.com/");
    assertNotNull(nodes.nodes.get(1).enabledResponsibilities);
    assertNotNull(nodes.nodes.get(1).effectiveResponsibilities);
    final Set<String> enabledResps = nodes.nodes.get(1).enabledResponsibilities.responsibilities.stream().map(n -> n.name).collect(Collectors.toSet());
    assertTrue(enabledResps.contains(NodeResponsibility.CAN_PROCESS_BUILD_MESSAGES.name()));
    assertFalse(enabledResps.contains(NodeResponsibility.MAIN_NODE.name()));

    final Set<String> disabledResps = nodes.nodes.get(1).disabledResponsibilities.responsibilities.stream().map(n -> n.name).collect(Collectors.toSet());
    assertFalse(disabledResps.contains(NodeResponsibility.CAN_PROCESS_BUILD_MESSAGES.name()));
    assertTrue(disabledResps.contains(NodeResponsibility.MAIN_NODE.name()));
  }

  public void getNodeById() {
    Heartbeat hb1 = createHeartbeat("node-1");
    hb1.capture();

    Heartbeat hb2 = createHeartbeat("node-2");
    hb2.capture();

    waitForAssert(() -> myRequest.nodes(Fields.SHORT.getFieldsSpec()).count == 3);

    assertNotNull(myRequest.getNode("node-1", Fields.SHORT.getFieldsSpec()));
    assertEquals("node-1", myRequest.getNode("node-1", Fields.SHORT.getFieldsSpec()).id);
    assertNotNull(myRequest.getNode("node-2", Fields.SHORT.getFieldsSpec()));
    assertEquals("node-2", myRequest.getNode("node-2", Fields.SHORT.getFieldsSpec()).id);
  }

  @NotNull
  private Heartbeat createHeartbeat(@NotNull String nodeId, @NotNull NodeResponsibility... responsibilities) {
    Set<NodeResponsibility> resps = new HashSet<>();
    Collections.addAll(resps, responsibilities);
    final Heartbeat hbt =
      new Heartbeat(Objects.requireNonNull(TestDB.getDbManager().getDataSource()), myFixture.getServerPaths().getDataDirectory(), TestDB.getDialect(), Loggers.SERVER,
                    new Heartbeat.AppInfo(nodeId, System.nanoTime(), "127.0.0.1", "server" + myHeartbeats.size(), Heartbeat.AppType.SECONDARY_NODE, "123456", "111-222-333",
                                          "build1", "build1", resps));
    hbt.updateUrl("http://some.url.com/" + Heartbeat.AppType.SECONDARY_NODE.name());
    myHeartbeats.add(hbt);
    return hbt;
  }
}
