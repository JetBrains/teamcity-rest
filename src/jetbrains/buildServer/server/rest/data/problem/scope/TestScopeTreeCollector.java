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
import jetbrains.buildServer.server.rest.data.Locator;
import org.jetbrains.annotations.NotNull;

public class TestScopeTreeCollector {
  public static final String TEST_OCCURRENCES = "testOccurrences";
  public static final String ORDER_BY = "orderBy";
  public static final String MAX_CHILDREN = "maxChildren";

  public static final String DEFAULT_MAX_CHILDREN = "5";

  private final TestScopesCollector myScopeCollector;

  public TestScopeTreeCollector(final @NotNull TestScopesCollector scopesCollector) {
    myScopeCollector = scopesCollector;
  }

  public List<TestScopeTree.Node> getSlicedTree(@NotNull Locator locator) {
    locator.addSupportedDimensions(TEST_OCCURRENCES);

    String occurrencesLocator = locator.getSingleDimensionValue(TEST_OCCURRENCES);
    String scopesLocator = String.format("%s:%s,%s:class,%s:true",
                                         TestScopesCollector.TEST_OCCURRENCES, occurrencesLocator,
                                         TestScopesCollector.SCOPE_TYPE,
                                         TestScopesCollector.SPLIT_BY_BUILD_TYPE);


    Stream<TestScope> testScopes = myScopeCollector.getItems(Locator.locator(scopesLocator));

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

    return scopeTree.getSlicedOrderedTree(Integer.parseInt(maxChildren), order);
  }
}
