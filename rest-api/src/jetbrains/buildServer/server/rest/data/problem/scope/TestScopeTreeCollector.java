/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.problem.Orders;
import jetbrains.buildServer.server.rest.data.problem.TestCountersData;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.data.util.tree.ScopeTree;
import jetbrains.buildServer.server.rest.data.util.tree.Node;
import jetbrains.buildServer.server.rest.data.util.tree.TreeSlicingOptions;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyInjectable;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.VirtualBuildsUtil;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * Collects test scopes (project path, build type, suite, package, class) according to a given
 * locator and returns sliced scope tree, top nodes coming first, children later. <br/>
 * Nodes in a tree are sorted according to ORDER_BY locator dimension before slicing. <br/>
 * There are two types of slicing: vertical and horisontal. <br/>
 * <ul>
 * <li>1. Vertical slicing: for each tree node recursively only first MAX_CHILDREN nodes are returned. </li>
 * <li>2. Horizontal slicing: tree nodes are returned up to the level, determined by a given condition. </li>
 * </ul>
 */
@JerseyInjectable
@Component
public class TestScopeTreeCollector {
  public static final String BUILD = "build";
  public static final String ORDER_BY = "orderBy";
  public static final String MAX_CHILDREN = "maxChildren";
  public static final String AFFECTED_PROJECT = "affectedProject";
  public static final String CURRENT = "currentlyFailing";
  public static final String CURRENTLY_INVESTIGATED = "currentlyInvestigated";
  public static final String NEW_FAILURE = "newFailure";
  public static final String SUBTREE_ROOT_ID = "subTreeRootId";

  public static final String TREE_MAX_TOTAL_NODES = "maxTotalNodes";

  public static final int DEFAULT_MAX_CHILDREN = 5;
  public static final String DEFAULT_NODE_ORDER_BY_NEW_FAILED_COUNT = "newFailedCount:desc";

  private final TestScopesCollector myScopeCollector;
  private final TestOccurrenceFinder myTestOccurrenceFinder;

  private static final Orders<Node<STestRun, TestCountersData>> SUPPORTED_ORDERS = new Orders<Node<STestRun, TestCountersData>>()
    .add("name", Comparator.comparing(node -> node.getScope().getName()))
    .add("duration", Comparator.comparing(node -> node.getCounters().getDuration()))
    .add("count", Comparator.comparing(node -> node.getCounters().getCount()))
    .add("newFailedCount", Comparator.nullsLast(Comparator.comparing(node -> node.getCounters().getNewFailed())))
    .add("childrenCount", Comparator.comparing(node -> node.getChildren().size()));

  public TestScopeTreeCollector(final @NotNull TestScopesCollector scopesCollector, final @NotNull TestOccurrenceFinder testOccurrenceFinder) {
    myScopeCollector = scopesCollector;
    myTestOccurrenceFinder = testOccurrenceFinder;
  }


  @NotNull
  public List<Node<STestRun, TestCountersData>> getSlicedTree(@NotNull Locator locator) {
    locator.addSupportedDimensions(BUILD, ORDER_BY, MAX_CHILDREN, AFFECTED_PROJECT, CURRENT, CURRENTLY_INVESTIGATED, NEW_FAILURE, SUBTREE_ROOT_ID,
                                   TestScopesCollector.GROUP_PARALLEL_TESTS);

    if(locator.isAnyPresent(SUBTREE_ROOT_ID)) {
      return getSlicedSubTree(locator);
    }

    return getSlicedTreeInternal(locator);
  }

  @NotNull
  private List<Node<STestRun, TestCountersData>> getSlicedTreeInternal(@NotNull Locator locator) {
    ScopeTree<STestRun, TestCountersData> tree = buildTree(locator);
    Comparator<Node<STestRun, TestCountersData>> order = getNodeOrder(locator);

    TreeSlicingOptions<STestRun, TestCountersData> slicingOptions = new TreeSlicingOptions<>(getMaxChildrenFunction(locator), STestRun.NEW_FIRST_NAME_COMPARATOR, order);
    String maxTotalNodesStr = locator.getSingleDimensionValue(TREE_MAX_TOTAL_NODES);
    if (maxTotalNodesStr != null) {
      try {
        int maxTotalNodes = Integer.parseInt(maxTotalNodesStr);
        slicingOptions = slicingOptions.withMaxNodes(maxTotalNodes);
      } catch (NumberFormatException nfe) {
        throw new LocatorProcessException("Dimension '" + TREE_MAX_TOTAL_NODES + "' must be an integer.");
      }
    }

    locator.checkLocatorFullyProcessed();

    return tree.getSlicedOrderedTree(slicingOptions);
  }

  @NotNull
  public List<Node<STestRun, TestCountersData>> getSlicedTreeFromBuildPromotions(@NotNull Stream<BuildPromotion> promotions, @NotNull Locator treeLocator) {
    treeLocator.addSupportedDimensions(NEW_FAILURE, SUBTREE_ROOT_ID);

    final String testRunsLocator = "build:%d,status:failure,muted:false,ignored:false"
                                   + (treeLocator.isAnyPresent(NEW_FAILURE) ? ",newFailure:" + treeLocator.getSingleDimensionValue(NEW_FAILURE) : "");

    boolean isGroupByDefault = TeamCityProperties.getBooleanOrTrue(TestScopesCollector.SPLIT_TESTS_GROUP_BY_DEFAULT_TOGGLE);
    Boolean groupSplitTests = treeLocator.getSingleDimensionValueAsBoolean(TestScopesCollector.GROUP_PARALLEL_TESTS, isGroupByDefault);

    List<TestScope> scopes = promotions
      .filter(promotion -> promotion.getAssociatedBuildId() != null)
      .filter(promotion -> {
        SBuildType bt = promotion.getParentBuildType();
        return bt != null && !VirtualBuildsUtil.isVirtualConfiguration(bt) &&
               (!promotion.isCompositeBuild() || promotion.isCompositeBuild() && VirtualBuildsUtil.isParallelizedBuild(promotion));
      })
      .flatMap(promotion -> {
        Stream<STestRun> testRunStream = myTestOccurrenceFinder.getItems(String.format(testRunsLocator, promotion.getAssociatedBuildId())).getEntries().stream();

        Stream<TestScope> scopeStream = myScopeCollector.groupByClass(testRunStream, new TestScopeFilterImpl(Collections.emptyList(), ""));

        return myScopeCollector.splitByBuildType(scopeStream, groupSplitTests != null && groupSplitTests, promotion);
      }).collect(Collectors.toList());

    ScopeTree<STestRun, TestCountersData> tree = new ScopeTree<STestRun, TestCountersData>(TestScopeInfo.ROOT, new TestCountersData(), scopes);
    TreeSlicingOptions<STestRun, TestCountersData> slicingOptions = new TreeSlicingOptions<>(
      (node) -> DEFAULT_MAX_CHILDREN,
      STestRun.NEW_FIRST_NAME_COMPARATOR,
      SUPPORTED_ORDERS.getComparator(DEFAULT_NODE_ORDER_BY_NEW_FAILED_COUNT)
    );

    String maxTotalNodesStr = treeLocator.getSingleDimensionValue(TREE_MAX_TOTAL_NODES);
    if(maxTotalNodesStr != null) {
      try {
        int maxTotalNodes = Integer.parseInt(maxTotalNodesStr);
        slicingOptions = slicingOptions.withMaxNodes(maxTotalNodes);
      } catch (NumberFormatException nfe) {
        throw new LocatorProcessException("Dimension '" + TREE_MAX_TOTAL_NODES + "' must be an integer.");
      }
    }

    if(treeLocator.isAnyPresent(SUBTREE_ROOT_ID)) {
      String subTreeRootId = treeLocator.getSingleDimensionValue(SUBTREE_ROOT_ID);
      treeLocator.checkLocatorFullyProcessed();

      //noinspection ConstantConditions
      return tree.getFullNodeAndSlicedOrderedSubtree(subTreeRootId, slicingOptions);
    }

    treeLocator.checkLocatorFullyProcessed();
    return tree.getSlicedOrderedTree(slicingOptions);
  }

  @NotNull
  private List<Node<STestRun, TestCountersData>> getSlicedSubTree(@NotNull Locator locator) {
    ScopeTree<STestRun, TestCountersData> tree = buildTree(locator);
    Comparator<Node<STestRun, TestCountersData>> order = getNodeOrder(locator);

    String subTreeRootID = locator.getSingleDimensionValue(SUBTREE_ROOT_ID);
    if (subTreeRootID == null) {
      throw new LocatorProcessException("Missing value of required dimension " + SUBTREE_ROOT_ID);
    }

    TreeSlicingOptions<STestRun, TestCountersData> slicingOptions = new TreeSlicingOptions<STestRun, TestCountersData>(getMaxChildrenFunction(locator), STestRun.NEW_FIRST_NAME_COMPARATOR, order);

    String maxTotalNodesStr = locator.getSingleDimensionValue(TREE_MAX_TOTAL_NODES);
    if (maxTotalNodesStr != null) {
      try {
        int maxTotalNodes = Integer.parseInt(maxTotalNodesStr);
        slicingOptions = slicingOptions.withMaxNodes(maxTotalNodes);
      } catch (NumberFormatException nfe) {
        throw new LocatorProcessException("Dimension '" + TREE_MAX_TOTAL_NODES + "' must be an integer.");
      }
    }

    locator.checkLocatorFullyProcessed();

    return tree.getFullNodeAndSlicedOrderedSubtree(subTreeRootID, slicingOptions);
  }

  @NotNull
  public List<Node<STestRun, TestCountersData>> getTopSlicedTree(@NotNull Locator locator) {
    locator.addSupportedDimensions(BUILD, ORDER_BY, AFFECTED_PROJECT);

    ScopeTree<STestRun, TestCountersData> tree = buildTree(locator);
    Comparator<Node<STestRun, TestCountersData>> order = getNodeOrder(locator);

    locator.checkLocatorFullyProcessed();

    // include projects and build types only
    return tree.getTopTreeSliceUpTo(order, scope -> ((TestScopeInfo) scope).getType().compareTo(TestScopeType.BUILD_TYPE) <= 0);
  }

  @NotNull
  private ScopeTree<STestRun, TestCountersData> buildTree(@NotNull Locator locator) {
    Locator scopesLocator = prepareScopesLocator(locator);

    Stream<TestScope> testScopes = myScopeCollector.getItems(scopesLocator);

    return new ScopeTree<STestRun, TestCountersData> (
      TestScopeInfo.ROOT,
      new TestCountersData(),
      testScopes.collect(Collectors.toList()) // this is a bit fragile as we require all of those to be CLASS
    );
  }

  @NotNull
  private static Function<Node<STestRun, TestCountersData>, Integer> getMaxChildrenFunction(int defaultMaxChildren, Function<TestScopeInfo, Integer> scope2maxChildren) {
    return (node) -> {
      // TW-71521 Don't apply `maxChildren` to project & build configuration nodes
      if (node.getScope() instanceof TestScopeInfo) {
        Integer maxChildren = scope2maxChildren.apply((TestScopeInfo)node.getScope());
        return maxChildren == null ? defaultMaxChildren : maxChildren;
      }
      return defaultMaxChildren;
    };
  }

  /**
   * <pre>
   *   "" -> DEFAULT_MAX_CHILDREN for all
   *   maxChildren:7 -> 7 for all
   *   maxChildren(default:7) -> 7 for all
   *   maxChildren(buildType:4,project:3,default:7) -> 4 for BUILD_TYPE nodes, 3 for PROJECT nodes, 7 for all others
   *   maxChildren(buildType:4,project:3) -> 4 for BUILD_TYPE nodes, 3 for PROJECT nodes, DEFAULT_MAX_CHILDREN for all others
   *
   *   maxChildren:qwerty -> error
   *   maxChildren(default:qwerty) -> error
   *   maxChildren(buildType:qwerty) -> error
   *   maxChildren(project:qwerty) -> error
   * </pre>
   *
   * TW-71521 Don't apply `maxChildren` to project & build configuration nodes
   */
  @NotNull
  private static Function<Node<STestRun, TestCountersData>, Integer> getMaxChildrenFunction(@NotNull Locator locator) {
    Locator maxChildrenLocator = locator.get(MAX_CHILDREN);
    if (maxChildrenLocator == null) {
      return (node) -> DEFAULT_MAX_CHILDREN;
    }

    if (maxChildrenLocator.isSingleValue()) {
      String maxChildrenDim = maxChildrenLocator.getSingleValue();
      //noinspection DataFlowIssue // Never null because cheched with isSingleValue above
      int maxChildren = Integer.parseInt(maxChildrenDim);
      return (node) -> maxChildren;
    }

    int defaultMaxChildren = (int)(long)maxChildrenLocator.getSingleDimensionValueAsLong("default", (long)DEFAULT_MAX_CHILDREN);
    int buildTypeMaxChildren = (int)(long)maxChildrenLocator.getSingleDimensionValueAsLong("buildType", (long)defaultMaxChildren);
    int projectMaxChildren = (int)(long)maxChildrenLocator.getSingleDimensionValueAsLong("project", (long)defaultMaxChildren);
    return getMaxChildrenFunction(defaultMaxChildren, scope -> {
      if (scope.getType().equals(TestScopeType.BUILD_TYPE)) return buildTypeMaxChildren;
      if (scope.getType().equals(TestScopeType.PROJECT)) return projectMaxChildren;
      return null;
    });
  }

  private static Locator prepareScopesLocator(@NotNull Locator locator) {
    Locator occurrencesLocator = Locator.createEmptyLocator();
    occurrencesLocator.setDimension(TestOccurrenceFinder.STATUS, "FAILURE");
    occurrencesLocator.setDimension(PagerData.COUNT, "-1");
    occurrencesLocator.setDimension(TestOccurrenceFinder.BUILD, locator.getDimensionValue(BUILD));
    occurrencesLocator.setDimension(TestOccurrenceFinder.CURRENTLY_INVESTIGATED, locator.getDimensionValue(CURRENTLY_INVESTIGATED));
    occurrencesLocator.setDimension(TestOccurrenceFinder.CURRENT, locator.getDimensionValue(CURRENT));
    occurrencesLocator.setDimension(TestOccurrenceFinder.AFFECTED_PROJECT, locator.getDimensionValue(AFFECTED_PROJECT));
    occurrencesLocator.setDimension(TestOccurrenceFinder.NEW_FAILURE, locator.getDimensionValue(NEW_FAILURE));
    occurrencesLocator.setDimension(TestOccurrenceFinder.MUTED, Locator.BOOLEAN_FALSE);
    occurrencesLocator.setDimension(TestOccurrenceFinder.IGNORED, Locator.BOOLEAN_FALSE);

    Locator scopesLocator = Locator.createEmptyLocator();
    scopesLocator.setDimension(TestScopesCollector.TEST_OCCURRENCES, occurrencesLocator.toString());
    scopesLocator.setDimension(TestScopesCollector.SCOPE_TYPE, "class");
    scopesLocator.setDimension(TestScopesCollector.SPLIT_BY_BUILD_TYPE, "true");
    scopesLocator.setDimension(TestScopesCollector.GROUP_PARALLEL_TESTS, locator.getDimensionValue(TestScopesCollector.GROUP_PARALLEL_TESTS));

    return scopesLocator;
  }

  private static Comparator<Node<STestRun, TestCountersData>> getNodeOrder(@NotNull Locator locator) {
    if(locator.isAnyPresent(ORDER_BY)) {
      String orderDimension = locator.getSingleDimensionValue(ORDER_BY);
      //noinspection ConstantConditions
      return SUPPORTED_ORDERS.getComparator(orderDimension);
    }

    return SUPPORTED_ORDERS.getComparator(DEFAULT_NODE_ORDER_BY_NEW_FAILED_COUNT);
  }
}