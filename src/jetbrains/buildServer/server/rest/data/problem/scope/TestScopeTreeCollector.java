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

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.model.PagerData;
import org.jetbrains.annotations.NotNull;

public class TestScopeTreeCollector {
  public static final String BUILD = "build";
  public static final String ORDER_BY = "orderBy";
  public static final String MAX_CHILDREN = "maxChildren";
  public static final String AFFECTED_PROJECT = "affectedProject";
  public static final String CURRENT = "currentlyFailing";
  public static final String CURRENTLY_INVESTIGATED = "currentlyInvestigated";

  public static final String DEFAULT_MAX_CHILDREN = "5";

  private final TestScopesCollector myScopeCollector;

  public TestScopeTreeCollector(final @NotNull TestScopesCollector scopesCollector) {
    myScopeCollector = scopesCollector;
  }

  private Locator prepareScopesLocator(@NotNull Locator locator, @NotNull HttpServletRequest request) {
    Locator occurrencesLocator = Locator.createEmptyLocator();
    occurrencesLocator.setDimension(TestOccurrenceFinder.STATUS, "FAILURE");
    occurrencesLocator.setDimension(PagerData.COUNT, "-1");
    occurrencesLocator.setDimension(TestOccurrenceFinder.BUILD, locator.getDimensionValue(BUILD));
    occurrencesLocator.setDimension(TestOccurrenceFinder.CURRENTLY_INVESTIGATED, locator.getDimensionValue(CURRENTLY_INVESTIGATED));
    occurrencesLocator.setDimension(TestOccurrenceFinder.CURRENT, locator.getDimensionValue(CURRENT));
    occurrencesLocator.setDimension(TestOccurrenceFinder.AFFECTED_PROJECT, locator.getDimensionValue(AFFECTED_PROJECT));
    occurrencesLocator.setDimension(TestOccurrenceFinder.MUTED, Locator.BOOLEAN_FALSE);
    occurrencesLocator.setDimension(TestOccurrenceFinder.IGNORED, Locator.BOOLEAN_FALSE);

    Locator scopesLocator = Locator.createEmptyLocator();
    scopesLocator.setDimension(TestScopesCollector.TEST_OCCURRENCES, TestOccurrenceFinder.patchLocatorForPersonalBuilds(occurrencesLocator.toString(), request));
    scopesLocator.setDimension(TestScopesCollector.SCOPE_TYPE, "class");
    scopesLocator.setDimension(TestScopesCollector.SPLIT_BY_BUILD_TYPE, "true");

    return scopesLocator;
  }

  public List<TestScopeTree.Node> getSlicedTree(@NotNull Locator locator, @NotNull HttpServletRequest request) {
    locator.addSupportedDimensions(BUILD, ORDER_BY, MAX_CHILDREN, AFFECTED_PROJECT, CURRENT, CURRENTLY_INVESTIGATED);

    Locator scopesLocator = prepareScopesLocator(locator, request);

    Stream<TestScope> testScopes = myScopeCollector.getItems(scopesLocator);

    TestScopeTree scopeTree = new TestScopeTree(testScopes.collect(Collectors.toList()));

    Comparator<TestScopeTree.Node> order = null;
    if(locator.isAnyPresent(ORDER_BY)) {
      String orderDimension = locator.getSingleDimensionValue(ORDER_BY);
      //noinspection ConstantConditions
      order = TestScopeTree.SUPPORTED_ORDERS.getComparator(orderDimension);
    }

    String maxChildren = locator.getSingleDimensionValue(MAX_CHILDREN);
    if(maxChildren == null) {
      maxChildren = DEFAULT_MAX_CHILDREN;
    }

    locator.checkLocatorFullyProcessed();

    return scopeTree.getSlicedOrderedTree(Integer.parseInt(maxChildren), order);
  }

  public List<TestScopeTree.Node> getTopSlicedTree(@NotNull Locator locator, @NotNull HttpServletRequest request) {
    locator.addSupportedDimensions(BUILD, ORDER_BY, AFFECTED_PROJECT);

    Locator scopesLocator = prepareScopesLocator(locator, request);

    Stream<TestScope> testScopes = myScopeCollector.getItems(scopesLocator);

    TestScopeTree scopeTree = new TestScopeTree(testScopes.collect(Collectors.toList()));

    Comparator<TestScopeTree.Node> order = null;
    if(locator.isAnyPresent(ORDER_BY)) {
      String orderDimension = locator.getSingleDimensionValue(ORDER_BY);
      //noinspection ConstantConditions
      order = TestScopeTree.SUPPORTED_ORDERS.getComparator(orderDimension);
    }

    locator.checkLocatorFullyProcessed();

    return scopeTree.getTopTreeSliceUpToBuildTypes(order);
  }
}
