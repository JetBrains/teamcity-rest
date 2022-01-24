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
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.serverSide.Parameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 07/07/2016
 */
public interface EntityWithParameters {

  @NotNull
  Collection<Parameter> getParametersCollection(@Nullable final Locator locator);

  @Nullable
  Parameter getParameter(@NotNull String paramName);


  /**
   * @return null if own parameters are not supported.
   * @see also InheritableUserParametersHolder
   */
  @Nullable
  default Collection<Parameter> getOwnParametersCollection() {
    return null;
  }

  @Nullable
  default Parameter getOwnParameter(@NotNull String paramName) {
    Collection<Parameter> ownParametersCollection = getOwnParametersCollection();
    if (ownParametersCollection == null) return null;
    for (Parameter parameter : ownParametersCollection) {
      if (paramName.equals(parameter.getName())) return parameter;
    }
    return null;
  }

  /**
   * @param paramName name of the existing parameter
   * @return null if parameter inheritance is not supported by the entity, true/false otherwise
   */
  @Nullable
  default Boolean isInherited(@NotNull final String paramName) {
    Collection<Parameter> ownParametersCollection = getOwnParametersCollection();
    if (ownParametersCollection == null) return null;
    for (Parameter parameter : ownParametersCollection) {
      if (paramName.equals(parameter.getName())) return false;
    }
    return true;
  }
}