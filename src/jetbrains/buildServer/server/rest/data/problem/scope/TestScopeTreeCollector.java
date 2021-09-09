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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.problem.Orders;
import jetbrains.buildServer.server.rest.data.problem.TestCountersData;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.data.problem.tree.*;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestScopeTreeCollector {
  public static final String BUILD = "build";
  public static final String ORDER_BY = "orderBy";
  public static final String MAX_CHILDREN = "maxChildren";
  public static final String AFFECTED_PROJECT = "affectedProject";
  public static final String CURRENT = "currentlyFailing";
  public static final String CURRENTLY_INVESTIGATED = "currentlyInvestigated";
  public static final String SUBTREE_ROOT_ID = "subTreeRootId";

  public static final int DEFAULT_MAX_CHILDREN = 5;
  public static final String DEFAULT_NODE_ORDER_BY_NEW_FAILED_COUNT = "newFailedCount:desc";

  private final TestScopesCollector myScopeCollector;

  private static final Orders<ScopeTree.Node<STestRun, TestCountersData>> SUPPORTED_ORDERS = new Orders<ScopeTree.Node<STestRun, TestCountersData>>()
    .add("name", Comparator.comparing(node -> node.getScope().getName()))
    .add("duration", Comparator.comparing(node -> node.getCounters().getDuration()))
    .add("count", Comparator.comparing(node -> node.getCounters().getCount()))
    .add("newFailedCount", Comparator.comparing(node -> {
      if(node.getCounters().getNewFailed() == null) {
        return -1; // we should always have new failed count, but if not, let's sort these to the end
      }
      return node.getCounters().getNewFailed();
    }))
    .add("childrenCount", Comparator.comparing(node -> node.getChildren().size()));

  public TestScopeTreeCollector(final @NotNull TestScopesCollector scopesCollector) {
    myScopeCollector = scopesCollector;
  }


  public List<ScopeTree.Node<STestRun, TestCountersData>> getSlicedTree(@NotNull Locator locator, @Nullable HttpServletRequest request) {
    locator.addSupportedDimensions(BUILD, ORDER_BY, MAX_CHILDREN, AFFECTED_PROJECT, CURRENT, CURRENTLY_INVESTIGATED);

    ScopeTree<STestRun, TestCountersData> tree = buildTree(locator, request);
    Comparator<ScopeTree.Node<STestRun, TestCountersData>> order = getNodeOrder(locator);

    String maxChildrenDim = locator.getSingleDimensionValue(MAX_CHILDREN);
    int maxChildren = maxChildrenDim == null ? DEFAULT_MAX_CHILDREN : Integer.parseInt(maxChildrenDim);

    locator.checkLocatorFullyProcessed();

    return tree.getSlicedOrderedTree(maxChildren, STestRun.NEW_FIRST_NAME_COMPARATOR, order);
  }

  @NotNull
  public List<ScopeTree.Node<STestRun, TestCountersData>> getSlicedSubTree(@NotNull Locator locator, @Nullable HttpServletRequest request) {
    locator.addSupportedDimensions(BUILD, ORDER_BY, MAX_CHILDREN, AFFECTED_PROJECT, CURRENT, CURRENTLY_INVESTIGATED, SUBTREE_ROOT_ID);

    ScopeTree<STestRun, TestCountersData> tree = buildTree(locator, request);
    Comparator<ScopeTree.Node<STestRun, TestCountersData>> order = getNodeOrder(locator);

    String maxChildrenDim = locator.getSingleDimensionValue(MAX_CHILDREN);
    int maxChildren = maxChildrenDim == null ? DEFAULT_MAX_CHILDREN : Integer.parseInt(maxChildrenDim);

    String subTreeRootID = locator.getSingleDimensionValue(SUBTREE_ROOT_ID);
    if(subTreeRootID == null) {
      throw new LocatorProcessException("Missing value of required dimension " + SUBTREE_ROOT_ID);
    }

    locator.checkLocatorFullyProcessed();

    return tree.getFullNodeAndSlicedOrderedSubtree(subTreeRootID, maxChildren, STestRun.NEW_FIRST_NAME_COMPARATOR, order);
  }

  public List<ScopeTree.Node<STestRun, TestCountersData>> getTopSlicedTree(@NotNull Locator locator, @Nullable HttpServletRequest request) {
    locator.addSupportedDimensions(BUILD, ORDER_BY, AFFECTED_PROJECT);

    ScopeTree<STestRun, TestCountersData> tree = buildTree(locator, request);
    Comparator<ScopeTree.Node<STestRun, TestCountersData>> order = getNodeOrder(locator);

    locator.checkLocatorFullyProcessed();

    // include projects and build types only
    return tree.getTopTreeSliceUpTo(order, scope -> ((TestScopeInfo) scope).getType().compareTo(TestScopeType.BUILD_TYPE) <= 0);
  }

  private ScopeTree<STestRun, TestCountersData> buildTree(@NotNull Locator locator, @Nullable HttpServletRequest request) {
    Locator scopesLocator = prepareScopesLocator(locator, request);

    Stream<TestScope> testScopes = myScopeCollector.getItems(scopesLocator);

    return new ScopeTree<STestRun, TestCountersData> (
      TestScopeInfo.ROOT,
      new TestCountersData(),
      testScopes.collect(Collectors.toList())     // TODO: this is very fragile as we require all of those to be CLASS
    );
  }

  private Locator prepareScopesLocator(@NotNull Locator locator, @Nullable HttpServletRequest request) {
    Locator occurrencesLocator = Locator.createEmptyLocator();
    occurrencesLocator.setDimension(TestOccurrenceFinder.STATUS, "FAILURE");
    occurrencesLocator.setDimension(PagerData.COUNT, "-1");
    occurrencesLocator.setDimension(TestOccurrenceFinder.BUILD, locator.getDimensionValue(BUILD));
    occurrencesLocator.setDimension(TestOccurrenceFinder.CURRENTLY_INVESTIGATED, locator.getDimensionValue(CURRENTLY_INVESTIGATED));
    occurrencesLocator.setDimension(TestOccurrenceFinder.CURRENT, locator.getDimensionValue(CURRENT));
    occurrencesLocator.setDimension(TestOccurrenceFinder.AFFECTED_PROJECT, locator.getDimensionValue(AFFECTED_PROJECT));
    occurrencesLocator.setDimension(TestOccurrenceFinder.MUTED, Locator.BOOLEAN_FALSE);
    occurrencesLocator.setDimension(TestOccurrenceFinder.IGNORED, Locator.BOOLEAN_FALSE);


    String patchedOccurrencesLocator = request == null ?
                                       occurrencesLocator.toString() :
                                       TestOccurrenceFinder.patchLocatorForPersonalBuilds(occurrencesLocator.toString(), request);

    Locator scopesLocator = Locator.createEmptyLocator();
    scopesLocator.setDimension(TestScopesCollector.TEST_OCCURRENCES, patchedOccurrencesLocator);
    scopesLocator.setDimension(TestScopesCollector.SCOPE_TYPE, "class");
    scopesLocator.setDimension(TestScopesCollector.SPLIT_BY_BUILD_TYPE, "true");

    return scopesLocator;
  }

  private Comparator<ScopeTree.Node<STestRun, TestCountersData>> getNodeOrder(@NotNull Locator locator) {
    if(locator.isAnyPresent(ORDER_BY)) {
      String orderDimension = locator.getSingleDimensionValue(ORDER_BY);
      //noinspection ConstantConditions
      return SUPPORTED_ORDERS.getComparator(orderDimension);
    }

    return SUPPORTED_ORDERS.getComparator(DEFAULT_NODE_ORDER_BY_NEW_FAILED_COUNT);
  }
}
