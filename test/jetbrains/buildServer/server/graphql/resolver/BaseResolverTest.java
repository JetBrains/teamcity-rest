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

package jetbrains.buildServer.server.graphql.resolver;

import graphql.relay.Edge;
import graphql.schema.DataFetchingEnvironment;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jetbrains.buildServer.server.graphql.model.connections.ExtensibleConnection;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;

import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;

public class BaseResolverTest extends BaseServerTestCase {
  protected ProjectFinder myProjectFinder;
  protected PermissionChecker myPermissionChecker;
  protected MockDataFetchingEnvironment myDataFetchingEnvironment;

  @Override
  @BeforeMethod(alwaysRun = true)
  public void setUp() throws Exception {
    super.setUp();

    myDataFetchingEnvironment = new MockDataFetchingEnvironment();
    myFixture.addService(myProjectManager);
    myPermissionChecker = new PermissionChecker(myServer.getSecurityContext(), myProjectManager);
    myFixture.addService(myPermissionChecker);
    myProjectFinder = new ProjectFinder(myProjectManager, myPermissionChecker, myServer);
    myFixture.addService(myProjectFinder);
  }

  protected <T> void assertEdges(@NotNull List<Edge<T>> edges, T... items) {
    assertEquals(items.length, edges.size());

    Set<String> cursorSet = new HashSet<>();
    for(int i = 0; i < items.length; i++) {
      assertEquals(
        String.format("Edge in position %d is not was expected: ", i),
        items[i],
        edges.get(i).getNode()
      );
      cursorSet.add(edges.get(i).getCursor().getValue());
    }

    assertEquals("Cursors must be unique", items.length, cursorSet.size());
  }

  protected <T> void assertExtensibleEdges(@NotNull List<? extends ExtensibleConnection.Edge<T>> edges, T... items) {
    assertEquals(items.length, edges.size());

    Set<String> cursorSet = new HashSet<>();
    for(int i = 0; i < items.length; i++) {
      assertEquals(
        String.format("Edge in position %d is not was expected: ", i),
        items[i],
        edges.get(i).getNode().getData()
      );

      cursorSet.add(edges.get(i).getCursor());
    }

    assertEquals("Cursors must be unique", items.length, cursorSet.size());
  }
}
