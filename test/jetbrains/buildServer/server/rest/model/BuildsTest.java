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

package jetbrains.buildServer.server.rest.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.TestFor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;


public class BuildsTest extends BaseFinderTest<BuildPromotion> {
  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  @TestFor(issues = "TW-78458")
  public void testLocatorGetLatestBuildFromDefaultBranchInBuildType() {
    BuildPromotion latestBuildIndefaultBranch = build().in(myBuildType).finish().getBuildPromotion();
    build().in(myBuildType).withBranch("branch1").finish().getBuildPromotion();
    build().in(myBuildType).withBranch("branch2").finish().getBuildPromotion();


    BuildTypes buildTypes = new BuildTypes(
      Collections.singletonList(new BuildTypeOrTemplate(myBuildType)),
      null,
      new Fields("buildType(builds(build(id),$locator(branch:(policy:ALL_BRANCHES,default:true),count:1)))"),
      getBeanContext(myFixture)
    );

    Builds builds = buildTypes.buildTypes.get(0).getBuilds();

    assertEquals("count:1 must be taken into account", 1, builds.getBuilds().size());
    assertEquals("Latest build from default branch is expected", latestBuildIndefaultBranch.getId(), (long) builds.getBuilds().get(0).getId());
  }

  @Test
  @TestFor(issues = "TW-78458")
  public void testLocatorGetLatestBuildFromAnyBranchInBuildType() {
    build().in(myBuildType).finish().getBuildPromotion();
    build().in(myBuildType).withBranch("branch2").finish().getBuildPromotion();
    BuildPromotion latestBuildInAnyBranch = build().in(myBuildType).withBranch("branch2").finish().getBuildPromotion();

    BuildTypes buildTypes = new BuildTypes(
      Collections.singletonList(new BuildTypeOrTemplate(myBuildType)),
      null,
      new Fields("buildType(builds(build(id),$locator(branch:(policy:ALL_BRANCHES,default:any),count:1)))"),
      getBeanContext(myFixture)
    );

    Builds builds = buildTypes.buildTypes.get(0).getBuilds();

    assertEquals("count:1 must be taken into account", 1, builds.getBuilds().size());
    assertEquals("Latest build from any branch is expected", latestBuildInAnyBranch.getId(), (long) builds.getBuilds().get(0).getId());
  }

  @Test
  @TestFor(issues = "TW-71560")
  public void testLocatorFilterByBuildType() {
    BuildTypeEx btLeft = myProject.createBuildType("bl_left", "bt_left");
    BuildTypeEx btRight = myProject.createBuildType("bl_right", "bt_right");

    BuildPromotion left = build().in(btLeft).finish().getBuildPromotion();
    BuildPromotion right = build().in(btRight).finish().getBuildPromotion();

    CallCountingItemsRetriever dataRetriever = new CallCountingItemsRetriever(Arrays.asList(left, right));
    Builds builds = Builds.createFromBuildPromotions(
      dataRetriever,
      new Fields("build(id),$locator(buildType:bt_left)"),
      getBeanContext(myFixture)
    );

    assertEquals(1, builds.getBuilds().size());
    assertEquals(left.getAssociatedBuildId(), builds.getBuilds().get(0).getId());

    assertEquals(1, dataRetriever.getItems_callCount);
    assertEquals(0, dataRetriever.getCount_callCount);
    assertEquals(0, dataRetriever.getPagerData_callCount);
  }

  @Test
  public void testBuildsAreLazy() {
    CallCountingItemsRetriever dataRetriever = new CallCountingItemsRetriever(Collections.emptyList());

    Builds.createFromBuildPromotions(
      dataRetriever,
      new Fields("href"),
      getBeanContext(myFixture)
    );

    assertEquals(0, dataRetriever.getCount_callCount);
    assertEquals(0, dataRetriever.getItems_callCount);
    assertEquals(0, dataRetriever.getPagerData_callCount);
  }

  class CallCountingItemsRetriever implements ItemsProviders.ItemsRetriever<BuildPromotion> {
    public int getItems_callCount = 0;
    public int getCount_callCount = 0;
    public int getPagerData_callCount = 0;

    private List<BuildPromotion> myData;

    public CallCountingItemsRetriever(@NotNull List<BuildPromotion> data) {
      myData = data;
    }

    @Nullable
    @Override
    public List<BuildPromotion> getItems() {
      getItems_callCount++;
      return myData;
    }

    @Override
    public Integer getCount() {
      getCount_callCount++;
      return myData.size();
    }

    @Override
    public boolean isCountCheap() {
      return true;
    }

    @Nullable
    @Override
    public PagerData getPagerData() {
      getPagerData_callCount++;
      return null;
    }
  }
}
