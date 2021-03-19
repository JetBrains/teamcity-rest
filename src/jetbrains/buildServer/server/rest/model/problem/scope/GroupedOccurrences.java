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
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

public class GroupedOccurrences<ITEM> {
  @NotNull
  private final List<ScopeItem<ITEM>> myItems;
  @NotNull
  private final GroupingScope myScope;
  @NotNull
  private final Fields myFields;

  public GroupedOccurrences(@NotNull List<ScopeItem<ITEM>> itemsGroupingScope, @NotNull GroupingScope scope, @NotNull Fields fields) {
    myItems = itemsGroupingScope;
    myScope = scope;
    myFields = fields;
  }

  public Integer getCount() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("count"), () -> myItems.stream().reduce(0, (s, item) -> s + item.getRunsCount(), Integer::sum));
  }

  public Long getDuration() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("duration"), () -> myItems.stream().reduce(0L, (v, item) -> v + item.getDuration(), Long::sum));
  }

  public GroupingScope getScope() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("scope"), myScope);
  }

  public List<ScopeItem<ITEM>> getItems() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("items"), myItems);
  }

  public static enum GroupingScope {
    SUITE,
    PACKAGE,
    CLASS,
    TEST,
  }
}
