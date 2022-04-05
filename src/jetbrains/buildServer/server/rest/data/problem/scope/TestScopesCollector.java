/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.problem.Orders;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.dependency.BuildDependency;
import jetbrains.buildServer.util.StringUtil;
import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;


public class TestScopesCollector {
  private static final String MISSING_BUILD_TYPE_ID = "<missingBuildType>";
  private static final List<String> SUPPORTED_SCOPES = Arrays.asList("suite", "package", "class");
  private static final Orders<TestScope> SUPPORTED_ORDERS = new Orders<TestScope>()
    .add("name",     Comparator.comparing(scope -> scope.getName()))
    .add("duration", Comparator.comparing(scope -> scope.getOrCalcCountersData().getDuration()))
    .add("count",    Comparator.comparing(scope -> scope.getOrCalcCountersData().getCount()));

  public static final String TEST_OCCURRENCES = "testOccurrences";
  public static final String SCOPE_TYPE = "scopeType";
  public static final String ORDER_BY = "orderBy";
  public static final String SPLIT_BY_BUILD_TYPE = "splitByBuildType";
  public static final String GROUP_SPLIT_TESTS = "groupSplitTests";
  @NotNull
  private final TestOccurrenceFinder myTestOccurrenceFinder;
  @NotNull
  private final TestScopeFilterProducer myTestScopeFilterProducer;

  private static final String DEFAULT_COUNT = "100";

  public TestScopesCollector(final @NotNull TestOccurrenceFinder finder, final @NotNull TestScopeFilterProducer testScopeFilterProducer) {
    myTestOccurrenceFinder = finder;
    myTestScopeFilterProducer = testScopeFilterProducer;
  }

  public PagedSearchResult<TestScope> getPagedItems(@NotNull Locator locator) {
    locator.setDimensionIfNotPresent(PagerData.COUNT, DEFAULT_COUNT);
    Long count = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    Long start = locator.getSingleDimensionValueAsLong(PagerData.START);

    Stream<TestScope> scopes = getItems(locator);

    if(start != null) {
      scopes = scopes.skip(start);
    }

    if(count != null && count != -1) {
      scopes = scopes.limit(count);
    }

    // We set count value to DEFAULT_COUNT earlier if it was not present
    return new PagedSearchResult<TestScope>(scopes.collect(Collectors.toList()), start, count.intValue());
  }

  public Stream<TestScope> getItems(@NotNull Locator locator) {
    locator.addSupportedDimensions(SCOPE_TYPE, TEST_OCCURRENCES, ORDER_BY, PagerData.START, PagerData.COUNT);
    locator.addSupportedDimensions(TestScopeFilterImpl.SUPPORTED_DIMENSIONS);

    String scopeName = locator.getSingleDimensionValue(SCOPE_TYPE);
    if(scopeName == null || !SUPPORTED_SCOPES.contains(scopeName)) {
      throw new BadRequestException("Invalid scope. Only scopes " + String.join(",", SUPPORTED_SCOPES) + " are supported.");
    }

    TestScopeFilter filter = myTestScopeFilterProducer.createFromLocator(locator);

    Locator testOccurrencesLocator = new Locator(locator.getSingleDimensionValue(TEST_OCCURRENCES));

    if(!StringUtil.isEmpty(filter.getLocatorString()))
      testOccurrencesLocator.setDimension(TestOccurrenceFinder.SCOPE, filter.getLocatorString());

    PagedSearchResult<STestRun> items = myTestOccurrenceFinder.getItems(testOccurrencesLocator.getStringRepresentation());

    Stream<TestScope> scopes = groupByScope(items, filter, scopeName);

    if(locator.isAnyPresent(ORDER_BY)) {
      String orderDimension = locator.getSingleDimensionValue(ORDER_BY);
      //noinspection ConstantConditions
      scopes = scopes.sorted(SUPPORTED_ORDERS.getComparator(orderDimension));
    }

    if(locator.isAnyPresent(SPLIT_BY_BUILD_TYPE)) {
      Boolean split = locator.getSingleDimensionValueAsBoolean(SPLIT_BY_BUILD_TYPE);
      if(BooleanUtils.isTrue(split)) {
        boolean groupSlitTests = BooleanUtils.isNotFalse(locator.getSingleDimensionValueAsBoolean(GROUP_SPLIT_TESTS));
        scopes = splitByBuildType(scopes, groupSlitTests);
      }
    }

    return scopes;
  }

  private Stream<TestScope> groupByScope(@NotNull PagedSearchResult<STestRun> testRuns, @NotNull TestScopeFilter filter, @NotNull String scopeName) {
    Stream<STestRun> testRunStream = testRuns.myEntries.stream();
    Stream<TestScope> scopes;
    switch (scopeName) {
      case "suite":
        scopes = groupBySuite(testRunStream, filter);
        break;
      case "package":
        scopes = groupByPackage(testRunStream, filter);
        break;
      case "class":
        scopes = groupByClass(testRunStream, filter);
        break;
      default:
        // Should never happen as we checked that before, just make java happy.
        throw new BadRequestException("Invalid scope. Only scopes " + String.join(",", SUPPORTED_SCOPES) + " are supported.");
    }

    return scopes;
  }

  public Stream<TestScope> splitByBuildType(@NotNull Stream<TestScope> testScopes, boolean groupSlitTests) {
    Map<String, SBuildType> encounteredBuildTypes = new HashMap<>();

    Function<STestRun, String> getIdAndRememberBuildType = (STestRun testRun) -> {
      SBuildType bt = testRun.getBuild().getBuildType();
      if(bt == null) {
        // should never happen, but just in case
        return MISSING_BUILD_TYPE_ID;
      }

      if(groupSlitTests && bt.getProject().isVirtual()) {
        Collection<? extends BuildDependency> dependees = testRun.getBuild().getBuildPromotion().getDependedOnMe();
        if(dependees.size() == 1) {
          // That is the usual way of things. In theory, somebody could add a dependency on autogenerated build configuration (i.e. dependees > 1),
          // but we won't group split tests in such case.

          BuildDependency dependeee = dependees.iterator().next();
          bt = dependeee.getDependent().getBuildType(); // let's report test's build type as if it was not split.
          if(bt == null) {
            // again, should never happen, but just in case
            return MISSING_BUILD_TYPE_ID;
          }
        }
      }

      encounteredBuildTypes.put(bt.getInternalId(), bt);
      return bt.getInternalId();
    };

    return testScopes.flatMap(scope -> {
      Map<String, List<STestRun>> byBuildType = scope.getTestRuns()
                                                     .stream()
                                                     .flatMap(tr -> {
                                                       if (tr instanceof MultiTestRun) return ((MultiTestRun)tr).getTestRuns().stream();
                                                       else return Stream.of(tr);
                                                     })
                                                     .collect(Collectors.groupingBy(getIdAndRememberBuildType));

      return byBuildType.entrySet().stream()
                        .map(entry -> TestScope.withBuildType(
                          scope,
                          MultiTestRun.mergeByTestName(entry.getValue(), false),
                          encounteredBuildTypes.get(entry.getKey())
                        ));
    });
  }

  private Stream<TestScope> groupBySuite(@NotNull Stream<STestRun> testRuns, @NotNull TestScopeFilter testScopeFilter) {
    return groupBySuiteInternal(testRuns, testScopeFilter);
  }

  public Stream<TestScope> groupByPackage(@NotNull Stream<STestRun> testRuns, @NotNull TestScopeFilter testScopeFilter) {
    Stream<TestScope> bySuite = groupBySuiteInternal(testRuns, testScopeFilter);
    return groupByPackageInternal(bySuite);
  }

  public Stream<TestScope> groupByClass(@NotNull Stream<STestRun> testRuns, @NotNull TestScopeFilter testScopeFilter) {
    Stream<TestScope> bySuite   = groupBySuiteInternal(testRuns, testScopeFilter);
    Stream<TestScope> byPackage = groupByPackageInternal(bySuite);
    return groupByClassInternal(byPackage);
  }

  private static Stream<TestScope> groupBySuiteInternal(@NotNull Stream<STestRun> testRuns, @NotNull TestScopeFilter testScopeFilter) {
    Map<String, List<STestRun>> scopes = testRuns.filter(testScopeFilter)
                                                 .collect(Collectors.groupingBy(item -> item.getTest().getName().getSuite()));

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
