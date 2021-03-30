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
import java.util.stream.Stream;
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;


public class ScopesCollector {
  public static Stream<Scope> groupBySuite(@NotNull List<STestRun> testRuns, @NotNull ScopeFilter scopeFilter) {
    return groupBySuiteInternal(testRuns, scopeFilter);
  }

  public static Stream<Scope> groupByPackage(@NotNull List<STestRun> testRuns, @NotNull ScopeFilter scopeFilter) {
    Stream<Scope> bySuite = groupBySuiteInternal(testRuns, scopeFilter);
    return groupByPackageInternal(bySuite);
  }

  public static Stream<Scope> groupByClass(@NotNull List<STestRun> testRuns, @NotNull ScopeFilter scopeFilter) {
    Stream<Scope> bySuite   = groupBySuiteInternal(testRuns, scopeFilter);
    Stream<Scope> byPackage = groupByPackageInternal(bySuite);
    return groupByClassInternal(byPackage);
  }

  private static Stream<Scope> groupBySuiteInternal(@NotNull List<STestRun> testRuns, @NotNull ScopeFilter scopeFilter) {
    Map<String, List<STestRun>> scopes = testRuns.stream()
                                                 .filter(scopeFilter)
                                                 .collect(Collectors.groupingBy(item -> item.getTest().getName().getSuite()));

    return scopes.entrySet().stream().map(entry -> new Scope(entry.getValue(), entry.getKey()));
  }

  private static Stream<Scope> groupByPackageInternal(@NotNull Stream<Scope> suites) {
    return suites.flatMap(scope -> {
      Map<String, List<STestRun>> byPackage = scope.getTestRuns().stream().collect(Collectors.groupingBy(item -> item.getTest().getName().getPackageName()));
      return byPackage.entrySet().stream().map(packagePair -> new Scope(packagePair.getValue(), scope.getSuite(), packagePair.getKey()));
    });
  }

  private static Stream<Scope> groupByClassInternal(@NotNull Stream<Scope> packages) {
    return packages.flatMap(scope -> {
      Map<String, List<STestRun>> byClass = scope.getTestRuns().stream().collect(Collectors.groupingBy(item -> item.getTest().getName().getClassName()));
      return byClass.entrySet().stream().map(classPair -> new Scope(classPair.getValue(), scope.getSuite(), scope.getPackage(), classPair.getKey()));
    });
  }
}
