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

import jetbrains.buildServer.server.rest.data.problem.tree.Scope;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;

public class TestScopeInfo implements Scope {
  public static final TestScopeInfo ROOT = new TestScopeInfo(SProject.ROOT_PROJECT_ID, SProject.ROOT_PROJECT_ID, TestScopeType.PROJECT);

  private final String myName;
  private final TestScopeType myType;
  private final String myId;

  public TestScopeInfo(@NotNull String id, @NotNull String name, @NotNull TestScopeType type) {
    myId = id;
    myName = name;
    myType = type;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getId() {
    return myId;
  }

  @Override
  public boolean isLeaf() {
    return myType == TestScopeType.CLASS;
  }

  @NotNull
  public TestScopeType getType() {
    return myType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TestScopeInfo that = (TestScopeInfo)o;

    if (!myName.equals(that.myName)) return false;
    return myType == that.myType;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + myType.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "TestScopeInfo{" + myType + ": " + myName + "}";
  }
}
