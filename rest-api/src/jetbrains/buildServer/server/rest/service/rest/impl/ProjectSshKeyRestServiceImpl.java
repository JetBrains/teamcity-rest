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

package jetbrains.buildServer.server.rest.service.rest.impl;

import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.rest.data.finder.impl.ProjectFinder;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyInjectable;
import jetbrains.buildServer.server.rest.model.project.SshKey;
import jetbrains.buildServer.server.rest.model.project.SshKeys;
import jetbrains.buildServer.server.rest.service.core.ProjectSshKeyCoreService;
import jetbrains.buildServer.server.rest.service.rest.ProjectSshKeyRestService;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@JerseyInjectable
@Service
public class ProjectSshKeyRestServiceImpl implements ProjectSshKeyRestService {

  private final ProjectFinder myProjectFinder;
  private final ProjectSshKeyCoreService myProjectSshKeyCoreService;

  public ProjectSshKeyRestServiceImpl(
    ProjectFinder projectFinder,
    ProjectSshKeyCoreService projectSshKeyCoreService
  ) {
    myProjectFinder = projectFinder;
    myProjectSshKeyCoreService = projectSshKeyCoreService;
  }

  @NotNull
  @Override
  public SshKeys getSshKeys(@NotNull String projectLocator) {
    SProject project = myProjectFinder.getItem(projectLocator);
    List<TeamCitySshKey> keys = myProjectSshKeyCoreService.getSshKeys(project);

    List<SshKey> sshKeys = keys
      .stream()
      .map(key -> toSshKey(key))
      .collect(Collectors.toList());

    SshKeys result = new SshKeys();
    result.setSshKeys(sshKeys);
    return result;
  }

  @Override
  public void addSshKey(@NotNull String projectLocator, @NotNull String keyName, @NotNull byte[] privateKey) {
    SProject project = myProjectFinder.getItem(projectLocator);

    myProjectSshKeyCoreService.addSshKey(project, keyName, privateKey);
  }

  @NotNull
  @Override
  public SshKey generateSshKey(@NotNull String projectLocator, @NotNull String keyName, @NotNull String keyType) {
    SProject project = myProjectFinder.getItem(projectLocator);
    TeamCitySshKey key = myProjectSshKeyCoreService.generateSshKey(project, keyName, keyType);
    SshKey result = toSshKey(key);
    if (!key.isEncrypted()) {
      result.setPublicKey(myProjectSshKeyCoreService.getPublicKey(key));
    }
    return result;
  }

  @Override
  public void deleteSshKey(@NotNull String projectLocator, @NotNull String keyName) {
    SProject project = myProjectFinder.getItem(projectLocator);
    myProjectSshKeyCoreService.deleteSshKey(project, keyName);
  }

  @NotNull
  private static SshKey toSshKey(TeamCitySshKey key) {
    SshKey sshKey = new SshKey();
    sshKey.setName(key.getName());
    sshKey.setEncrypted(key.isEncrypted());
    return sshKey;
  }
}
