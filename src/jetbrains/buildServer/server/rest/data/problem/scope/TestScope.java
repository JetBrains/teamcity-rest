/*
 * Copyright 2000-2021 JetBrains s.r.o.
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
  private TestCountersData myCountersData;
  @Nullable
  private List<Scope> myPath;

  public static TestScope withBuildType(@NotNull TestScope source, @NotNull List<STestRun> testRuns, @NotNull SBuildType buildType) {
    return new TestScope(testRuns, source.getSuite(), source.getPackage(), source.getClass1(), source.myType, buildType);
  }

  public TestScope(@NotNull List<STestRun> testRuns, @NotNull String suite) {
    this(testRuns, suite, null, null, TestScopeType.SUITE, null);
  }

  public TestScope(@NotNull List<STestRun> testRuns, @NotNull String suite, @NotNull String pack) {
    this(testRuns, suite, pack, null, TestScopeType.PACKAGE, null);
  }

  public TestScope(@NotNull List<STestRun> testRuns, @NotNull String suite, @NotNull String pack, @NotNull String clazz) {
    this(testRuns, suite, pack, clazz, TestScopeType.CLASS, null);
  }

  private TestScope(@NotNull List<STestRun> testRuns, @NotNull String suite, @Nullable String pack, @Nullable String clazz, @NotNull TestScopeType type, @Nullable SBuildType buildType) {
    myTestRuns = testRuns;
    mySuite = suite;
    myPackage = pack;
    myClass = clazz;
    myBuildType = buildType;
    myType = type;
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

      myPath.add(new TestScopeInfo(ancestorId, ancestorId, TestScopeType.PROJECT));
    }
    myPath.add(new TestScopeInfo(myBuildType.getExternalId(), myBuildType.getExternalId(), TestScopeType.BUILD_TYPE));

    String suiteId = Hashing.sha1().hashString(myBuildType.getExternalId() + mySuite, Charsets.UTF_8).toString();
    myPath.add(new TestScopeInfo(suiteId, mySuite, TestScopeType.SUITE));

    String packageName = myPackage == null ? "" : myPackage;
    String packageId = Hashing.sha1().hashString(myBuildType.getExternalId() + mySuite + packageName, Charsets.UTF_8).toString();
    myPath.add(new TestScopeInfo(packageId, packageName, TestScopeType.PACKAGE));

    String className = myClass == null ? "" : myClass;
    String classId = Hashing.sha1().hashString(myBuildType.getExternalId() + mySuite + packageName + className, Charsets.UTF_8).toString();
    myPath.add(new TestScopeInfo(classId, className, TestScopeType.CLASS));

    return myPath;
  }

  @NotNull
  @Override
  public Collection<STestRun> getData() {
    return myTestRuns;
  }
}
