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

package jetbrains.buildServer.server.rest.model.buildType;

import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 01/04/2016
 */
public interface PropEntityEdit<T> {
  /**
   * Adds the setting defined by the current instance to the buildType passes.
   * If the instance has "inherited=true" and buildType uses a tempalte and the template has an entity with all the same settings, the call does nothing, it is just ignored,
   * otherwise the settings is added.
   */
  @NotNull
  T addTo(@NotNull final BuildTypeOrTemplate buildType, @NotNull final ServiceLocator serviceLocator);

  @NotNull
  T replaceIn(@NotNull final BuildTypeOrTemplate buildType, @NotNull final T entityToReplace, @NotNull final ServiceLocator serviceLocator);
  //todo: currently, snapshot deps and agent requirements consider "inherited" attribute on replacemnt (can skip adding if there is alike entity in the template) and others don't - might need unifying

//  static void removeFrom(@NotNull final BuildTypeSettings buildType, @NotNull final T entity);
}
