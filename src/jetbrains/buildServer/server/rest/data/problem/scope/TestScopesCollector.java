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

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.problem.Orders;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;


public class TestScopesCollector {
  private static final List<String> SUPPORTED_SCOPES = Arrays.asList("suite", "package", "class");
  private static final Orders<TestScope> SUPPORTED_ORDERS = new Orders<TestScope>()
    .add("name",     Comparator.comparing(scope -> scope.getName()))
    .add("duration", Comparator.comparing(scope -> scope.getOrCalcCountersData().getDuration()))
    .add("count",    Comparator.comparing(scope -> scope.getOrCalcCountersData().getCount()));

  public static final String TEST_OCCURRENCES = "testOccurrences";
  public static final String SCOPE_TYPE = "scopeType";
  public static final String ORDER_BY = "orderBy";
  @NotNull
  private final TestOccurrenceFinder myTestOccurrenceFinder;

  public TestScopesCollector(final @NotNull TestOccurrenceFinder finder) {
    myTestOccurrenceFinder = finder;
  }

  public PagedSearchResult<TestScope> getItems(@NotNull Locator locator) {
    locator.addSupportedDimensions(SCOPE_TYPE, TEST_OCCURRENCES, ORDER_BY, PagerData.START, PagerData.COUNT);
    locator.addSupportedDimensions(TestScopeFilter.SUPPORTED_DIMENSIONS);

    String scopeName = locator.getSingleDimensionValue(SCOPE_TYPE);
    if(scopeName == null || !SUPPORTED_SCOPES.contains(scopeName)) {
      throw new BadRequestException("Invalid scope. Only scopes " + String.join(",", SUPPORTED_SCOPES) + " are supported.");
    }

    TestScopeFilter filter = new TestScopeFilter(locator);

    Locator testOccurrencesLocator = new Locator(locator.getSingleDimensionValue(TEST_OCCURRENCES));
    testOccurrencesLocator.setDimension(TestOccurrenceFinder.SCOPE, filter.getLocatorString());

    PagedSearchResult<STestRun> items = myTestOccurrenceFinder.getItems(testOccurrencesLocator.getStringRepresentation());

    Stream<TestScope> scopes = groupByScope(items, filter, scopeName);

    if(locator.isAnyPresent(ORDER_BY)) {
      String orderDimension = locator.getSingleDimensionValue(ORDER_BY);
      //noinspection ConstantConditions
      scopes = scopes.sorted(SUPPORTED_ORDERS.getComparator(orderDimension));
    }

    locator.setDimensionIfNotPresent("count", "100");
    Long count = locator.getSingleDimensionValueAsLong("count");
    Long start = locator.getSingleDimensionValueAsLong("start");

    if(start != null) {
      scopes = scopes.skip(start);
    }

    if(count != null && count != -1) {
      scopes = scopes.limit(count);
    }

    return new PagedSearchResult<TestScope>(scopes.collect(Collectors.toList()), start, count.intValue());
  }

  private Stream<TestScope> groupByScope(@NotNull PagedSearchResult<STestRun> testRuns, @NotNull TestScopeFilter filter, @NotNull String scopeName) {
    Stream<TestScope> scopes;
    switch (scopeName) {
      case "suite":
        scopes = groupBySuite(testRuns, filter);
        break;
      case "package":
        scopes = groupByPackage(testRuns, filter);
        break;
      case "class":
        scopes = groupByClass(testRuns, filter);
        break;
      default:
        // Should never happen as we checked that before, just make java happy.
        throw new BadRequestException("Invalid scope. Only scopes " + String.join(",", SUPPORTED_SCOPES) + " are supported.");
    }

    return scopes;
  }

  private Stream<TestScope> groupBySuite(@NotNull PagedSearchResult<STestRun> testRuns, @NotNull TestScopeFilter testScopeFilter) {
    return groupBySuiteInternal(testRuns, testScopeFilter);
  }

  public Stream<TestScope> groupByPackage(@NotNull PagedSearchResult<STestRun> testRuns, @NotNull TestScopeFilter testScopeFilter) {
    Stream<TestScope> bySuite = groupBySuiteInternal(testRuns, testScopeFilter);
    return groupByPackageInternal(bySuite);
  }

  public Stream<TestScope> groupByClass(@NotNull PagedSearchResult<STestRun> testRuns, @NotNull TestScopeFilter testScopeFilter) {
    Stream<TestScope> bySuite   = groupBySuiteInternal(testRuns, testScopeFilter);
    Stream<TestScope> byPackage = groupByPackageInternal(bySuite);
    return groupByClassInternal(byPackage);
  }

  private static Stream<TestScope> groupBySuiteInternal(@NotNull PagedSearchResult<STestRun> testRuns, @NotNull TestScopeFilter testScopeFilter) {
    Map<String, List<STestRun>> scopes = testRuns.myEntries.stream()
                                                 .filter(testScopeFilter)
                                                 .collect(Collectors.groupingBy(item -> item.getTest().getName().getSuiteWithoutSeparator()));

    return scopes.entrySet().stream().map(entry -> new TestScope(entry.getValue(), entry.getKey()));
  }

  private static Stream<TestScope> groupByPackageInternal(@NotNull Stream<TestScope> suites) {
    return suites.flatMap(testScope -> {
      Map<String, List<STestRun>> byPackage = testScope.getTestRuns().stream().collect(Collectors.groupingBy(item -> item.getTest().getName().getPackageName()));
      return byPackage.entrySet().stream().map(packagePair -> new TestScope(packagePair.getValue(), testScope.getSuite(), packagePair.getKey()));
    });
  }

  private static Stream<TestScope> groupByClassInternal(@NotNull Stream<TestScope> packages) {
    return packages.flatMap(testScope -> {
      Map<String, List<STestRun>> byClass = testScope.getTestRuns().stream().collect(Collectors.groupingBy(item -> item.getTest().getName().getClassName()));
      return byClass.entrySet().stream().map(classPair -> new TestScope(classPair.getValue(), testScope.getSuite(), testScope.getPackage(), classPair.getKey()));
    });
  }
}
