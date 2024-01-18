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

package jetbrains.buildServer.server.rest.service.rest;

import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyInjectable;
import jetbrains.buildServer.server.rest.model.server.GlobalSettings;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.crypt.BaseEncryptionStrategy;
import jetbrains.buildServer.serverSide.crypt.CustomKeyEncryptionStrategy;
import jetbrains.buildServer.serverSide.crypt.EncryptionManager;
import jetbrains.buildServer.serverSide.crypt.EncryptionSettings;
import jetbrains.buildServer.serverSide.impl.ServerSettingsImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@JerseyInjectable
@Service
public class ServerGlobalSettingsRestService {
  @NotNull private final PermissionChecker myPermissionChecker;
  @NotNull private final ServerSettingsImpl myServerSettings;
  @NotNull private final EncryptionManager myEncryptionManager;

  public ServerGlobalSettingsRestService(@NotNull PermissionChecker permissionChecker,
                                         @NotNull ServiceLocator serviceLocator) {
    myPermissionChecker = permissionChecker;
    myServerSettings = serviceLocator.getSingletonService(ServerSettingsImpl.class);
    myEncryptionManager = serviceLocator.getSingletonService(EncryptionManager.class);
  }

  @NotNull
  public GlobalSettings getGlobalSettings() {
    myPermissionChecker.checkGlobalPermissionAnyOf(new Permission[]{Permission.VIEW_SERVER_SETTINGS, Permission.CHANGE_SERVER_SETTINGS});
    return new GlobalSettings(myServerSettings);
  }

  @NotNull
  public GlobalSettings setGlobalSettings(GlobalSettings settings) {
    myPermissionChecker.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);

    if (settings.getArtifactDirectories() != null) {
      myServerSettings.setArtifactDirectories(settings.getArtifactDirectories());
    }
    if (settings.getRootUrl() != null) {
      myServerSettings.setRootUrl(settings.getRootUrl());
    }
    if (settings.getMaxArtifactSize() != null) {
      myServerSettings.setMaximumAllowedArtifactSize(settings.getMaxArtifactSize());
    }
    if (settings.getMaxArtifactsNumber() != null) {
      myServerSettings.setMaximumAllowedArtifactsNumber(settings.getMaxArtifactsNumber());
    }
    if (settings.getDefaultExecutionTimeout() != null) {
      myServerSettings.setDefaultExecutionTimeout(settings.getDefaultExecutionTimeout());
    }
    if (settings.getDefaultVCSCheckInterval() != null) {
      myServerSettings.setDefaultModificationCheckInterval(settings.getDefaultVCSCheckInterval());
    }
    if (settings.getEnforceDefaultVCSCheckInterval() != null) {
      myServerSettings.setMinimumCheckIntervalEnforced(settings.getEnforceDefaultVCSCheckInterval());
    }
    if (settings.getDefaultQuietPeriod() != null) {
      myServerSettings.setDefaultQuietPeriod(settings.getDefaultQuietPeriod());
    }

    if (settings.getUseEncryption() != null) {
      if (settings.getUseEncryption()) {
        String key = settings.getEncryptionKey();
        if (key == null) {
          throw new BadRequestException("'encryptionKey' must be provided");
        }
        myEncryptionManager.setDefaultStrategy(CustomKeyEncryptionStrategy.STRATEGY_NAME, key);
      } else {
        myEncryptionManager.setDefaultStrategy(BaseEncryptionStrategy.BASE_STRATEGY_NAME);
      }
      myServerSettings.setEncryptionStrategy(myEncryptionManager.getDefaultStrategy().getName());
      myServerSettings.setEncryptionKey(myEncryptionManager.getDefaultStrategy().getDefaultKeyName());
    }

    if (settings.getArtifactsDomainIsolation() != null) {
      myServerSettings.setDomainIsolationProtectionEnabled(settings.getArtifactsDomainIsolation());
    }
    if (settings.getArtifactsUrl() != null) {
      myServerSettings.setArtifactsRootUrl(settings.getArtifactsUrl());
    }

    myServerSettings.persistConfiguration();

    return new GlobalSettings(myServerSettings);
  }

  public void initForTests(EncryptionSettings encryptionSettings) {
    myServerSettings.setEncryptionSettings(encryptionSettings);
  }
}
