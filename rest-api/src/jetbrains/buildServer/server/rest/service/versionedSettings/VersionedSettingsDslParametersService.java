/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.service.versionedSettings;

import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyInjectable;
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsContextParameters;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;

@JerseyInjectable
public interface VersionedSettingsDslParametersService {

  /**
   * Returns a list containing both DSL context parameters specified in project config
   * and empty required parameters for DSL run
   * @param project project with enabled versioned settings feature
   * @return see above
   */
  @NotNull VersionedSettingsContextParameters getVersionedSettingsContextParameters(@NotNull SProject project);

  /**
   * Sets given versioned settings DSL context parameters
   * @param project project with enabled versioned settings feature
   * @param versionedSettingsContextParameters context parameters to set
   */
  void setVersionedSettingsContextParameters(@NotNull SProject project,
                                             @NotNull VersionedSettingsContextParameters versionedSettingsContextParameters);

}
