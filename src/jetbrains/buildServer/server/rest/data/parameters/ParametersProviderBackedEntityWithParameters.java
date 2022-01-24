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

package jetbrains.buildServer.server.rest.data.parameters;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.ParameterCondition;
import jetbrains.buildServer.serverSide.Parameter;
import jetbrains.buildServer.serverSide.SimpleParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ParametersProviderBackedEntityWithParameters implements EntityWithParameters {
  @NotNull
  private final ParametersProvider myProvider;
  @Nullable
  private List<Parameter> myParameters = null;

  public ParametersProviderBackedEntityWithParameters(@NotNull ParametersProvider provider) {
    myProvider = provider;
  }

  @NotNull
  @Override
  public Collection<Parameter> getParametersCollection(@Nullable Locator locator) {
    if(myParameters != null) {
      return myParameters;
    }

    myParameters = myProvider.getAll().entrySet().stream()
                             .map(name2Value -> new SimpleParameter(name2Value.getKey(), name2Value.getValue()))
                             .collect(Collectors.toList());

    return myParameters;
  }

  @Nullable
  @Override
  public Parameter getParameter(@NotNull String paramName) {
    String value = myProvider.get(paramName);
    if(value == null)
      return null;

    return new SimpleParameter(paramName, value);
  }

  @Nullable
  @Override
  public Collection<Parameter> getOwnParametersCollection() {
    return null;
  }

  @Nullable
  @Override
  public Parameter getOwnParameter(@NotNull String paramName) {
    return null;
  }

  @Nullable
  @Override
  public Boolean isInherited(@NotNull String paramName) {
    return null;
  }
}
