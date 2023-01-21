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

package jetbrains.buildServer.server.rest.data.finder.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.BranchData;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.BuildTypeOptions;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.impl.MockVcsSupport;
import jetbrains.buildServer.util.Util;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.ModificationDataBuilder.modification;

/**
 * @author Yegor.Yarko
 * Date: 10/01/2019
 */
public class BranchFinderTest extends BaseFinderTest<BranchData> {
  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myBranchFinder = new BranchFinder1(myBuildTypeFinder, myServer);
    setFinder(myBranchFinder);
  }

  @Test
  public void testBranchesFromDependencies() {
    final BuildTypeEx bt10 = registerBuildType("10", "Project");
    final BuildTypeEx bt20 = registerBuildType("20", "Project");

    createDependencyChain(bt20, bt10);

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    vcs.setDAGBased(true);
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", bt10);
    SVcsRootEx parentRoot2 = myFixture.addVcsRoot(vcs.getName(), "", bt20);
    VcsRootInstance root1 = bt10.getVcsRootInstanceForParent(parentRoot1);
    VcsRootInstance root2 = bt20.getVcsRootInstanceForParent(parentRoot2);
    assert root1 != null;
    assert root2 != null;

    setBranchSpec(root1, "+:*");
    setBranchSpec(root2, "+:*");

    final BuildFinderTestBase.MockCollectRepositoryChangesPolicy changesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(changesPolicy);

    SVcsModification m10 = myFixture.addModification(modification().in(root1).version("10").parentVersions("1"));
    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("1"));
    SVcsModification m30 = myFixture.addModification(modification().in(root1).version("30").parentVersions("1"));

    SVcsModification n10 = myFixture.addModification(modification().in(root2).version("10").parentVersions("1"));
    SVcsModification n20 = myFixture.addModification(modification().in(root2).version("20").parentVersions("1"));
    SVcsModification n30 = myFixture.addModification(modification().in(root2).version("30").parentVersions("1"));

    changesPolicy.setCurrentState(root1, RepositoryStateData.createVersionState("master", Util.map("master", "10", "branch1", "20", "branch2", "30")));
    changesPolicy.setCurrentState(root2, RepositoryStateData.createVersionState("master", Util.map("master", "10", "branch2", "20", "branch3", "30")));
    myFixture.getVcsModificationChecker().checkForModifications(bt10.getVcsRootInstances(), OperationRequestor.UNKNOWN);
    myFixture.getVcsModificationChecker().checkForModifications(bt20.getVcsRootInstances(), OperationRequestor.UNKNOWN);

    check("buildType:(id:" + bt10.getExternalId() + ")", "<default>", "branch1", "branch2");
    check("buildType:(id:" + bt20.getExternalId() + ")", "<default>", "branch2", "branch3");
    check("buildType:(id:" + bt20.getExternalId() + "),changesFromDependencies:true", "<default>", "branch1", "branch2", "branch3");
    check("buildType:(id:" + bt20.getExternalId() + "),changesFromDependencies:false", "<default>", "branch2", "branch3");

    bt20.setOption(BuildTypeOptions.BT_SHOW_DEPS_CHANGES, true);

    check("buildType:(id:" + bt20.getExternalId() + ")", "<default>", "branch1", "branch2", "branch3");
    check("buildType:(id:" + bt20.getExternalId() + "),changesFromDependencies:false", "<default>", "branch2", "branch3");
    check("buildType:(id:" + bt20.getExternalId() + "),changesFromDependencies:true", "<default>", "branch1", "branch2", "branch3");
    checkExceptionOnItemsSearch(LocatorProcessException.class, "buildType:(id:" + bt20.getExternalId() + "),changesFromDependencies:any");
  }

  @Test
  public void testBranchExists() {
    final BuildTypeEx bt = registerBuildType("10", "Project");

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    vcs.setDAGBased(true);
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot = myFixture.addVcsRoot(vcs.getName(), "", bt);
    VcsRootInstance root = bt.getVcsRootInstanceForParent(parentRoot);
    assert root != null;

    setBranchSpec(root, "+:*");

    final BuildFinderTestBase.MockCollectRepositoryChangesPolicy changesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(changesPolicy);

    SVcsModification m10 = myFixture.addModification(modification().in(root).version("10").parentVersions("1"));
    SVcsModification m20 = myFixture.addModification(modification().in(root).version("20").parentVersions("1"));
    SVcsModification m30 = myFixture.addModification(modification().in(root).version("30").parentVersions("1"));

    changesPolicy.setCurrentState(root, RepositoryStateData.createVersionState("master", Util.map("master", "10", "branch1", "20", "branch2", "30")));
    myFixture.getVcsModificationChecker().checkForModifications(bt.getVcsRootInstances(), OperationRequestor.UNKNOWN);

    assertTrue("There are more than one branch by this locator.", myBranchFinder.itemsExist(new Locator("buildType:(id:" + bt.getExternalId() + ")")));
    assertTrue("Should find default branch.", myBranchFinder.itemsExist(new Locator("buildType:(id:" + bt.getExternalId() + "),default:true")));
    assertTrue("Should find non-default branch.", myBranchFinder.itemsExist(new Locator("buildType:(id:" + bt.getExternalId() + "),default:false")));
    assertTrue("Should find branch by name.", myBranchFinder.itemsExist(new Locator("buildType:(id:" + bt.getExternalId() + "),name:branch1")));
    assertFalse("Should not find branch of there isn't one.", myBranchFinder.itemsExist(new Locator("buildType:(id:" + bt.getExternalId() + "),name:branch999")));
  }

  @Test
  public void testBranchExistsLaziness() {
    final BuildTypeEx bt1 = registerBuildType("10", "Project");
    final BuildTypeEx bt2 = registerBuildType("20", "Project");

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    vcs.setDAGBased(true);
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", bt1);
    SVcsRootEx parentRoot2 = myFixture.addVcsRoot(vcs.getName(), "", bt2);
    VcsRootInstance root1 = bt1.getVcsRootInstanceForParent(parentRoot1);
    VcsRootInstance root2 = bt2.getVcsRootInstanceForParent(parentRoot2);
    assert root1 != null && root2 != null;

    setBranchSpec(root1, "+:*");
    setBranchSpec(root2, "+:*");

    final BuildFinderTestBase.MockCollectRepositoryChangesPolicy changesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(changesPolicy);

    SVcsModification m = myFixture.addModification(modification().in(root1).version("10").parentVersions("1"));
    SVcsModification n = myFixture.addModification(modification().in(root2).version("10").parentVersions("1"));

    changesPolicy.setCurrentState(root1, RepositoryStateData.createVersionState("master", Util.map("branch1", "10")));
    changesPolicy.setCurrentState(root2, RepositoryStateData.createVersionState("master", Util.map("branch1", "10")));
    myFixture.getVcsModificationChecker().checkForModifications(bt1.getVcsRootInstances(), OperationRequestor.UNKNOWN);
    myFixture.getVcsModificationChecker().checkForModifications(bt2.getVcsRootInstances(), OperationRequestor.UNKNOWN);


    assertTrue("Branch 'branch1' exists.", myBranchFinder.itemsExist(new Locator("buildType:(project:(name:Project)),name:branch1")));

    List<String> usedBuildTypes = ((BranchFinder1) myBranchFinder).getBuildTypeIdsUsedInGetBranches();
    assertTrue("Lazy lookup failed: we needed to get branches of exactly one build type, second one is excessive.",
               usedBuildTypes.contains(bt1.getBuildTypeId()) && !usedBuildTypes.contains(bt2.getBuildTypeId()) ||
               usedBuildTypes.contains(bt2.getBuildTypeId()) && !usedBuildTypes.contains(bt1.getBuildTypeId())
    );

    assertFalse("Branch 'branch2' does not exist.", myBranchFinder.itemsExist(new Locator("buildType:(project:(name:Project)),name:branch2")));
    // We must look into both buildtypes as neither of them has a branch that we look for.
    assertContains(((BranchFinder1) myBranchFinder).getBuildTypeIdsUsedInGetBranches(), bt1.getBuildTypeId(), bt2.getBuildTypeId());
  }

  private class BranchFinder1 extends BranchFinder {
    private final List<String> myBuildTypeIdsUsed = new ArrayList<>();
    public BranchFinder1(@NotNull BuildTypeFinder buildTypeFinder, @NotNull ServiceLocator serviceLocator) {
      super(buildTypeFinder, serviceLocator);
    }

    List<String> getBuildTypeIdsUsedInGetBranches() {
      return myBuildTypeIdsUsed;
    }

    @NotNull
    @Override
    protected Stream<BranchData> getBranches(@NotNull SBuildType buildType,
                                             @NotNull BranchFinder.BranchSearchOptions branchSearchOptions,
                                             boolean computeTimestamps,
                                             @NotNull Filter<SBuildType> dependenciesFilter) {
      myBuildTypeIdsUsed.add(buildType.getBuildTypeId());
      return super.getBranches(buildType, branchSearchOptions, computeTimestamps, dependenciesFilter);
    }
  }

  private void check(final String locator, final String defaultBranchName, final String... branchNames) {
    check(locator,
          (s, branchData) -> s.equals(branchData.getName()) && (!s.equals(defaultBranchName) || branchData.isDefaultBranch()),
          Stream.concat(Stream.of(defaultBranchName), Arrays.stream(branchNames)).toArray(String[]::new));
  }
}
