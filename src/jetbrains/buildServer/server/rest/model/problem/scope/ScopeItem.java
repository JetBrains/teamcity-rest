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

import java.util.List;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrences;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;

public class ScopeItem<ITEM> {
  private static long idCounter = 0;
  @NotNull
  private final ScopePath myPath;
  @NotNull
  private final List<STestRun> myRuns;
  @NotNull
  private final ITEM myItem;
  @NotNull
  private final Fields myFields;
  @NotNull
  private final BeanContext myContext;

  private final long myId;

  public ScopeItem(@NotNull ITEM item, @NotNull List<STestRun> runs, @NotNull ScopePath path, @NotNull Fields fields, @NotNull BeanContext context) {
    myItem = item;
    myRuns = runs;
    myPath = path;
    myFields = fields;
    myContext = context;
    myId = idCounter++;
  }

  public String getId() {
    // TODO: Implement id generation
    return ValueWithDefault.decideDefault(myFields.isIncluded("id"), myPath.toString() + myId + ":TODO:NOT_IMPLEMENTED");
  }

  public ScopePath getPath() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("path"), myPath);
  }

  public ITEM getItem() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("item"), myItem);
  }

  public Integer getTestCount() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("testCount"), myRuns.size());
  }

  public Long getDuration() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("duration"), () -> myRuns.stream().reduce(0L, (v, test) -> v + test.getDuration(), Long::sum));
  }

  int getRunsCount() {
    return myRuns.size();
  }

  public TestOccurrences getOccurrences() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("occurrences"),
      // TODO: add pager data
      new TestOccurrences(myRuns, null, null, null, myFields.getNestedField("occurrences"), myContext)
    );
  }
}