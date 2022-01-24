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
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.serverSide.Parameter;
import jetbrains.buildServer.serverSide.SimpleParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 07/07/2016
 */
public class MapBackedEntityWithModifiableParameters implements EntityWithModifiableParameters {
  @NotNull private final PropProxy myProvider;

  public MapBackedEntityWithModifiableParameters(@NotNull final PropProxy provider) {
    myProvider = provider;
  }

  @Override
  public void addParameter(@NotNull final Parameter param) {
    Map<String, String> params = new HashMap<>(myProvider.get());
    params.put(param.getName(), param.getValue());
    myProvider.set(params);
  }

  @Override
  public void removeParameter(@NotNull final String paramName) {
    Map<String, String> params = new HashMap<>(myProvider.get());
    params.remove(paramName);
    myProvider.set(params);
  }

  @NotNull
  @Override
  public Collection<Parameter> getParametersCollection(@Nullable final Locator locator) {
    return Properties.convertToSimpleParameters(myProvider.get());
  }

  @Nullable
  @Override
  public Parameter getParameter(@NotNull final String paramName) {
    String value = myProvider.get().get(paramName);
    return value == null ? null : new SimpleParameter(paramName, value);
  }

  public interface PropProxy {
    Map<String, String> get();

    void set(Map<String, String> params);
  }
}
