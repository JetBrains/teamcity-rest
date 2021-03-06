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
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

public class LazyEdge<DATA, MODEL> implements ExtensibleConnection.Edge<MODEL> {
  @NotNull
  protected final DATA myData;

  @NotNull
  protected final Function<DATA, MODEL> myTransformer;

  public LazyEdge(@NotNull DATA data, @NotNull Function<DATA, MODEL> transformer) {
    myData = data;
    myTransformer = transformer;
  }

  @NotNull
  @Override
  public DataFetcherResult<MODEL> getNode() {
    return DataFetcherResult.<MODEL>newResult().data(myTransformer.apply(myData)).localContext(myData).build();
  }
}