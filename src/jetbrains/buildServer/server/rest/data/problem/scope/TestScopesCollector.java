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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.problem.Orders;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.SplitBuildsFeatureUtil;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.dependency.BuildDependency;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class TestScopesCollector {
  public static final String SPLIT_TESTS_GROUP_BY_DEFAULT_TOGGLE = "rest.splitTests.groupByDefault";

  private static final List<String> SUPPORTED_SCOPES = Arrays.asList("suite", "package", "class");
  private static final Orders<TestScope> SUPPORTED_ORDERS = new Orders<TestScope>()
    .add("name",     Comparator.comparing(scope -> scope.getName()))
    .add("duration", Comparator.comparing(scope -> scope.getOrCalcCountersData().getDuration()))
    .add("count",    Comparator.comparing(scope -> scope.getOrCalcCountersData().getCount()));

  /** Dimensions */
  public static final String TEST_OCCURRENCES = "testOccurrences";
  public static final String SCOPE_TYPE = "scopeType";
  public static final String ORDER_BY = "orderBy";
  public static final String SPLIT_BY_BUILD_TYPE = "splitByBuildType";
  public static final String GROUP_PARALLEL_TESTS = "groupParallelTests";

  @NotNull
  private final TestOccurrenceFinder myTestOccurrenceFinder;
  @NotNull
  private final TestScopeFilterProducer myTestScopeFilterProducer;

  private static final long DEFAULT_COUNT = 100;

  public TestScopesCollector(final @NotNull TestOccurrenceFinder finder, final @NotNull TestScopeFilterProducer testScopeFilterProducer) {
    myTestOccurrenceFinder = finder;
    myTestScopeFilterProducer = testScopeFilterProducer;
  }

  @NotNull
  public PagedSearchResult<TestScope> getPagedItems(@NotNull Locator locator) {
    Long count = locator.getSingleDimensionValueAsLong(PagerData.COUNT, DEFAULT_COUNT);
    Long start = locator.getSingleDimensionValueAsLong(PagerData.START);

    Stream<TestScope> scopes = getItems(locator);

    if(start != null) {
      scopes = scopes.skip(start);
    }

    if(count != -1) {
      scopes = scopes.limit(count);
    }

    // We set count value to DEFAULT_COUNT earlier if it was not present
    return new PagedSearchResult<TestScope>(scopes.collect(Collectors.toList()), start, count.intValue());
  }

  @NotNull
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
      Boolean split = locator.getSingleDimensionValueAsBoolean(SPLIT_BY_BUILD_TYPE, false);
      if(split != null && split) {
        boolean isGroupByDefault = TeamCityProperties.getBooleanOrTrue(SPLIT_TESTS_GROUP_BY_DEFAULT_TOGGLE);
        Boolean groupParallelTests = locator.getSingleDimensionValueAsBoolean(GROUP_PARALLEL_TESTS, isGroupByDefault);
        scopes = splitByBuildType(scopes, groupParallelTests != null && groupParallelTests, null);
      }
    }

    return scopes;
  }

  @NotNull
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

  @NotNull
  public Stream<TestScope> splitByBuildType(@NotNull Stream<TestScope> testScopes, boolean groupParallelTests, @Nullable BuildPromotion headPromotion) {
    ByBuildSplitter splitter = new ByBuildSplitter(groupParallelTests, headPromotion);

    return testScopes.flatMap(scope -> {
      Map<BuildPromotion, List<STestRun>> byBuild = scope.getTestRuns().stream()
                                                 .flatMap(tr -> {
                                                   if (tr instanceof MultiTestRun) return ((MultiTestRun)tr).getTestRuns().stream();
                                                   else return Stream.of(tr);
                                                 })
                                                 .collect(Collectors.groupingBy(splitter::remapPromotion));

      return byBuild.entrySet().stream()
                    .map(promotionToTestRuns -> TestScope.withBuild(
                      scope,
                      MultiTestRun.mergeByTestName(promotionToTestRuns.getValue(), false), // we already split by builds, no need to do that again
                      promotionToTestRuns.getKey()
                    ));
    });
  }

  private class ByBuildSplitter {
    private final boolean myGroupParallelTests;
    private final BuildPromotion myHeadPromotion;
    private final Map<String, BuildPromotion> myVirtualBtToConcreteBuild = new HashMap<>();

    public ByBuildSplitter(boolean groupParallelTests, @Nullable BuildPromotion headPromotion) {
      myGroupParallelTests = groupParallelTests;
      myHeadPromotion = headPromotion;
    }

    @NotNull
    public BuildPromotion remapPromotion(@NotNull STestRun testRun) {
      BuildPromotion promo = testRun.getBuild().getBuildPromotion();
      SBuildType bt = promo.getBuildType();
      if(bt == null) {
        // should never happen, but just in case
        return promo;
      }

      // Let's check if we need any additional actions.
      if(!myGroupParallelTests || !SplitBuildsFeatureUtil.isVirtualConfiguration(bt)) {
        // simple case, no remapping
        return promo;
      }

      // todo: do some proper checks if this configuration needs grouping,
      // SProject::isVirtual() is not enough evidence for that.

      BuildPromotion remappedPromotion = myVirtualBtToConcreteBuild.get(bt.getInternalId());
      if(remappedPromotion != null) {
        // That is a quick win for us, we already know what to do!
        return remappedPromotion;
      }

      Collection<? extends BuildDependency> dependees = promo.getDependedOnMe();
      if(dependees.size() == 1) {
        // That is the easy way of things, exactly one dependee, no need for complex lookup in a chain.
        BuildPromotion dependee = dependees.iterator().next().getDependent();
        myVirtualBtToConcreteBuild.put(bt.getInternalId(), dependee);
        return dependee;
      } else if(dependees.size() > 1 && myHeadPromotion != null) {
        // let's find the parallelized build the hard way, going through build chain and finding

        BuildPromotion dependee = getParentOf(myHeadPromotion, promo);
        if(dependee != null) {
          myVirtualBtToConcreteBuild.put(bt.getInternalId(), dependee);
          return dependee;
        }
      }

      // There are no dependees or we can't choose the correct dependee.
      return promo;
    }

    @Nullable
    private BuildPromotion getParentOf(@NotNull BuildPromotion head, @NotNull BuildPromotion target) {
      Deque<BuildPromotion> queue = new ArrayDeque<>();
      queue.add(head);
      while(!queue.isEmpty()) {
        BuildPromotion current = queue.poll();
        Collection<? extends BuildDependency> dependencies = current.getDependencies();
        for (BuildDependency dependency : dependencies) {
          BuildPromotion child = dependency.getDependOn();
          if (child.getId() == target.getId()) {
            return child;
          }

          queue.add(child);
        }
      }

      return null;
    }
  }

  @NotNull
  private Stream<TestScope> groupBySuite(@NotNull Stream<STestRun> testRuns, @NotNull TestScopeFilter testScopeFilter) {
    return groupBySuiteInternal(testRuns, testScopeFilter);
  }

  @NotNull
  public Stream<TestScope> groupByPackage(@NotNull Stream<STestRun> testRuns, @NotNull TestScopeFilter testScopeFilter) {
    Stream<TestScope> bySuite = groupBySuiteInternal(testRuns, testScopeFilter);
    return groupByPackageInternal(bySuite);
  }

  @NotNull
  public Stream<TestScope> groupByClass(@NotNull Stream<STestRun> testRuns, @NotNull TestScopeFilter testScopeFilter) {
    Stream<TestScope> bySuite   = groupBySuiteInternal(testRuns, testScopeFilter);
    Stream<TestScope> byPackage = groupByPackageInternal(bySuite);
    return groupByClassInternal(byPackage);
  }

  @NotNull
  private static Stream<TestScope> groupBySuiteInternal(@NotNull Stream<STestRun> testRuns, @NotNull TestScopeFilter testScopeFilter) {
    Map<String, List<STestRun>> scopes = testRuns.filter(testScopeFilter)
                                                 .collect(Collectors.groupingBy(item -> item.getTest().getName().getSuite()));

    return scopes.entrySet().stream().map(entry -> new TestScope(entry.getValue(), entry.getKey()));
  }

  @NotNull
  private static Stream<TestScope> groupByPackageInternal(@NotNull Stream<TestScope> suites) {
    return suites.flatMap(testScope -> {
      Map<String, List<STestRun>> byPackage = testScope.getTestRuns().stream().collect(Collectors.groupingBy(item -> item.getTest().getName().getPackageName()));
      return byPackage.entrySet().stream().map(packagePair -> new TestScope(packagePair.getValue(), testScope.getSuite(), packagePair.getKey()));
    });
  }

  @NotNull
  private static Stream<TestScope> groupByClassInternal(@NotNull Stream<TestScope> packages) {
    return packages.flatMap(testScope -> {
      Map<String, List<STestRun>> byClass = testScope.getTestRuns().stream().collect(Collectors.groupingBy(item -> item.getTest().getName().getClassName()));
      return byClass.entrySet().stream().map(classPair -> new TestScope(classPair.getValue(), testScope.getSuite(), testScope.getPackage(), classPair.getKey()));
    });
  }
}
