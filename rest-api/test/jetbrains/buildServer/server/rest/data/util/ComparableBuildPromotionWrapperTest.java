/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.util;


import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import org.testng.annotations.Test;

public class ComparableBuildPromotionWrapperTest extends BaseServerTestCase {
  @Test
  public void compareTwoQueued() {
    BuildTypeEx bt1 = registerBuildType("bt1", "P");
    BuildTypeEx bt2 = registerBuildType("bt2", "P");

    ComparableBuildPromotionWrapper queuedBuild1 = ComparableBuildPromotionWrapper.fromPromotion(build().in(bt1).addToQueue().getBuildPromotion());
    ComparableBuildPromotionWrapper queuedBuild2 = ComparableBuildPromotionWrapper.fromPromotion(build().in(bt2).addToQueue().getBuildPromotion());

    assertTrue("Last in queue is the smallest", queuedBuild2.compareTo(queuedBuild1) < 0);
  }

  @Test
  public void compareTwoRunning() {
    myFixture.createEnabledAgent("ant");
    myFixture.createEnabledAgent("ant");

    BuildTypeEx bt1 = registerBuildType("bt1", "P");
    BuildTypeEx bt2 = registerBuildType("bt2", "P");

    ComparableBuildPromotionWrapper runningBuild1 = ComparableBuildPromotionWrapper.fromPromotion(build().in(bt1).run().getBuildPromotion());
    ComparableBuildPromotionWrapper runningBuild2 = ComparableBuildPromotionWrapper.fromPromotion(build().in(bt2).run().getBuildPromotion());

    assertTrue("Last started is the smallest", runningBuild2.compareTo(runningBuild1) < 0);
  }

  @Test
  public void compareTwoFinished() {
    BuildTypeEx bt1 = registerBuildType("bt1", "P");
    BuildTypeEx bt2 = registerBuildType("bt2", "P");

    ComparableBuildPromotionWrapper runningBuild1 = ComparableBuildPromotionWrapper.fromPromotion(build().in(bt1).finish().getBuildPromotion());
    ComparableBuildPromotionWrapper runningBuild2 = ComparableBuildPromotionWrapper.fromPromotion(build().in(bt2).finish().getBuildPromotion());

    assertTrue("Last started is the smallest", runningBuild2.compareTo(runningBuild1) < 0);
  }

  @Test
  public void compareQueuedRunningFinished() {
    myFixture.createEnabledAgent("Ant");
    BuildTypeEx bt1 = registerBuildType("bt1", "P", "prevent_to_run");
    BuildTypeEx bt2 = registerBuildType("bt2", "P");
    BuildTypeEx bt3 = registerBuildType("bt3", "P");

    ComparableBuildPromotionWrapper queuedBuild = ComparableBuildPromotionWrapper.fromPromotion(build().in(bt1).addToQueue().getBuildPromotion());
    ComparableBuildPromotionWrapper runningBuild = ComparableBuildPromotionWrapper.fromPromotion(build().in(bt2).run().getBuildPromotion());
    ComparableBuildPromotionWrapper finishedBuild = ComparableBuildPromotionWrapper.fromPromotion(build().in(bt3).finish().getBuildPromotion());

    assertTrue("Queued comes first", queuedBuild.compareTo(runningBuild) < 0);
    assertTrue("Running comes second", runningBuild.compareTo(finishedBuild) < 0);
    assertTrue("Finished comes last", queuedBuild.compareTo(finishedBuild) < 0);
  }

  @Test
  public void compareChangingState() {
    BuildTypeEx bt1 = registerBuildType("bt1", "P", "special_runner");
    BuildTypeEx bt2 = registerBuildType("bt2", "P", "another_runner");

    // Add builds to queue so that promo2 < promo1
    BuildPromotion promotion1 = build().in(bt1).addToQueue().getBuildPromotion();
    BuildPromotion promotion2 = build().in(bt2).addToQueue().getBuildPromotion();

    // Pretend to prepare for sorting while both builds are still in queue
    ComparableBuildPromotionWrapper queuedWrapper = ComparableBuildPromotionWrapper.fromPromotion(promotion1);
    // This one is not running yet, but going to be
    ComparableBuildPromotionWrapper runningWrapper = ComparableBuildPromotionWrapper.fromPromotion(promotion2);

    // Only build 2 will start as we have unsatifiable run type for the build 1
    // After the build has started we have promotion1 < promotion2, as the first one is still queued
    myFixture.createEnabledAgent("another_runner");
    myFixture.waitForQueuedBuildToStart(promotion2.getQueuedBuild());


    assertTrue(
      "Comparison order is expected to be the same even if builds have changed their state while being sorted",
      runningWrapper.compareTo(queuedWrapper) < 0
    );
  }
}
