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
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsConfig;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@JerseyInjectable
public interface VersionedSettingsConfigsService {

  @Nullable
  VersionedSettingsConfig getVersionedSettingsConfig(@NotNull SProject project, @NotNull Fields fields);

  void setVersionedSettingsConfig(@NotNull SProject project, @NotNull VersionedSettingsConfig versionedSettingsConfig);

  void checkEnabled(@NotNull SProject project);

}
