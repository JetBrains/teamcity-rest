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

import jetbrains.buildServer.controllers.project.VersionedSettingsBean;
import jetbrains.buildServer.controllers.project.VersionedSettingsConfigUpdater;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.versionedSettings.VersionedSettingsBeanCollector;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsConfig;
import jetbrains.buildServer.server.rest.service.versionedSettings.VersionedSettingsConfigsService;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

@JerseyContextSingleton
@Service
public class VersionedSettingsConfigsServiceImpl implements VersionedSettingsConfigsService {

  @NotNull private final VersionedSettingsConfigUpdater myVersionedSettingsConfigUpdater;
  @NotNull private final VersionedSettingsBeanCollector myVersionedSettingsBeanCollector;
  @NotNull private final PermissionChecker myPermissionChecker;


  public VersionedSettingsConfigsServiceImpl(@NotNull VersionedSettingsConfigUpdater versionedSettingsConfigUpdater,
                                             @NotNull VersionedSettingsBeanCollector versionedSettingsBeanCollector,
                                             @NotNull PermissionChecker permissionChecker) {
    myVersionedSettingsConfigUpdater = versionedSettingsConfigUpdater;
    myVersionedSettingsBeanCollector = versionedSettingsBeanCollector;
    myPermissionChecker = permissionChecker;
  }

  @Override
  @Nullable
  public VersionedSettingsConfig getVersionedSettingsConfig(@NotNull SProject project, @NotNull Fields fields) {
    checkAccess(project, (SUser) myPermissionChecker.getCurrent().getAssociatedUser());

    VersionedSettingsBean versionedSetttingsBean = myVersionedSettingsBeanCollector.getItem(project);
    return new VersionedSettingsConfig(versionedSetttingsBean, fields);
  }

  @Override
  public void setVersionedSettingsConfig(@NotNull SProject project, @NotNull VersionedSettingsConfig versionedSettingsConfig) {
    SUser user = (SUser) myPermissionChecker.getCurrent().getAssociatedUser();
    checkAccess(project, user);

    if (versionedSettingsConfig.getSyncronizationMode() == null) {
      throw new OperationException("Mandatory 'syncronizationMode' property is not specified.");
    }
    final String syncronizationMode = versionedSettingsConfig.getSyncronizationMode().getParamValue();
    final String vcsRoodId = versionedSettingsConfig.getVcsRootId();
    final boolean showSettingsChanges = versionedSettingsConfig.getShowSettingsChanges() != null && versionedSettingsConfig.getShowSettingsChanges();  // false by default
    final boolean useCredentialsStorage = versionedSettingsConfig.getStoreSecureValuesOutsideVcs() == null || versionedSettingsConfig.getStoreSecureValuesOutsideVcs();  // true by default
    final String confirmationDecision = versionedSettingsConfig.getImportDecision() == null ? null : versionedSettingsConfig.getImportDecision().getParamValue();
    final String formatParam = versionedSettingsConfig.getFormat();
    boolean useRelativeIdsParam = versionedSettingsConfig.getPortableDsl() == null || versionedSettingsConfig.getPortableDsl();  // true by default
    if (!((ProjectEx)project).getBooleanInternalParameter("kotlinDsl.newProjects.allowUsingNonPortableDSL") &&
        !useRelativeIdsParam &&
        formatParam.equals("kotlin")) {
      throw new OperationException("Non-portable Kotlin DSL format is deprecated.");
    }
    final boolean useTwoWaySync = versionedSettingsConfig.getAllowUIEditing() == null || versionedSettingsConfig.getAllowUIEditing();  // true by default
    final String buildSettingsModeParam = versionedSettingsConfig.getBuildSettingsMode() == null  // ALWAYS_USE_CURRENT by default
                                          ? VersionedSettingsConfig.BuildSettingsMode.alwaysUseCurrent.getBuildSettingsMode().toString()
                                          : versionedSettingsConfig.getBuildSettingsMode().getBuildSettingsMode().toString();

    VersionedSettingsConfigUpdater.Result updateConfigResult = myVersionedSettingsConfigUpdater.updateConfig(
      project,
      user,
      syncronizationMode,
      vcsRoodId,
      showSettingsChanges,
      useCredentialsStorage,
      confirmationDecision,
      formatParam,
      useRelativeIdsParam,
      useTwoWaySync,
      buildSettingsModeParam
    );
    switch (updateConfigResult.getStatus()) {
      case ERROR:
        throw new OperationException("Cannot update versioned settings config. " + updateConfigResult.getMessage());
      case CONFIRMATION_REQUIRED:
        throw new OperationException("VCS Root already contains project. Add 'importDecision' parameter " +
                                     "with 'overrideInVCS' value to override settings in VCS with current server settings," +
                                     "or 'importFromVCS' to import settings from VCS and override current server settings.");
    }
  }

  private void checkAccess(@NotNull SProject project, @Nullable SUser user) {
    if (user == null || !user.isPermissionGrantedForProject(project.getProjectId(), Permission.EDIT_PROJECT)) {
      throw new AuthorizationFailedException("User has no permissions to edit project.");
    }
  }
}
