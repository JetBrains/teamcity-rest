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

package jetbrains.buildServer.server.rest.model.problem.scope;

import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.tests.TestName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScopePath {
  public static ScopePath fromTest(@NotNull TestName test, @NotNull Fields fields) {
    return new ScopePath(test.getSuite(), test.getPackageName(), test.getClassName(), test.getTestNameWithoutPrefix(), fields);
  }

  public static ScopePath fromClass(@NotNull TestName test, @NotNull Fields fields) {
    return new ScopePath(test.getSuite(), test.getPackageName(), null, null, fields);
  }

  public static ScopePath fromPackage(@NotNull TestName test, @NotNull Fields fields) {
    return new ScopePath(test.getSuite(), null, null, null, fields);
  }

  public static ScopePath fromSuite(@NotNull TestName test, @NotNull Fields fields) {
    return new ScopePath(null, null, null, null, fields);
  }

  @NotNull
  private final Fields myFields;
  @Nullable
  private final String mySuite;
  @Nullable
  private final String myPackage;
  @Nullable
  private final String myClass;
  @Nullable
  private final String myTest;

  ScopePath(@Nullable String suite, @Nullable String pack, @Nullable String cls, @Nullable String test, @NotNull Fields fields) {
    mySuite = suite;
    myPackage = pack;
    myClass = cls;
    myTest = test;
    myFields = fields;
  }

  public String getSuite() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("suite"), mySuite);
  }

  public String getPackage() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("package"), myPackage);
  }

  @NotNull
  @Override
  public String toString() {
    return mySuite + ":" + myPackage + ":" + myClass + ":" + myTest;
  }
}
