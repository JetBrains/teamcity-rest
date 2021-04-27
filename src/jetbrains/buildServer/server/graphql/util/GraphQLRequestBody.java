/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.server.graphql.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public class GraphQLRequestBody {
  @Nullable
  public final String query;
  @Nullable
  public final Map<String, Object> variables;
  @Nullable
  public final String operationName;

  public GraphQLRequestBody() {
    query = null;
    operationName = null;
    variables = new HashMap<>();
  }

  public GraphQLRequestBody(@Nullable String query, @Nullable String operationName, @Nullable String variables) throws JsonProcessingException {
    this.query = query;
    this.operationName = operationName;

    if(variables != null)
      this.variables = new ObjectMapper().readValue(variables, new TypeReference<Map<String,Object>>(){});
    else
      this.variables = Collections.emptyMap();
  }

  public static GraphQLRequestBody fromJson(@Nullable String json) throws JsonProcessingException {
    if(json == null)
      return new GraphQLRequestBody();
    return new ObjectMapper().readValue(json, GraphQLRequestBody.class);
  }
}
