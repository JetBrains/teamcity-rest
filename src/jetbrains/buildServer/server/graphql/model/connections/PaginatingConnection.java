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

package jetbrains.buildServer.server.graphql.model.connections;

import com.intellij.openapi.diagnostic.Logger;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import jetbrains.buildServer.server.graphql.util.TeamCityGraphQLErrorType;
import org.jetbrains.annotations.NotNull;

public class PaginatingConnection<DATA, MODEL, EDGE extends LazyEdge<DATA, MODEL>> implements ExtensibleConnection<MODEL, EDGE> {
  private final Logger LOG;
  @NotNull
  private final List<DATA> myData;
  @NotNull
  private final Function<DATA, EDGE> myEdgeFactory;
  @NotNull
  private final PaginationArguments myPaginationArguments;

  public PaginatingConnection(@NotNull Collection<DATA> data, @NotNull Function<DATA, EDGE> edgeProducer, @NotNull PaginationArguments paginationArguments) {
    if(data instanceof List) {
      myData = (List<DATA>) data;
    } else {
      myData = new ArrayList<>(data);
    }

    myEdgeFactory = edgeProducer;
    myPaginationArguments = paginationArguments;
    LOG = Logger.getInstance(getClass());
  }

  @NotNull
  public List<DATA> getData() {
    return myData;
  }

  @NotNull
  public Function<DATA, EDGE> getEdgeFactory() {
    return myEdgeFactory;
  }

  @NotNull
  @Override
  public DataFetcherResult<List<EDGE>> getEdges() {
    if(myPaginationArguments.getCount() == 0) {
      return DataFetcherResult.<List<EDGE>>newResult()
                              .data(Collections.emptyList())
                              .localContext(Collections.<DATA>emptyList())
                              .build();
    }

    List<EDGE> result = new ArrayList<>();

    int from = myPaginationArguments.getDirection() == PaginationArguments.Direction.FORWARD ? 0 : myData.size() - 1;
    int direction = myPaginationArguments.getDirection() == PaginationArguments.Direction.FORWARD ? 1 : - 1;
    if(myPaginationArguments.getAfter() != null) {
      while(from >= 0 && from < myData.size()) {
        EDGE edge = myEdgeFactory.apply(myData.get(from));
        from += direction;

        if(edge.getCursor() == null) {
          LOG.warn("Cursor-based pagination fail: at least on edge does not have a cursor.");

          return DataFetcherResult.<List<EDGE>>newResult()
                                  .error(GraphqlErrorBuilder.newError().errorType(TeamCityGraphQLErrorType.SERVER_ERROR).build())
                                  .build();

        }
        if(edge.getCursor().equals(myPaginationArguments.getAfter())) {
          break;
        }
      }
    }

    int pos = from;
    int maxCount = myPaginationArguments.getCount() == -1 ? myData.size() : myPaginationArguments.getCount();
    while(result.size() < maxCount && pos >= 0 && pos < myData.size()) {
      EDGE e = myEdgeFactory.apply(myData.get(pos));
      result.add(e);

      pos += direction;
    }

    return DataFetcherResult.<List<EDGE>>newResult()
      .data(result)
      .localContext(myData)
      .build();
  }
}