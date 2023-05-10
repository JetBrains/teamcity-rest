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
import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.nodes.EnabledResponsibilities;
import jetbrains.buildServer.server.rest.model.nodes.Node;
import jetbrains.buildServer.server.rest.model.nodes.Nodes;
import jetbrains.buildServer.server.rest.model.nodes.Responsibility;
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
    Nodes nodes = myRequest.getAllNodes(null, Fields.SHORT.getFieldsSpec());
    assertEquals(1, nodes.nodes.size());
    assertEquals(1, (int)nodes.count);

    Heartbeat hb = createHeartbeat("node-2");
    hb.capture();
    hb.updateUrl("http://some.url.com/" + Heartbeat.AppType.SECONDARY_NODE.name());

    waitForAssert(() -> myRequest.getAllNodes(null, Fields.SHORT.getFieldsSpec()).count == 2);
    waitForAssert(() -> myFixture.getTeamCityNodes().getOnlineNodes().get(1).canAcceptHTTPRequests());

    nodes = myRequest.getAllNodes(null, Fields.SHORT.getFieldsSpec());
    assertEquals(2, nodes.nodes.size());
    assertTrue(nodes.nodes.get(0).current); // the current node is always first
    assertEquals("main_node", nodes.nodes.get(0).role);
    assertEquals(Node.NodeState.online, nodes.nodes.get(0).state);
    assertNull(nodes.nodes.get(0).url);
    assertNull(nodes.nodes.get(0).enabledResponsibilities);
    assertNull(nodes.nodes.get(0).effectiveResponsibilities);

    assertFalse(nodes.nodes.get(1).current);
    assertEquals("secondary_node", nodes.nodes.get(1).role);
    assertEquals(Node.NodeState.online, nodes.nodes.get(1).state);
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
    hb.updateUrl("http://some.url.com/" + Heartbeat.AppType.SECONDARY_NODE.name());

    waitForAssert(() -> teamCityNodes.getNodes().size() == 2);
    waitForAssert(() -> teamCityNodes.getOnlineNodes().get(1).canAcceptHTTPRequests());

    teamCityNodes.setEnabled(NodeResponsibility.CAN_PROCESS_BUILD_MESSAGES, teamCityNodes.getNodes().get(1), true);

    waitForAssert(() -> myRequest.getAllNodes(null, Fields.SHORT.getFieldsSpec()).count == 2);

    Nodes nodes = myRequest.getAllNodes(null, Fields.LONG.getFieldsSpec());
    assertEquals(2, nodes.nodes.size());
    assertTrue(nodes.nodes.get(0).current); // the current node is always first
    assertEquals("main_node", nodes.nodes.get(0).role);
    assertEquals(Node.NodeState.online, nodes.nodes.get(0).state);
    assertNotNull(nodes.nodes.get(0).url);
    assertNotNull(nodes.nodes.get(0).enabledResponsibilities);
    assertNotNull(nodes.nodes.get(0).effectiveResponsibilities);
    assertTrue(nodes.nodes.get(0).enabledResponsibilities.responsibilities.stream().map(n -> n.name).collect(Collectors.toSet()).contains(NodeResponsibility.MAIN_NODE.name()));
    assertNull(nodes.nodes.get(0).disabledResponsibilities.responsibilities);

    assertFalse(nodes.nodes.get(1).current);
    assertEquals("secondary_node", nodes.nodes.get(1).role);
    assertEquals(Node.NodeState.online, nodes.nodes.get(1).state);
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

  public void different_node_states() {
    SUser user = createAdmin("admin");
    assertTrue(user.isPermissionGrantedGlobally(Permission.VIEW_SERVER_SETTINGS));

    TeamCityNodes teamCityNodes = myFixture.getTeamCityNodes();

    makeLoggedIn(user);

    Heartbeat hb = createHeartbeat("node-2");
    hb.capture();

    waitForAssert(() -> teamCityNodes.getNodes().size() == 2);

    Nodes nodes = myRequest.getAllNodes(null, Fields.LONG.getFieldsSpec());
    assertEquals(2, nodes.nodes.size());
    assertTrue(nodes.nodes.get(0).current); // the current node is always first

    assertFalse(nodes.nodes.get(1).current);
    assertEquals("secondary_node", nodes.nodes.get(1).role);
    assertEquals(Node.NodeState.starting, nodes.nodes.get(1).state);

    hb.updateUrl("http://some.url.com/" + Heartbeat.AppType.SECONDARY_NODE.name());

    waitForAssert(() -> teamCityNodes.getOnlineNodes().get(1).canAcceptHTTPRequests());

    nodes = myRequest.getAllNodes(null, Fields.LONG.getFieldsSpec());
    assertEquals(Node.NodeState.online, nodes.nodes.get(1).state);

    hb.prepareLockForReleasing();

    waitForAssert(() -> teamCityNodes.getOnlineNodes().get(1).isStopping());

    nodes = myRequest.getAllNodes(null, Fields.LONG.getFieldsSpec());
    assertEquals(Node.NodeState.stopping, nodes.nodes.get(1).state);
  }

  public void getNodeById() {
    Heartbeat hb1 = createHeartbeat("node-1");
    hb1.capture();

    Heartbeat hb2 = createHeartbeat("node-2");
    hb2.capture();

    waitForAssert(() -> myRequest.getAllNodes(null, Fields.SHORT.getFieldsSpec()).count == 3);

    assertEquals(1, myRequest.getAllNodes("id:node-1", Fields.SHORT.getFieldsSpec()).count.intValue());
    assertNotNull(myRequest.getNode("id:node-1", Fields.SHORT.getFieldsSpec()));
    assertEquals("node-1", myRequest.getNode("id:node-1", Fields.SHORT.getFieldsSpec()).id);
    assertNotNull(myRequest.getNode("id:node-2", Fields.SHORT.getFieldsSpec()));
    assertEquals("node-2", myRequest.getNode("id:node-2", Fields.SHORT.getFieldsSpec()).id);
  }

  public void getNodesByState() {
    Heartbeat hb1 = createHeartbeat("node-1");
    hb1.capture();

    Heartbeat hb2 = createHeartbeat("node-2");
    hb2.capture();

    waitForAssert(() -> myRequest.getAllNodes(null, Fields.SHORT.getFieldsSpec()).count == 3);

    assertEquals(2, myRequest.getAllNodes("state:starting", Fields.SHORT.getFieldsSpec()).count.intValue());
    assertEquals(1, myRequest.getAllNodes("state:online", Fields.SHORT.getFieldsSpec()).count.intValue());
    assertEquals(Node.NodeRole.main_node.name(), myRequest.getNode("state:online", Fields.SHORT.getFieldsSpec()).role);

    hb2.updateUrl("http://some.url");

    waitForAssert(() -> myRequest.getAllNodes("state:online", Fields.SHORT.getFieldsSpec()).count == 2);
    assertEquals(2, myRequest.getAllNodes("state:online", Fields.SHORT.getFieldsSpec()).count.intValue());
    assertEquals(1, myRequest.getAllNodes("state:starting", Fields.SHORT.getFieldsSpec()).count.intValue());
    assertEquals("node-1", myRequest.getNode("state:starting", Fields.SHORT.getFieldsSpec()).id);

    hb1.prepareLockForReleasing();

    waitForAssert(() -> myRequest.getAllNodes("state:stopping", Fields.SHORT.getFieldsSpec()).count == 1);
    assertEquals(1, myRequest.getAllNodes("state:stopping", Fields.SHORT.getFieldsSpec()).count.intValue());
    assertEquals("node-1", myRequest.getNode("state:stopping", Fields.SHORT.getFieldsSpec()).id);
  }

  public void getNodesByRole() {
    Heartbeat hb1 = createHeartbeat("node-1");
    hb1.capture();

    Heartbeat hb2 = createHeartbeat("node-2");
    hb2.capture();

    waitForAssert(() -> myRequest.getAllNodes(null, Fields.SHORT.getFieldsSpec()).count == 3);

    assertEquals(1, myRequest.getAllNodes("role:main_node", Fields.SHORT.getFieldsSpec()).count.intValue());
    assertEquals(2, myRequest.getAllNodes("role:secondary_node", Fields.SHORT.getFieldsSpec()).count.intValue());
    assertTrue(myRequest.getAllNodes("role:secondary_node", Fields.SHORT.getFieldsSpec()).nodes.stream().map(n -> n.id).collect(Collectors.toSet()).containsAll(Arrays.asList("node-1", "node-2")));
    assertNotNull(myRequest.getNode("role:secondary_node", Fields.SHORT.getFieldsSpec()));
  }

  public void change_enabled_responsibility() {
    SUser user = createAdmin("admin");
    assertTrue(user.isPermissionGrantedGlobally(Permission.CHANGE_SERVER_SETTINGS));
    makeLoggedIn(user);

    Heartbeat hb1 = createHeartbeat("node-1");
    hb1.capture();

    waitForAssert(() -> myRequest.getAllNodes("id:node-1", Fields.SHORT.getFieldsSpec()).count == 1);

    EnabledResponsibilities enabledResponsibilities = myRequest.getNode("id:node-1", Fields.LONG.getFieldsSpec()).enabledResponsibilities;
    assertEquals(0, enabledResponsibilities.count.intValue());

    EnabledResponsibilities result = myRequest.changeNodeResponsibility("id:node-1", NodeResponsibility.CAN_PROCESS_BUILD_MESSAGES.name(), "true");
    assertEquals(1, result.count.intValue());
    assertTrue(result.responsibilities.contains(new Responsibility(NodeResponsibility.CAN_PROCESS_BUILD_MESSAGES, Fields.LONG)));

    result = myRequest.changeNodeResponsibility("id:node-1", NodeResponsibility.CAN_PROCESS_BUILD_MESSAGES.name(), "false");
    assertEquals(0, result.count.intValue());
  }

  public void do_not_allow_to_change_unassignable_responsibility() {
    SUser user = createAdmin("admin");
    assertTrue(user.isPermissionGrantedGlobally(Permission.CHANGE_SERVER_SETTINGS));
    makeLoggedIn(user);

    Heartbeat hb1 = createHeartbeat("node-1");
    hb1.capture();

    waitForAssert(() -> myRequest.getAllNodes("id:node-1", Fields.SHORT.getFieldsSpec()).count == 1);

    try {
      myRequest.changeNodeResponsibility("id:node-1", NodeResponsibility.CAN_CLEANUP.name(), "true");
      fail("Exception expected");
    } catch (BadRequestException e) {
      assertTrue(e.getMessage().contains("Cannot change"));
    }
  }

  public void allow_to_change_responsibility_of_the_main_node() {
    SUser user = createAdmin("admin");
    assertTrue(user.isPermissionGrantedGlobally(Permission.CHANGE_SERVER_SETTINGS));
    makeLoggedIn(user);

    EnabledResponsibilities result = myRequest.changeNodeResponsibility("role:main_node", NodeResponsibility.CAN_PROCESS_BUILD_MESSAGES.name(), "false");
    assertFalse(result.responsibilities.contains(new Responsibility(NodeResponsibility.CAN_PROCESS_BUILD_MESSAGES, Fields.LONG)));
  }

  public void do_not_allow_to_change_main_node_responsibility_if_main_node_is_online() {
    SUser user = createAdmin("admin");
    assertTrue(user.isPermissionGrantedGlobally(Permission.CHANGE_SERVER_SETTINGS));
    makeLoggedIn(user);

    Heartbeat hb1 = createHeartbeat("node-1");
    hb1.capture();

    waitForAssert(() -> myRequest.getAllNodes("id:node-1", Fields.SHORT.getFieldsSpec()).count == 1);

    try {
      myRequest.changeNodeResponsibility("id:node-1", NodeResponsibility.MAIN_NODE.name(), "true");
      fail("Exception expected");
    } catch (BadRequestException e) {
      assertTrue(e.getMessage().contains("while there is online main node"));
    }
  }

  @NotNull
  private Heartbeat createHeartbeat(@NotNull String nodeId, @NotNull NodeResponsibility... responsibilities) {
    Set<NodeResponsibility> resps = new HashSet<>();
    Collections.addAll(resps, responsibilities);
    final Heartbeat hbt =
      new Heartbeat(Objects.requireNonNull(TestDB.getDbManager().getDataSource()), myFixture.getServerPaths().getDataDirectory(), TestDB.getDialect(), Loggers.SERVER,
                    new Heartbeat.AppInfo(nodeId, System.nanoTime(), "127.0.0.1", "server" + myHeartbeats.size(), Heartbeat.AppType.SECONDARY_NODE, "123456", "111-222-333",
                                          "build1", "build1", resps));
    myHeartbeats.add(hbt);
    return hbt;
  }
}
