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

package jetbrains.buildServer.server.rest.data.changelog;

import java.util.function.Function;
import jetbrains.buildServer.controllers.BranchBeanFactory;
import jetbrains.buildServer.controllers.buildType.BuildTypeChangeLogBeanProvider;
import jetbrains.buildServer.controllers.buildType.BuildTypePendingChangeLogBeanProvider;
import jetbrains.buildServer.controllers.buildType.tabs.ChangeLogBean;
import jetbrains.buildServer.controllers.buildType.tabs.ChangeLogFilter;
import jetbrains.buildServer.controllers.buildType.tabs.DAGLayoutFactoryImpl;
import jetbrains.buildServer.controllers.buildType.tabs.GraphFactory;
import jetbrains.buildServer.controllers.project.ProjectChangeLogBeanProvider;
import jetbrains.buildServer.controllers.viewLog.BuildChangeLogBeanProvider;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.changeLog.ChangeLogBeanCollector;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.BranchGroupsService;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.versionedSettings.VersionedSettingsManager;
import jetbrains.buildServer.users.SUser;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

@Test
public class ChangeLogBeanCollectorTest extends BaseFinderTest {
  private ChangeLogBeanCollector myCollector;
  private BuildChangeLogBeanProvider myBuildBeanProviderMock;
  private BuildTypeChangeLogBeanProvider myBuildTypeBeanProviderMock;
  private BuildTypePendingChangeLogBeanProvider myBuildTypePendingBeanProviderMock;
  private ProjectChangeLogBeanProvider myProjectBeanProviderMock;

  private ChangeLogBean myDummyBean;

  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();

    myBuildBeanProviderMock = Mockito.mock(BuildChangeLogBeanProvider.class);
    myBuildTypeBeanProviderMock = Mockito.mock(BuildTypeChangeLogBeanProvider.class);
    myBuildTypePendingBeanProviderMock = Mockito.mock(BuildTypePendingChangeLogBeanProvider.class);
    myProjectBeanProviderMock = Mockito.mock(ProjectChangeLogBeanProvider.class);

    myCollector = new ChangeLogBeanCollector(
      myBuildPromotionFinder,
      myProjectFinder,
      myBuildTypeFinder,
      myBranchFinder,
      myBuildBeanProviderMock,
      myBuildTypeBeanProviderMock,
      myBuildTypePendingBeanProviderMock,
      myProjectBeanProviderMock,
      new BranchBeanFactory(myFixture.getSingletonService(BranchGroupsService.class), myServer.getSecurityContext()),
      myFixture.getSecurityContext()
    );

    SUser admin = createAdmin("admin");
    myFixture.getSecurityContext().setAuthorityHolder(admin);
    myDummyBean = new ChangeLogBean(
      new ChangeLogFilter(admin),
      new GraphFactory(myFixture.getVcsHistory(), myFixture.getVcsManager(), new DAGLayoutFactoryImpl(myFixture.getVcsHistory())),
      myFixture.getSingletonService(VersionedSettingsManager.class),
      false
    );
  }

  public void testPathDimension_singleValue() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> filter.getPathRequirement().getValue().equals("Expelliarmus") && filter.getPathRequirement().getRequirementType() == RequirementType.EQUALS),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("path:Expelliarmus,buildType:" + myBuildType.getExternalId())));
  }

  public void testPathDimension_explicitValueCondition() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> filter.getPathRequirement().getValue().equals("Expelliarmus") && filter.getPathRequirement().getRequirementType() == RequirementType.ENDS_WITH),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("path:(value:Expelliarmus,matchType:ends-with),buildType:" + myBuildType.getExternalId())));
  }

  public void testCommentDimension_singleValue() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> filter.getCommentRequirement().getValue().equals("ExpectoPatronum") && filter.getCommentRequirement().getRequirementType() == RequirementType.EQUALS),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("comment:ExpectoPatronum,buildType:" + myBuildType.getExternalId())));
  }

  public void testCommentDimension_explicitValueCondition() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> filter.getCommentRequirement().getValue().equals("ExpectoPatronum") && filter.getCommentRequirement().getRequirementType() == RequirementType.ENDS_WITH),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("comment:(value:ExpectoPatronum,matchType:ends-with),buildType:" + myBuildType.getExternalId())));
  }

  public void testRevisionDimension_singleValue() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> filter.getRevisionRequirement().getValue().equals("Riddikulus") && filter.getRevisionRequirement().getRequirementType() == RequirementType.EQUALS),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("revision:Riddikulus,buildType:" + myBuildType.getExternalId())));
  }

  public void testRevisionDimension_explicitValueCondition() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> filter.getPathRequirement().getValue().equals("Riddikulus") && filter.getPathRequirement().getRequirementType() == RequirementType.ENDS_WITH),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("path:(value:Riddikulus,matchType:ends-with),buildType:" + myBuildType.getExternalId())));
  }


  public void testIncludeBuilds() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> filter.isShowBuilds()),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("includeBuilds:true,buildType:" + myBuildType.getExternalId())));
  }

  public void testNotIncludeBuilds() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> !filter.isShowBuilds()),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("includeBuilds:false,buildType:" + myBuildType.getExternalId())));
  }

  public void testChangesFromDepsWhenSpecified() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> filter.isShowChangesFromDependencies()),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("changesFromDependencies:true,buildType:" + myBuildType.getExternalId())));
  }

  public void testChangesFromDepsWhenSpecifiedFalse() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> !filter.isShowChangesFromDependencies()),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("changesFromDependencies:false,buildType:" + myBuildType.getExternalId())));
  }

  public void testPagination() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> filter.getPage() == 2 && filter.getRecordsPerPage() == 17),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("page:2,pageSize:17,buildType:" + myBuildType.getExternalId())));
  }

  public void testPaginationWithFromTo() {
    // We check this because in the current implementation setting 'from' and 'to' resets 'page' in a filter, so we need to be sure that we do not lose 'page'
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> filter.getPage() == 2 && filter.getRecordsPerPage() == 17 && filter.getFrom().equals("PhilosophersStone") && filter.getTo().equals("DeathlyHallows")),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("page:2,pageSize:17,fromBuildNumber:PhilosophersStone,toBuildNumber:DeathlyHallows,buildType:" + myBuildType.getExternalId())));
  }

  public void testUser() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> filter.isHasUser() && filter.getUserId().equals("id:HarryPotter")),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("user:id:HarryPotter,buildType:" + myBuildType.getExternalId())));
  }

  public void testUserSingleValueIsNotSupported() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> filter.isHasUser() && filter.getUserId().equals("HarryPotter")),
      eqq(false)
    )).thenReturn(myDummyBean);

    try {
      assertEquals(myDummyBean, myCollector.getItem(Locator.locator("user:HarryPotter,buildType:" + myBuildType.getExternalId())));
      fail();
    } catch (BadRequestException ignore) {
      // pass
    }
  }

  public void testVcsUsername() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> filter.isHasUser() && filter.getUserId().equals("vcs:RonWeasley")),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("vcsUsername:RonWeasley,buildType:" + myBuildType.getExternalId())));
  }

  public void testBuildDimension() {
    SFinishedBuild build = build().in(myBuildType).finish();
    when(myBuildBeanProviderMock.createChangeLogBean(
      eqq(build.getBuildPromotion()),
      trueThat(filter -> filter.isShowGraph() && !filter.isHasUser()),
      eqq(false)
   )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("build:" + build.getBuildId())));
  }

  public void testBuildTypeDimensionDefaultsToAllBranches() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> filter.isShowGraph() && !filter.isHasUser()),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("buildType:" + myBuildType.getExternalId())));
  }

  public void testExplicitAllBranches() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> filter.isShowGraph() && !filter.isHasUser()),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("branch:(default:any),buildType:" + myBuildType.getExternalId())));
  }

  public void testDefaultBranchOnly() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isDefaultBranch()),
      trueThat(filter -> filter.isShowGraph() && !filter.isHasUser()),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("branch:(default:true),buildType:" + myBuildType.getExternalId())));
  }

  public void testBranchGroup() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> !bb.isSingleBranch() && bb.getUserBranch().equals("__groupId__")),
      trueThat(filter -> filter.isShowGraph() && !filter.isHasUser()),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("branch:(group:groupId),buildType:" + myBuildType.getExternalId())));
  }

  public void testBranchPolicyOnly() {
    // Documenting current behaviour, we ignore branch policy completely for now
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> filter.isShowGraph() && !filter.isHasUser()),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("branch:(policy:ACTIVE_HISTORY_AND_ACTIVE_VCS_BRANCHES),buildType:" + myBuildType.getExternalId())));
  }

  public void testBranchSingleValueNotSupported() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      any(),
      any(),
      eqq(false)
    )).thenReturn(myDummyBean);

    try {
      myCollector.getItem(Locator.locator("branch:singleValue,buildType:" + myBuildType.getExternalId()));
      fail("Single value branch dimension is not supported. It is unclear how to convert single value to branch bean.");
    } catch (BadRequestException ignored) {
      // pass
    }

    try {
      myCollector.getItem(Locator.locator("branch:(name:singleValue),buildType:" + myBuildType.getExternalId()));
      fail("Simple name in branch dimension is not supported. It is unclear how to convert simple name to branch bean.");
    } catch (BadRequestException ignored) {
      // pass
    }
  }

  public void testWithSimpleBranch() {
    when(myBuildTypeBeanProviderMock.createChangeLogBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isSingleBranch() && bb.getUserBranch().equals("some_branch")),
      trueThat(filter -> filter.isShowGraph() && !filter.isHasUser()),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("branch:(name:(matchType:equals,value:some_branch)),buildType:" + myBuildType.getExternalId())));
  }

  public void testBuildTypePendingDimensionDefaultsToAllBranches() {
    when(myBuildTypePendingBeanProviderMock.createPendingChangesBean(
      eqq(myBuildType),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> filter.isShowGraph() && !filter.isHasUser()),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("pending:true,buildType:" + myBuildType.getExternalId())));
  }

  public void testProjectDimensionDefaultsToAllBranches() {
    when(myProjectBeanProviderMock.createBean(
      eqq(myProject),
      trueThat(bb -> bb.isAllBranches()),
      trueThat(filter -> filter.isShowGraph() && !filter.isHasUser()),
      eqq(false)
    )).thenReturn(myDummyBean);

    assertEquals(myDummyBean, myCollector.getItem(Locator.locator("project:" + myProject.getExternalId())));
  }

  private <T> T eqq(T value) {
    // Can't make a static import for eq as we already have a method with such name.
    return Mockito.eq(value);
  }

  private <T> T trueThat(@NotNull Function<T, Boolean> conditionChecker) {
    // Add this for the sake of supporting lambda
    return Mockito.argThat(new BaseMatcher<T>() {
      @Override
      public boolean matches(Object o) {
        try {
          return conditionChecker.apply((T) o);
        } catch (ClassCastException ignore) {
          return false;
        }
      }

      @Override
      public void describeTo(Description description) {}
    });
  }
}
