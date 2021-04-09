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

import java.util.List;
import jetbrains.buildServer.server.rest.data.problem.TestCountersData;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestScope {
  private final List<STestRun> myTestRuns;
  @NotNull
  private final String mySuite;
  @Nullable
  private final String myPackage;
  @Nullable
  private final String myClass;
  @NotNull
  private final Type myType;
  @Nullable
  private final SBuildType myBuildType;
  @Nullable
  private TestCountersData myCountersData;

  public static TestScope withBuildType(@NotNull TestScope source, @NotNull List<STestRun> testRuns, @NotNull SBuildType buildType) {
    return new TestScope(testRuns, source.getSuite(), source.getPackage(), source.getClass1(), source.myType, buildType);
  }

  public TestScope(@NotNull List<STestRun> testRuns, @NotNull String suite) {
    this(testRuns, suite, null, null, Type.SUITE, null);
  }

  public TestScope(@NotNull List<STestRun> testRuns, @NotNull String suite, @NotNull String pack) {
    this(testRuns, suite, pack, null, Type.PACKAGE, null);
  }

  public TestScope(@NotNull List<STestRun> testRuns, @NotNull String suite, @NotNull String pack, @NotNull String clazz) {
    this(testRuns, suite, pack, clazz, Type.CLASS, null);
  }

  private TestScope(@NotNull List<STestRun> testRuns, @NotNull String suite, @Nullable String pack, @Nullable String clazz, @NotNull Type type, @Nullable SBuildType buildType) {
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
    return null; // never happens
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

  private enum Type {
    SUITE, PACKAGE, CLASS
  }
}
