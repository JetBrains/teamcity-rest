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
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Scope {
  private final List<STestRun> myTestRuns;
  @Nullable
  private final String mySuite;
  @Nullable
  private final String myPackage;
  @Nullable
  private final String myClass;

  public Scope(@NotNull List<STestRun> testRuns, @NotNull String suite) {
    myTestRuns = testRuns;
    mySuite = suite;
    myPackage = null;
    myClass = null;
  }

  public Scope(@NotNull List<STestRun> testRuns, @Nullable String suite, @Nullable String pack) {
    myTestRuns = testRuns;
    mySuite = suite;
    myPackage = pack;
    myClass = null;
  }

  public Scope(@NotNull List<STestRun> testRuns, @NotNull String suite, @NotNull String pack, @NotNull String clazz) {
    myTestRuns = testRuns;
    mySuite = suite;
    myPackage = pack;
    myClass = clazz;
  }

  public List<STestRun> getTestRuns() {
    return myTestRuns;
  }

  @Nullable
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
}
