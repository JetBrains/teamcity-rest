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

package jetbrains.buildServer.server.rest.data.problem.scope;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.server.rest.data.problem.TestCountersData;
import jetbrains.buildServer.server.rest.data.problem.tree.LeafInfo;
import jetbrains.buildServer.server.rest.data.problem.tree.Scope;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestScope implements LeafInfo<STestRun, TestCountersData> {
  private final List<STestRun> myTestRuns;
  @NotNull
  private final String mySuite;
  @Nullable
  private final String myPackage;
  @Nullable
  private final String myClass;
  @NotNull
  private final TestScopeType myType;
  @Nullable
  private final SBuildType myBuildType;
  @Nullable
  private final BuildPromotion myBuildPromotion;
  @Nullable
  private TestCountersData myCountersData;
  @Nullable
  private List<Scope> myPath;

  public static TestScope withBuild(@NotNull TestScope source, @NotNull List<STestRun> testRuns, @NotNull BuildPromotion promo) {
    return new TestScope(testRuns, source.getSuite(), source.getPackage(), source.getClass1(), source.myType, promo.getBuildType(), promo);
  }

  public TestScope(@NotNull List<STestRun> testRuns, @NotNull String suite) {
    this(testRuns, suite, null, null, TestScopeType.SUITE, null, null);
  }

  public TestScope(@NotNull List<STestRun> testRuns, @NotNull String suite, @NotNull String pack) {
    this(testRuns, suite, pack, null, TestScopeType.PACKAGE, null, null);
  }

  public TestScope(@NotNull List<STestRun> testRuns, @NotNull String suite, @NotNull String pack, @NotNull String clazz) {
    this(testRuns, suite, pack, clazz, TestScopeType.CLASS, null, null);
  }

  private TestScope(@NotNull List<STestRun> testRuns,
                    @NotNull String suite,
                    @Nullable String pack,
                    @Nullable String clazz,
                    @NotNull TestScopeType type,
                    @Nullable SBuildType buildType,
                    @Nullable BuildPromotion promotion) {
    myTestRuns = testRuns;
    mySuite = suite;
    myPackage = pack;
    myClass = clazz;
    myBuildType = buildType;
    myType = type;
    myBuildPromotion = promotion;
  }

  @NotNull
  public List<STestRun> getTestRuns() {
    return myTestRuns;
  }

  @NotNull
  public TestCountersData getOrCalcCountersData() {
    if(myCountersData == null) {
      myCountersData = new TestCountersData(myTestRuns);
    }

    return myCountersData;
  }

  @Nullable
  public String getName() {
    switch (myType) {
      case SUITE:
        return mySuite;
      case CLASS:
        return myClass;
      case PACKAGE:
        return myPackage;
    }
    return null;
  }

  @NotNull
  public String getSuite() {
    return mySuite;
  }

  @Nullable
  public String getPackage() {
    return myPackage;
  }

  @Nullable
  public String getClass1() {
    return myClass;
  }

  @Nullable
  public SBuildType getBuildType() {
    return myBuildType;
  }

  @Nullable
  public BuildPromotion getBuildPromotion() {
    return myBuildPromotion;
  }

  @NotNull
  @Override
  public TestCountersData getCounters() {
    return getOrCalcCountersData();
  }

  @NotNull
  @Override
  public Iterable<Scope> getPath() {
    if(myBuildType == null) {
      return Collections.emptyList();
    }

    if(myPath != null) {
      return myPath;
    }

    myPath = new ArrayList<>();

    for (SProject ancestor : myBuildType.getProject().getProjectPath()) {
      String ancestorId = ancestor.getExternalId();

      String id = Hashing.sha1().hashString("P" + ancestor.getExternalId(), Charsets.UTF_8).toString();
      myPath.add(new TestScopeInfo(id, ancestorId, TestScopeType.PROJECT));
    }
    String btId = Hashing.sha1().hashString("BT" + myBuildType.getInternalId(), Charsets.UTF_8).toString();
    myPath.add(new TestScopeInfo(btId, myBuildType.getExternalId(), TestScopeType.BUILD_TYPE));

    if(myBuildPromotion != null) {
      String buildNodeId = Hashing.sha1().hashString("B" + Long.toString(myBuildPromotion.getId()), Charsets.UTF_8).toString();
      myPath.add(new TestScopeInfo(buildNodeId, Long.toString(myBuildPromotion.getId()), TestScopeType.BUILD));
    }

    String suiteId = Hashing.sha1().hashString(myBuildType.getExternalId() + "s" + mySuite, Charsets.UTF_8).toString();
    myPath.add(new TestScopeInfo(suiteId, mySuite, TestScopeType.SUITE));

    String packageName = myPackage == null ? "" : myPackage;
    String packageId = Hashing.sha1().hashString(myBuildType.getExternalId() + "s" + mySuite + "p" + packageName, Charsets.UTF_8).toString();
    myPath.add(new TestScopeInfo(packageId, packageName, TestScopeType.PACKAGE));

    String className = myClass == null ? "" : myClass;
    String classId = Hashing.sha1().hashString(myBuildType.getExternalId() + "s" + mySuite + "p" + packageName + "c" + className, Charsets.UTF_8).toString();
    myPath.add(new TestScopeInfo(classId, className, TestScopeType.CLASS));

    return myPath;
  }

  @NotNull
  @Override
  public Collection<STestRun> getData() {
    return myTestRuns;
  }
}
