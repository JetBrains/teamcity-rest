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

package jetbrains.buildServer.server.rest.service.core.impl;

import java.util.List;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.service.core.ProjectSshKeyCoreService;
import jetbrains.buildServer.serverSide.ConfigAction;
import jetbrains.buildServer.serverSide.ConfigActionFactory;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Service
public class ProjectSshKeyCoreServiceImpl implements ProjectSshKeyCoreService {

  private final ServerSshKeyManager myServerSshKeyManager;
  private final ConfigActionFactory myConfigActionFactory;

  public ProjectSshKeyCoreServiceImpl(ServerSshKeyManager serverSshKeyManager, ConfigActionFactory configActionFactory) {
    myServerSshKeyManager = serverSshKeyManager;
    myConfigActionFactory = configActionFactory;
  }

  @NotNull
  @Override
  public List<TeamCitySshKey> getSshKeys(@NotNull SProject project) {
    return myServerSshKeyManager.getKeys(project);
  }

  @Override
  public void addSshKey(@NotNull SProject project, @NotNull String fileName, @NotNull byte[] privateKey) {
    try {
      validateKey(privateKey);
    } catch (Exception e) {
      throw new BadRequestException("Invalid key file: " + e.getCause(), e);
    }

    ConfigAction configAction = myConfigActionFactory.createAction("New SSH key uploaded");
    myServerSshKeyManager.addKey(project, fileName, privateKey, configAction);
  }

  @Override
  public void deleteSshKey(@NotNull SProject project, @NotNull String fileName) {
      ConfigAction configAction = myConfigActionFactory.createAction("SSH key deleted");
      myServerSshKeyManager.removeKey(project, fileName, configAction);
  }

  private void validateKey(byte[] privateKey) {
    if (privateKey == null || privateKey.length == 0) {
      throw new IllegalArgumentException("Empty key file");
    }
    // TODO @vshefer validate key file
  }

}
