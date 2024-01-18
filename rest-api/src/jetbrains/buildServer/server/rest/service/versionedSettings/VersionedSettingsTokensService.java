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
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsTokens;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@JerseyInjectable
public interface VersionedSettingsTokensService {

  /**
   * Return Versioned Settings tokens without values for given project.
   *
   * @param project project to get tokens
   * @param status status to filter tokens to return, one from null to return all the tokens,
   *               or 'broken', 'used', 'unused'
   * @return Versioned Settings tokens
   */
  @NotNull
  VersionedSettingsTokens getTokens(@NotNull SProject project, @Nullable String status);


  /**
   * Set Versioned Settings token values with given names.
   * @param project project to set tokens
   * @param versionedSettingsTokens a collection of tokens with names and values to set
   */
  void setTokens(@NotNull SProject project, @NotNull VersionedSettingsTokens versionedSettingsTokens);

  /**
   * Remove Versioned Settings tokens with given names.
   * @param project project to remove tokens
   * @param versionedSettingsTokens a collection of tokens with names to remove
   */
  void deleteTokens(@NotNull SProject project, @NotNull VersionedSettingsTokens versionedSettingsTokens);

}
