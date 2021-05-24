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

import graphql.execution.DataFetcherResult;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class NonPaginatingLazyConnection<D, M, E extends LazyEdge<D, M>> implements ExtensibleConnection<M, E> {
  @NotNull
  private final List<D> myData;
  @NotNull
  private final Function<D, E> myEdgeFactory;

  public NonPaginatingLazyConnection(@NotNull List<D> data, @NotNull Function<D, E> edgeFactory) {
    myData = data;
    myEdgeFactory = edgeFactory;
  }

  @NotNull
  public List<D> getData() {
    return myData;
  }

  @NotNull
  public Function<D, E> getEdgeFactory() {
    return myEdgeFactory;
  }

  @NotNull
  @Override
  public DataFetcherResult<List<E>> getEdges() {
    List<E> result = myData.stream().map(myEdgeFactory::apply).collect(Collectors.toList());

    return DataFetcherResult.<List<E>>newResult()
      .data(result)
      .localContext(myData)
      .build();
  }
}