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

package jetbrains.buildServer.server.graphql.resolver;

import graphql.cachecontrol.CacheControl;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedField;
import graphql.execution.directives.QueryDirectives;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.schema.*;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockDataFetchingEnvironment implements DataFetchingEnvironment {
  @Nullable
  private Object mySource;
  @NotNull
  private final Map<String, Object> myArguments = new HashMap<>();
  @Nullable
  private Object myContext;
  @Nullable
  private Object myLocalContext;

  @Override
  public <T> T getSource() {
    return (T) mySource;
  }

  public void setSource(@Nullable Object source) {
    mySource = source;
  }

  @Override
  public Map<String, Object> getArguments() {
    return myArguments;
  }

  @Override
  public boolean containsArgument(String name) {
    return myArguments.containsKey(name);
  }

  @Override
  public <T> T getArgument(String name) {
    return (T) myArguments.get(name);
  }

  public <T> void setArgument(String name, T value) {
    myArguments.put(name, value);
  }

  @Override
  public <T> T getArgumentOrDefault(String name, T defaultValue) {
    return (T) myArguments.getOrDefault(name, defaultValue);
  }

  @Override
  public <T> T getContext() {
    return (T)myContext;
  }

  public void setContext(@Nullable Object context) {
    myContext = context;
  }

  @Override
  public <T> T getLocalContext() {
    return (T) myLocalContext;
  }

  public void setLocalContext(@Nullable Object localContext) {
    myLocalContext = localContext;
  }

  @Override
  public <T> T getRoot() {
    return null;
  }

  @Override
  public GraphQLFieldDefinition getFieldDefinition() {
    return null;
  }

  @Override
  public List<Field> getFields() {
    return null;
  }

  @Override
  public MergedField getMergedField() {
    return null;
  }

  @Override
  public Field getField() {
    return null;
  }

  @Override
  public GraphQLOutputType getFieldType() {
    return null;
  }

  @Override
  public ExecutionStepInfo getExecutionStepInfo() {
    return null;
  }

  @Override
  public GraphQLType getParentType() {
    return null;
  }

  @Override
  public GraphQLSchema getGraphQLSchema() {
    return null;
  }

  @Override
  public Map<String, FragmentDefinition> getFragmentsByName() {
    return null;
  }

  @Override
  public ExecutionId getExecutionId() {
    return null;
  }

  @Override
  public DataFetchingFieldSelectionSet getSelectionSet() {
    return null;
  }

  @Override
  public QueryDirectives getQueryDirectives() {
    return null;
  }

  @Override
  public <K, V> DataLoader<K, V> getDataLoader(String dataLoaderName) {
    return null;
  }

  @Override
  public DataLoaderRegistry getDataLoaderRegistry() {
    return null;
  }

  @Override
  public CacheControl getCacheControl() {
    return null;
  }

  @Override
  public Locale getLocale() {
    return null;
  }

  @Override
  public OperationDefinition getOperationDefinition() {
    return null;
  }

  @Override
  public Document getDocument() {
    return null;
  }

  @Override
  public Map<String, Object> getVariables() {
    return null;
  }
}
