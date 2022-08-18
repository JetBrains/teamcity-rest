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

package jetbrains.buildServer.server.rest.util;

import java.util.stream.Collectors;
import jetbrains.buildServer.serverSide.BuildQueue;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class BuildQueuePositionModifierTest extends BaseServerTestCase {
  private BuildQueuePostitionModifier myModifier;

  @BeforeMethod(alwaysRun = true)
  public void setUp() throws Exception {
    super.setUp();
    myModifier = new BuildQueuePostitionModifier(myFixture.getBuildQueue());
  }

  public void should_move_to_top() {
    SQueuedBuild b1 = build().in(myBuildType).parameter("test", "1").addToQueue();
    SQueuedBuild b2 = build().in(myBuildType).parameter("test", "2").addToQueue();
    SQueuedBuild b3 = build().in(myBuildType).parameter("test", "3").addToQueue();

    BuildQueue q = myFixture.getBuildQueue();
    Assert.assertEquals("Test setup failure", b1, q.getItems().get(0));

    myModifier.moveBuild(b3, BuildQueuePositionDescriptor.FIRST);
    Assert.assertEquals(b3, q.getItems().get(0));
  }

  public void should_move_to_bottom() {
    SQueuedBuild b1 = build().in(myBuildType).parameter("test", "1").addToQueue();
    SQueuedBuild b2 = build().in(myBuildType).parameter("test", "2").addToQueue();
    SQueuedBuild b3 = build().in(myBuildType).parameter("test", "3").addToQueue();

    BuildQueue q = myFixture.getBuildQueue();
    Assert.assertEquals("Test setup failure", b3, q.getItems().get(2));

    myModifier.moveBuild(b1, BuildQueuePositionDescriptor.LAST);
    Assert.assertEquals(b1, q.getItems().get(2));
  }

  public void should_move_after_1() {
    SQueuedBuild b1 = build().in(myBuildType).parameter("test", "1").addToQueue();
    SQueuedBuild b2 = build().in(myBuildType).parameter("test", "2").addToQueue();
    SQueuedBuild b3 = build().in(myBuildType).parameter("test", "3").addToQueue();
    SQueuedBuild b4 = build().in(myBuildType).parameter("test", "4").addToQueue();

    BuildQueue q = myFixture.getBuildQueue();
    Assert.assertEquals("Test setup failure", b3, q.getItems().get(2));

    myModifier.moveBuild(b4, new BuildQueuePositionDescriptor(b1.getBuildPromotion().getId()));
    Assert.assertEquals(b1, q.getItems().get(0));
    Assert.assertEquals(b4, q.getItems().get(1));
    Assert.assertEquals(b2, q.getItems().get(2));
    Assert.assertEquals(b3, q.getItems().get(3));
  }

  public void should_move_after_2() {
    SQueuedBuild b1 = build().in(myBuildType).parameter("test", "1").addToQueue();
    SQueuedBuild b2 = build().in(myBuildType).parameter("test", "2").addToQueue();
    SQueuedBuild b3 = build().in(myBuildType).parameter("test", "3").addToQueue();
    SQueuedBuild b4 = build().in(myBuildType).parameter("test", "4").addToQueue();

    BuildQueue q = myFixture.getBuildQueue();
    Assert.assertEquals("Test setup failure", b3, q.getItems().get(2));

    myModifier.moveBuild(b4, new BuildQueuePositionDescriptor(b2.getBuildPromotion().getId(), b1.getBuildPromotion().getId()));
    Assert.assertEquals(b1, q.getItems().get(0));
    Assert.assertEquals(b2, q.getItems().get(1));
    Assert.assertEquals(b4, q.getItems().get(2));
    Assert.assertEquals(b3, q.getItems().get(3));
  }

  public void should_move_after_3() {
    SQueuedBuild b1 = build().in(myBuildType).parameter("test", "1").addToQueue();
    SQueuedBuild b2 = build().in(myBuildType).parameter("test", "2").addToQueue();
    SQueuedBuild b3 = build().in(myBuildType).parameter("test", "3").addToQueue();
    SQueuedBuild b4 = build().in(myBuildType).parameter("test", "4").addToQueue();

    BuildQueue q = myFixture.getBuildQueue();
    Assert.assertEquals("Test setup failure", b3, q.getItems().get(2));

    myModifier.moveBuild(b4, new BuildQueuePositionDescriptor(b2.getBuildPromotion().getId(), b3.getBuildPromotion().getId()));
    Assert.assertEquals(b1, q.getItems().get(0));
    Assert.assertEquals(b2, q.getItems().get(1));
    Assert.assertEquals(b3, q.getItems().get(2));
    Assert.assertEquals(b4, q.getItems().get(3));
  }

  public void should_move_after_several_builds() {
    SProject project = createProject("test");
    SQueuedBuild[] queuedBuilds = new SQueuedBuild[10];
    for(int i = 0; i < 10; i++) {
      SBuildType bt = project.createBuildType("B" + i, "B" + i);
      queuedBuilds[i] = build().in(bt).addToQueue();
    }

    BuildQueue q = myFixture.getBuildQueue();
    assertEquals("Test setup failure", "B0,B1,B2,B3,B4,B5,B6,B7,B8,B9", getQueueStateWithBuildTypeNames(q));

    BuildQueuePositionDescriptor afterOneThreeAndFive = new BuildQueuePositionDescriptor(
      queuedBuilds[1].getBuildPromotion().getId(),
      queuedBuilds[3].getBuildPromotion().getId(),
      queuedBuilds[5].getBuildPromotion().getId()
    );


    myModifier.moveBuild(queuedBuilds[0], afterOneThreeAndFive);
    assertEquals(
      "B1,B2,B3,B4,B5,B0,B6,B7,B8,B9",
      getQueueStateWithBuildTypeNames(q)
    );
  }

  public void should_move_after_with_dependencies() {
    SProject project = createProject("testPrjct");
    SBuildType A0 = project.createBuildType("A0", "A0");
    SBuildType A1 = project.createBuildType("A1", "A1");
    SBuildType A2 = project.createBuildType("A2", "A2");

    SBuildType B0 = project.createBuildType("B0", "B0");
    SBuildType B1 = project.createBuildType("B1", "B1");
    SBuildType B2 = project.createBuildType("B2", "B2");

    // B0 depends on B1 depends on B2
    addDependency(B0, B1);
    addDependency(B1, B2);

    addToQueue(A2);
    addToQueue(A1);
    addToQueue(A0);
    SQueuedBuild qb0 = addToQueue(B0);

    BuildQueue q = myFixture.getBuildQueue();
    assertEquals("Test setup failure", "A2,A1,A0,B2,B1,B0", getQueueStateWithBuildTypeNames(q));

    // moving build in B0 right after A2
    BuildQueuePositionDescriptor secondPlace = new BuildQueuePositionDescriptor(q.getItems().get(0).getBuildPromotion().getId());
    myModifier.moveBuild(qb0, secondPlace);

    assertEquals(
      "Build queue moves all dependencies too, so our build should actually become the fourth in the queue.",
      "A2,B2,B1,B0,A1,A0",
      getQueueStateWithBuildTypeNames(q)
    );
  }

  @NotNull
  private String getQueueStateWithBuildTypeNames(BuildQueue q) {
    return q.getItems().stream()
            .map(SQueuedBuild::getBuildType)
            .map(SBuildType::getName)
            .collect(Collectors.joining(","));
  }
}
