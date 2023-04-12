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

package jetbrains.buildServer.server.rest;

import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.impl.RestApiFacade;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * There is a dependency in the core which needs to be notified when this plugin is unloaded.
 * In order to avoid creating any additional dependency on this plugin in the core (e.g. listne for lifecycle), we notify the core ourselves on plugin unload.
 */
@Component
public class PluginUnloadListener extends BuildServerAdapter {
  private final RestApiFacade myRestFacadeInCore;

  public PluginUnloadListener(@NotNull SBuildServer server, @NotNull RestApiFacade restFacadeInCore) {
    myRestFacadeInCore = restFacadeInCore;
    server.addListener(this);
  }

  @Override
  public void serverShutdown() {
    myRestFacadeInCore.forgetRestController();
  }
}
