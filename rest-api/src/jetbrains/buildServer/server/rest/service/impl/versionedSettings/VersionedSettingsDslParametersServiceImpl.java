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

package jetbrains.buildServer.server.rest.service.impl.versionedSettings;

import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.controllers.project.VersionedSettingsBean;
import jetbrains.buildServer.controllers.project.VersionedSettingsDslContextParameters;
import jetbrains.buildServer.server.rest.data.versionedSettings.VersionedSettingsBeanCollector;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyInjectable;
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsContextParameter;
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsContextParameters;
import jetbrains.buildServer.server.rest.service.versionedSettings.VersionedSettingsDslParametersService;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@JerseyInjectable
@Service
public class VersionedSettingsDslParametersServiceImpl implements VersionedSettingsDslParametersService {

  private final VersionedSettingsDslContextParameters myVersionedSettingsDslContextParameters;
  private final VersionedSettingsBeanCollector myVersionedSettingsBeanCollector;

  public VersionedSettingsDslParametersServiceImpl(@NotNull VersionedSettingsDslContextParameters versionedSettingsDslContextParameters,
                                                   VersionedSettingsBeanCollector versionedSettingsBeanCollector) {
    myVersionedSettingsDslContextParameters = versionedSettingsDslContextParameters;
    myVersionedSettingsBeanCollector = versionedSettingsBeanCollector;
  }

  @NotNull
  @Override
  public VersionedSettingsContextParameters getVersionedSettingsContextParameters(@NotNull SProject project) {
    VersionedSettingsBean settingsBean = myVersionedSettingsBeanCollector.getItem(project);
    List<VersionedSettingsContextParameter> allDslContextParameters =
      myVersionedSettingsDslContextParameters
        .getAllDslContextParameters(project, settingsBean).stream()
        .map(it -> new VersionedSettingsContextParameter(it.getName(), it.getValue()))
        .collect(Collectors.toList());
    return new VersionedSettingsContextParameters(allDslContextParameters);
  }

  @Override
  public void setVersionedSettingsContextParameters(@NotNull SProject project, @NotNull VersionedSettingsContextParameters versionedSettingsContextParameters) {
    myVersionedSettingsDslContextParameters.setConfigsDslParameters(project, versionedSettingsContextParameters.toMap());
  }
}
