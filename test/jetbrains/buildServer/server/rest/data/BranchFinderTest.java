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

package jetbrains.buildServer.server.rest.data;

import java.util.Arrays;
import java.util.stream.Stream;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.BuildTypeOptions;
import jetbrains.buildServer.serverSide.impl.MockVcsSupport;
import jetbrains.buildServer.util.Util;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
  public void testDefaultBranchLookup() {
    final BuildTypeEx bt = registerBuildType("bt", "Project");

    MockVcsSupport vcs = new MockVcsSupport("vcs"); vcs.setDAGBased(true);
    myFixture.getVcsManager().registerVcsSupport(vcs);

    SVcsRootEx parentRoot = myFixture.addVcsRoot(vcs.getName(), "", bt);
    VcsRootInstance root = bt.getVcsRootInstanceForParent(parentRoot);
    assert root != null;

    setBranchSpec(root, "+:*");

    final BuildFinderTestBase.MockCollectRepositoryChangesPolicy changesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(changesPolicy);

    myFixture.addModification(modification().in(root).version("10").parentVersions("1"));
    myFixture.addModification(modification().in(root).version("20").parentVersions("1"));
    myFixture.addModification(modification().in(root).version("30").parentVersions("1"));

    changesPolicy.setCurrentState(root, RepositoryStateData.createVersionState("master", Util.map("master", "10", "branch1", "20", "branch2", "30")));
    myFixture.getVcsModificationChecker().checkForModifications(bt.getVcsRootInstances(), OperationRequestor.UNKNOWN);

    check("buildType:(id:" + bt.getExternalId() + ")", "<default>", "branch1", "branch2");
    check("buildType:(id:" + bt.getExternalId() + "),default:true", "<default>");
    check("buildType:(id:" + bt.getExternalId() + "),default:false", (String) null, "branch1", "branch2");
  }

  private void check(@NotNull final String locator, @Nullable final String defaultBranchName, final String... expectedBranchNames) {
    if(defaultBranchName != null) {
      check(
        locator,
        (expectedName, actualBranchData) -> expectedName.equals(actualBranchData.getName()) && (!expectedName.equals(defaultBranchName) || actualBranchData.isDefaultBranch()),
        Stream.concat(Stream.of(defaultBranchName), Arrays.stream(expectedBranchNames)).toArray(String[]::new)
      );
    } else {
      check(
        locator,
        (expectedName, actualBranchData) -> expectedName.equals(actualBranchData.getName()),
        expectedBranchNames
      );
    }
  }
}
