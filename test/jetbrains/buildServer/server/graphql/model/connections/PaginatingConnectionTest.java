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

package jetbrains.buildServer.server.graphql.model.connections;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.Test;

import static org.junit.Assert.assertEquals;

public class PaginatingConnectionTest {
  private final PaginationArguments EVERYTHING = new PaginationArguments() {
    @Nullable
    @Override
    public String getAfter() {
      return null;
    }

    @Override
    public int getCount() {
      return -1;
    }

    @NotNull
    @Override
    public Direction getDirection() {
      return Direction.FORWARD;
    }
  };

  @Test
  public void simpleTest1() {
    List<String> data = Arrays.asList("A", "B", "C", "D");

    ExtensibleConnection<Model, StringEdge> connection = new PaginatingConnection<String, Model, StringEdge>(data, s -> new StringEdge(s), EVERYTHING);

    List<StringEdge> edges = connection.getEdges().getData();

    assertEquals(data.size(), edges.size());
    for(int i = 0; i < data.size(); i++) {
      assertEquals("Original data must be in local context", data.get(i), edges.get(i).getNode().getLocalContext());

      assertEquals("Model must contain correct data", data.get(i), edges.get(i).getNode().getData().myValue);
    }
  }

  @Test
  public void simpleTest2() {
    List<String> data = Arrays.asList("A", "B", "C", "D");

    ExtensibleConnection<Model, StringEdge> connection = new PaginatingConnection<String, Model, StringEdge>(data, s -> new StringEdge(s), EVERYTHING);

    List<StringEdge> edges = connection.getEdges().getData();

    assertEquals(data.size(), edges.size());
    for(int i = 0; i < data.size(); i++) {
      assertEquals("Original data must be in local context", data.get(i), edges.get(i).getNode().getLocalContext());

      assertEquals("Model must contain correct data", data.get(i), edges.get(i).getNode().getData().myValue);
    }
  }

  @Test
  public void testAfter() {
    List<String> data = Arrays.asList("A", "B", "C", "D");

    PaginationArgumentsProvider provider = new PaginationArgumentsProviderImpl();
    PaginationArguments args = provider.get(2, "B", PaginationArgumentsProvider.FallbackBehaviour.RETURN_EVERYTHING);
    ExtensibleConnection<Model, StringEdge> connection = new PaginatingConnection<String, Model, StringEdge>(data, s -> new StringEdge(s), args);

    List<StringEdge> edges = connection.getEdges().getData();

    assertEquals(2, edges.size());
    for(int i = 0; i < 2; i++) {
      assertEquals("Original data must be in local context", data.get(i + 2), edges.get(i).getNode().getLocalContext());

      assertEquals("Model must contain correct data", data.get(i + 2), edges.get(i).getNode().getData().myValue);
    }
  }

  class Model {
    public String myValue;

    public Model(String value) {
      myValue = value;
    }
  }

  class StringEdge extends LazyEdge<String, Model> {
    public StringEdge(@NotNull String s) {
      // Can't replace this with method reference (Model::new) because of the bug in javac/jvm
      super(s, new Function<String, Model>() {
        @Override
        public Model apply(String s) {
          return new Model(s);
        }
      });
    }

    @Nullable
    @Override
    public String getCursor() {
      return myData;
    }
  }
}
