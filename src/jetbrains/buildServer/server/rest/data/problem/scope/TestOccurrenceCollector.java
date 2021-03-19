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
import java.util.Map;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.problem.Test;
import jetbrains.buildServer.server.rest.model.problem.scope.GroupedOccurrences;
import jetbrains.buildServer.server.rest.model.problem.scope.NamedScope;
import jetbrains.buildServer.server.rest.model.problem.scope.ScopeItem;
import jetbrains.buildServer.server.rest.model.problem.scope.ScopePath;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.tests.TestName;
import org.jetbrains.annotations.NotNull;


public class TestOccurrenceCollector {
  public static GroupedOccurrences<Test> groupByTest(@NotNull List<STestRun> testRuns, @NotNull Locator scopeLocator, @NotNull Fields fields, @NotNull BeanContext context) {
    ScopeFilter filter = new ScopeFilter(scopeLocator);

    Map<TestName, List<STestRun>> scopes = testRuns.stream()
                                                 .filter(filter)
                                                 .collect(Collectors.groupingBy(item -> item.getTest().getName()));

    Fields itemFields = fields.getNestedField("items");
    Fields testFields = itemFields.getNestedField("item");
    List<ScopeItem<Test>> items = scopes.entrySet().stream().map(group -> {
      STest realTest = group.getValue().get(0).getTest();
      Test test = new Test(realTest, context, testFields);
      ScopePath path = ScopePath.fromTest(group.getKey(), itemFields.getNestedField("path"));

      return new ScopeItem<>(test, group.getValue(), path, itemFields, context);
    }).collect(Collectors.toList());

    return new GroupedOccurrences<>(items, GroupedOccurrences.GroupingScope.TEST, fields);
  }

  public static GroupedOccurrences<NamedScope> groupByPackage(@NotNull List<STestRun> testRuns, @NotNull Locator scopeLocator, @NotNull Fields fields, @NotNull BeanContext context) {
    ScopeFilter filter = new ScopeFilter(scopeLocator);

    Map<String, List<STestRun>> scopes = testRuns.stream()
                                                   .filter(filter)
                                                   .collect(Collectors.groupingBy(item -> item.getTest().getName().getPackageName()));

    Fields itemFields = fields.getNestedField("items");
    Fields packageFields = itemFields.getNestedField("item");
    List<ScopeItem<NamedScope>> items = scopes.entrySet().stream().map(group -> {
      NamedScope pack = new NamedScope(group.getKey(), packageFields);

      STest realTest = group.getValue().get(0).getTest();
      ScopePath path = ScopePath.fromPackage(realTest.getName(), itemFields.getNestedField("path"));

      return new ScopeItem<>(pack, group.getValue(), path, itemFields, context);
    }).collect(Collectors.toList());

    return new GroupedOccurrences<>(items, GroupedOccurrences.GroupingScope.PACKAGE, fields);
  }

  public static GroupedOccurrences<NamedScope> groupBySuite(@NotNull List<STestRun> testRuns, @NotNull Locator scopeLocator,  @NotNull Fields fields, @NotNull BeanContext context) {
    ScopeFilter filter = new ScopeFilter(scopeLocator);

    Map<String, List<STestRun>> scopes = testRuns.stream()
                                                 .filter(filter)
                                                 .collect(Collectors.groupingBy(item -> item.getTest().getName().getSuite()));

    Fields itemFields = fields.getNestedField("items");
    Fields packageFields = itemFields.getNestedField("item");
    List<ScopeItem<NamedScope>> items = scopes.entrySet().stream().map(group -> {
      NamedScope pack = new NamedScope(group.getKey(), packageFields);

      STest realTest = group.getValue().get(0).getTest();
      ScopePath path = ScopePath.fromSuite(realTest.getName(), itemFields.getNestedField("path"));

      return new ScopeItem<>(pack, group.getValue(), path, itemFields, context);
    }).collect(Collectors.toList());

    return new GroupedOccurrences<>(items, GroupedOccurrences.GroupingScope.SUITE, fields);
  }

  public static GroupedOccurrences<NamedScope> groupByClass(@NotNull List<STestRun> testRuns, @NotNull Locator scopeLocator,  @NotNull Fields fields, @NotNull BeanContext context) {
    ScopeFilter filter = new ScopeFilter(scopeLocator);

    Map<String, List<STestRun>> scopes = testRuns.stream()
                                                 .filter(filter)
                                                 .collect(Collectors.groupingBy(item -> item.getTest().getName().getClassName()));

    Fields itemFields = fields.getNestedField("items");
    Fields packageFields = itemFields.getNestedField("item");
    List<ScopeItem<NamedScope>> items = scopes.entrySet().stream().map(group -> {
      NamedScope pack = new NamedScope(group.getKey(), packageFields);

      STest realTest = group.getValue().get(0).getTest();
      ScopePath path = ScopePath.fromClass(realTest.getName(), itemFields.getNestedField("path"));

      return new ScopeItem<>(pack, group.getValue(), path, itemFields, context);
    }).collect(Collectors.toList());

    return new GroupedOccurrences<>(items, GroupedOccurrences.GroupingScope.CLASS, fields);
  }
}
