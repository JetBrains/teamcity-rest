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

package jetbrains.buildServer.server.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.*;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import jetbrains.buildServer.server.graphql.util.GraphQLRequestBody;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.util.NamedThreadFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;


@Path(Constants.GRAPHQL_API_URL)
@Component
public class GraphQLEndpoint {

  @NotNull
  private final GraphQL myGraphQL;
  @NotNull
  private final ObjectMapper myObjectMapper = new ObjectMapper();

  public GraphQLEndpoint(@NotNull GraphQL graphQL) {
    myGraphQL = graphQL;
  }

  @GET
  @Produces({MediaType.APPLICATION_JSON})
  public String get(@QueryParam("query") String query,
                    @QueryParam("operationName") String operationName,
                    @QueryParam("variables") String variables,
                    @Context HttpServletRequest request)
    throws Exception {
    return handle(new GraphQLRequestBody(query, operationName, variables), request);
  }

  @POST
  @Produces({MediaType.APPLICATION_JSON})
  @Consumes({MediaType.APPLICATION_JSON})
  public String post(String json, @Context HttpServletRequest request) throws Exception {
    return handle(GraphQLRequestBody.fromJson(json), request);
  }

  @NotNull
  private String handle(@NotNull GraphQLRequestBody body, @NotNull HttpServletRequest request) throws Exception {
    ExecutionResult result;
    if(body.query == null) {
       result = ExecutionResultImpl.newExecutionResult()
                                   .addError(GraphqlErrorException.newErrorException()
                                                                  .message("Query can't be empty.")
                                                                  .build())
                                   .build();
    } else {
      ExecutionInput.Builder inputBuilder = ExecutionInput
        .newExecutionInput()
        .context(new GraphQLContext(request))
        .query(body.query);
      if (body.operationName != null) {
        inputBuilder.operationName(body.operationName);
      }
      if (body.variables != null) {
        inputBuilder.variables(body.variables);
      }
      ExecutionInput input = inputBuilder.build();
      result = NamedThreadFactory.executeWithNewThreadName("Processing GraphQL request", () -> myGraphQL.execute(input));
    }

    return myObjectMapper.writeValueAsString(result.toSpecification());
  }
}
