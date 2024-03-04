package jetbrains.buildServer.server.graphql;

import graphql.GraphQL;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyInjectableBeanProvider;
import org.springframework.stereotype.Service;

@Service
public class GraphQLProvider implements JerseyInjectableBeanProvider {
  @Override
  public Class<?> getBeanClass() {
    return GraphQL.class;
  }
}
