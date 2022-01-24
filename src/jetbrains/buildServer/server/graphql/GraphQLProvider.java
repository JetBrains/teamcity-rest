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

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import graphql.GraphQL;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.AsyncSerialExecutionStrategy;
import graphql.execution.SubscriptionExecutionStrategy;
import graphql.kickstart.tools.SchemaParser;
import graphql.kickstart.tools.SchemaParserOptions;
import graphql.schema.GraphQLSchema;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import javax.annotation.PostConstruct;
import jetbrains.buildServer.server.graphql.model.agentPool.actions.*;
import jetbrains.buildServer.server.graphql.model.buildType.incompatibility.*;
import jetbrains.buildServer.server.graphql.resolver.*;
import jetbrains.buildServer.server.graphql.resolver.agentPool.AgentPoolMutation;
import jetbrains.buildServer.server.graphql.resolver.agentPool.AgentPoolResolver;
import jetbrains.buildServer.server.graphql.resolver.agentPool.ProjectAgentPoolResolver;
import jetbrains.buildServer.server.graphql.util.ResolverExceptionHandler;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.executors.ExecutorsFactory;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.ExecutorsKt;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class GraphQLProvider {
  private static final String GRAPHQL_RESOLVER_POOL_SIZE = "teamcity.graphql.resolvers.poolSize";
  private static final int POOL_SIZE = TeamCityProperties.getInteger(GRAPHQL_RESOLVER_POOL_SIZE, 1);

  private final ExecutorService myExecutor = ExecutorsFactory.newFixedDaemonExecutor("GraphQL resolver", POOL_SIZE);
  private GraphQL graphQL;

  @Autowired
  @NotNull
  private Query myQuery;

  @Autowired
  @NotNull
  private Mutation myMutation;

  @Autowired
  @NotNull
  private AgentPoolMutation myAgentPoolMutation;

  @Autowired
  @NotNull
  private ProjectResolver myProjectResolver;

  @Autowired
  @NotNull
  private AgentResolver myAgentResolver;

  @Autowired
  @NotNull
  private BuildTypeResolver myBuildTypeResolver;

  @Autowired
  @NotNull
  private AgentBuildTypeEdgeResolver myAgentBuildTypeEdgeResolver;

  @Autowired
  @NotNull
  private AgentPoolResolver myAgentPoolResolver;

  @Autowired
  @NotNull
  private ProjectAgentPoolResolver myProjectAgentPoolResolver;

  @Autowired
  @NotNull
  private CloudImageResolver myCloudImageResolver;

  @Bean
  public GraphQL graphQL() {
    return graphQL;
  }

  @Autowired
  @NotNull
  private ResolverExceptionHandler myExceptionHandler;

  @PostConstruct
  public void init() throws IOException {
    // disable excessive logging
    org.apache.log4j.Logger.getLogger("notprivacysafe.graphql").setLevel(Level.ERROR);

    URL url = GraphQLProvider.class.getClassLoader().getResource("main/resources/schema.graphqls");
    if(url == null) {
      throw new IOException("Can't find schema.graphls");
    }

    String sdl = Resources.toString(url, Charsets.UTF_8);
    GraphQLSchema graphQLSchema = buildSchema(sdl);
    graphQL = GraphQL
      .newGraphQL(graphQLSchema)
      .queryExecutionStrategy(new AsyncExecutionStrategy(myExceptionHandler))
      .mutationExecutionStrategy(new AsyncSerialExecutionStrategy(myExceptionHandler))
      .subscriptionExecutionStrategy(new SubscriptionExecutionStrategy(myExceptionHandler))
      .build();
  }

  private GraphQLSchema buildSchema(String sdl) {
    return SchemaParser.newParser()
                .schemaString(sdl)
                .options(SchemaParserOptions.newOptions().coroutineContext((CoroutineContext) ExecutorsKt.from(myExecutor)).build())
                .resolvers(
                  myQuery,
                  myMutation,
                  myAgentPoolMutation,
                  myProjectResolver,
                  myAgentResolver,
                  myBuildTypeResolver,
                  myAgentBuildTypeEdgeResolver,
                  myAgentPoolResolver,
                  myProjectAgentPoolResolver,
                  myCloudImageResolver
                )
                .dictionary(
                  InvalidRunParameterAgentBuildTypeIncompatibility.class,
                  MissedVCSPluginAgentBuildTypeIncompatibility.class,
                  RunnerAgentBuildTypeIncompatibility.class,
                  UndefinedRunParameterAgentBuildTypeIncompatibility.class,
                  UnmetRequirementAgentBuildTypeIncompatibility.class,
                  MissingGlobalOrPerProjectPermission.class,
                  MissingGlobalPermission.class
                )
                .build()
                .makeExecutableSchema();
  }
}
