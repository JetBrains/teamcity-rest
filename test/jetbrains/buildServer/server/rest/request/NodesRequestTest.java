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

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class NodesRequestTest extends BaseFinderTest<TeamCityNode> {
  private NodesRequest myRequest;
  private final List<Heartbeat> myHeartbeats = new ArrayList<>();
  private Heartbeat myMainHeartbeat;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    final BeanContext beanContext = BaseFinderTest.getBeanContext(myFixture);
    myRequest = NodesRequest.createForTests(beanContext);
    myMainHeartbeat = myFixture.getSingletonService(Heartbeat.class);
    myMainHeartbeat.capture();
    myHeartbeats.add(myMainHeartbeat);
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
    then(nodes.nodes.size()).isEqualTo(1);
    then(nodes.count).isEqualTo(1);

    Heartbeat hb = createHeartbeat("node-2");
    hb.capture();

    waitForAssert(() -> myRequest.nodes(Fields.SHORT.getFieldsSpec()).count == 2);

    nodes = myRequest.nodes(Fields.SHORT.getFieldsSpec());
    then(nodes.nodes.size()).isEqualTo(2);
    then(nodes.nodes.get(0).current).isTrue(); // the current node is always first
    then(nodes.nodes.get(0).role).isEqualTo("main_node");
    then(nodes.nodes.get(0).online).isTrue();
    then(nodes.nodes.get(0).url).isNull();
    then(nodes.nodes.get(0).enabledResponsibilities).isNull();
    then(nodes.nodes.get(0).effectiveResponsibilities).isNull();

    then(nodes.nodes.get(1).current).isFalse();
    then(nodes.nodes.get(1).role).isEqualTo("secondary_node");
    then(nodes.nodes.get(1).online).isTrue();
    then(nodes.nodes.get(1).url).isNull();
    then(nodes.nodes.get(1).enabledResponsibilities).isNull();
    then(nodes.nodes.get(1).effectiveResponsibilities).isNull();
  }

  public void nodes_authorized_user() {
    SUser user = createAdmin("admin");
    then(user.isPermissionGrantedGlobally(Permission.VIEW_SERVER_SETTINGS)).isTrue();

    TeamCityNodes teamCityNodes = myFixture.getTeamCityNodes();

    makeLoggedIn(user);

    Heartbeat hb = createHeartbeat("node-2");
    hb.capture();

    waitForAssert(() -> teamCityNodes.getNodes().size() == 2);
    teamCityNodes.setEnabled(NodeResponsibility.CAN_PROCESS_BUILD_MESSAGES, teamCityNodes.getNodes().get(1), true);

    waitForAssert(() -> myRequest.nodes(Fields.SHORT.getFieldsSpec()).count == 2);

    Nodes nodes = myRequest.nodes(Fields.LONG.getFieldsSpec());
    then(nodes.nodes.size()).isEqualTo(2);
    then(nodes.nodes.get(0).current).isTrue(); // the current node is always first
    then(nodes.nodes.get(0).role).isEqualTo("main_node");
    then(nodes.nodes.get(0).online).isTrue();
    then(nodes.nodes.get(0).url).isNotNull();
    then(nodes.nodes.get(0).enabledResponsibilities).isNotNull();
    then(nodes.nodes.get(0).effectiveResponsibilities).isNotNull();
    then(nodes.nodes.get(0).enabledResponsibilities.responsibilities.stream().map(n -> n.name).collect(Collectors.toSet())).contains(NodeResponsibility.MAIN_NODE.name());

    then(nodes.nodes.get(1).current).isFalse();
    then(nodes.nodes.get(1).role).isEqualTo("secondary_node");
    then(nodes.nodes.get(1).online).isTrue();
    then(nodes.nodes.get(1).url).isNotNull().startsWith("http://some.url.com/");
    then(nodes.nodes.get(1).enabledResponsibilities).isNotNull();
    then(nodes.nodes.get(1).effectiveResponsibilities).isNotNull();
    then(nodes.nodes.get(1).enabledResponsibilities.responsibilities.stream().map(n -> n.name).collect(Collectors.toSet()))
      .doesNotContain(NodeResponsibility.MAIN_NODE.name())
      .contains(NodeResponsibility.CAN_PROCESS_BUILD_MESSAGES.name());
  }

  public void getNodeById() {
    Heartbeat hb1 = createHeartbeat("node-1");
    hb1.capture();

    Heartbeat hb2 = createHeartbeat("node-2");
    hb2.capture();

    waitForAssert(() -> myRequest.nodes(Fields.SHORT.getFieldsSpec()).count == 3);

    then(myRequest.getNode("node-1", Fields.SHORT.getFieldsSpec())).isNotNull();
    then(myRequest.getNode("node-1", Fields.SHORT.getFieldsSpec()).id).isEqualTo("node-1");
    then(myRequest.getNode("node-2", Fields.SHORT.getFieldsSpec())).isNotNull();
    then(myRequest.getNode("node-2", Fields.SHORT.getFieldsSpec()).id).isEqualTo("node-2");
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
