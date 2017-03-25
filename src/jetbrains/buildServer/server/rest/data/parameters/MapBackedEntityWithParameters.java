/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
public class MapBackedEntityWithParameters implements EntityWithParameters {
  @NotNull private final Map<String, String> myParams;
  @Nullable private final Map<String, String> myOwnParams;

  public MapBackedEntityWithParameters(@NotNull final Map<String, String> params, @Nullable final Map<String, String> ownParams) {
    myParams = params;
    myOwnParams = ownParams;
  }

  @NotNull
  @Override
  public Collection<Parameter> getParametersCollection(@Nullable final Locator locator) {   //todo: test without filter!
    return Properties.convertToSimpleParameters(myParams);
  }

  @Nullable
  @Override
  public Parameter getParameter(@NotNull final String paramName) {
    String value = myParams.get(paramName);
    return value == null ? null : new SimpleParameter(paramName, value);
  }

  @Nullable
  @Override
  public Collection<Parameter> getOwnParametersCollection() {
    return myOwnParams == null ? null : Properties.convertToSimpleParameters(myOwnParams);
  }

  @Nullable
  @Override
  public Parameter getOwnParameter(@NotNull final String paramName) {
    if (myOwnParams == null) return null;
    String value = myOwnParams.get(paramName);
    return value == null ? null : new SimpleParameter(paramName, value);
  }

  @Nullable
  @Override
  public Boolean isInherited(@NotNull final String paramName) {
    if (myOwnParams == null) return null;
    return myOwnParams.get(paramName) == null;
  }
}
