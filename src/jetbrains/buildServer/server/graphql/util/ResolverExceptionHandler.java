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

package jetbrains.buildServer.server.graphql.util;

import com.intellij.openapi.diagnostic.Logger;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.execution.ExecutionPath;
import graphql.language.SourceLocation;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class ResolverExceptionHandler implements DataFetcherExceptionHandler {
  private static final Logger LOG = Logger.getInstance(ResolverExceptionHandler.class.getName());

  @Override
  public DataFetcherExceptionHandlerResult onException(DataFetcherExceptionHandlerParameters handlerParameters) {
    Throwable exception = handlerParameters.getException();
    SourceLocation sourceLocation = handlerParameters.getSourceLocation();
    ExecutionPath path = handlerParameters.getPath();

    GraphqlErrorBuilder builder = GraphqlErrorBuilder.newError()
                                                     .path(path)
                                                     .location(sourceLocation)
                                                     .message(exception.getMessage());

    if(exception instanceof AccessDeniedException) {
      GraphQLError error = builder.errorType(TeamCityGraphQLErrorType.ACCESS_DENIED).build();

      return DataFetcherExceptionHandlerResult.newResult().error(error).build();
    }
    if(exception instanceof NotFoundException) {
      GraphQLError error = builder.errorType(TeamCityGraphQLErrorType.NOT_FOUND).build();

      return DataFetcherExceptionHandlerResult.newResult().error(error).build();
    }

    LOG.warnAndDebugDetails("Exception occured while fetching data.", exception);

    GraphQLError error = builder.errorType(TeamCityGraphQLErrorType.SERVER_ERROR).build();
    return DataFetcherExceptionHandlerResult.newResult().error(error).build();
  }
}
