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

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.model.server.AuthModule;
import jetbrains.buildServer.server.rest.model.server.AuthSettings;
import jetbrains.buildServer.serverSide.ServerSettings;
import jetbrains.buildServer.serverSide.auth.AuthModuleType;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.impl.ServerSettingsImpl;
import jetbrains.buildServer.serverSide.impl.auth.AuthConfigManager;
import jetbrains.buildServer.serverSide.impl.auth.LoginConfigurationEx;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@JerseyContextSingleton
@Service
public class ServerAuthRestService {
  @NotNull private final PermissionChecker myPermissionChecker;
  @NotNull private final ServiceLocator myServiceLocator;

  public ServerAuthRestService(@NotNull PermissionChecker permissionChecker,
                               @NotNull ServiceLocator serviceLocator) {
    myPermissionChecker = permissionChecker;
    myServiceLocator = serviceLocator;
  }

  @NotNull
  public AuthSettings getAuthSettings() {
    myPermissionChecker.checkGlobalPermissionAnyOf(new Permission[]{Permission.VIEW_SERVER_SETTINGS, Permission.MANAGE_AUTHENTICATION_SETTINGS});
    LoginConfigurationEx config = myServiceLocator.getSingletonService(LoginConfigurationEx.class);
    ServerSettings server = myServiceLocator.getSingletonService(ServerSettings.class);
    return new AuthSettings(config, server, myServiceLocator);
  }

  @NotNull
  public AuthSettings setAuthSettings(AuthSettings settings) {
    myPermissionChecker.checkGlobalPermission(Permission.MANAGE_AUTHENTICATION_SETTINGS);
    LoginConfigurationEx config = myServiceLocator.getSingletonService(LoginConfigurationEx.class);
    ServerSettingsImpl server = myServiceLocator.getSingletonService(ServerSettingsImpl.class);
    AuthConfigManager manager = myServiceLocator.getSingletonService(AuthConfigManager.class);

    if (settings.getModules() == null || settings.getModules().getModules() == null) {
      throw new BadRequestException("At least one auth module must be specified");
    }
    for (AuthModule module : settings.getModules().getModules()) {
      AuthModuleType m = config.findAuthModuleTypeByName(module.getName());
      if (m == null) {
        throw new BadRequestException("Auth module '" + module.getName() + "' is not found");
      }
    }

    config.performBatchChange(() -> {
      if (settings.getAllowGuest() != null) {
        config.setGuestLoginAllowed(settings.getAllowGuest());
      }
      if (settings.getGuestUsername() != null) {
        config.setGuestUsername(settings.getGuestUsername());
      }
      if (settings.getWelcomeText() != null) {
        config.setTextForLoginPage(settings.getWelcomeText());
      }
      if (settings.getCollapseLoginForm() != null) {
        config.setLoginFormCollapsed(settings.getCollapseLoginForm());
      }

      config.clearConfiguredAuthModules();
      for (AuthModule module : settings.getModules().getModules()) {
        AuthModuleType m = config.findAuthModuleTypeByName(module.getName());
        Map<String, String> props = module.getProperties() != null ? module.getProperties().getMap() : new HashMap<>();
        config.addAuthModule(m, props);
      }
    });

    if (settings.getPerProjectPermissions() != null) {
      server.setPerProjectPermissionsEnabled(settings.getPerProjectPermissions());
    }
    if (settings.getEmailVerification() != null) {
      server.setEmailVerificationEnabled(settings.getEmailVerification());
    }

    manager.persistConfiguration();
    server.persistConfiguration();

    return new AuthSettings(config, server, myServiceLocator);
  }
}
