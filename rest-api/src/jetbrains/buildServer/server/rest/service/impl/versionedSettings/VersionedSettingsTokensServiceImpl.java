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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jetbrains.buildServer.controllers.project.VersionedSettingsTokensControllerHelper;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyInjectable;
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsToken;
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsTokens;
import jetbrains.buildServer.server.rest.service.versionedSettings.VersionedSettingsTokensService;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.versionedSettings.SecureValue;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;


@JerseyInjectable
@Service
public class VersionedSettingsTokensServiceImpl implements VersionedSettingsTokensService {

  @NotNull private final VersionedSettingsTokensControllerHelper myVersionedSettingsTokensControllerHelper;
  @NotNull private final PermissionChecker myPermissionChecker;


  public VersionedSettingsTokensServiceImpl(@NotNull VersionedSettingsTokensControllerHelper versionedSettingsTokensControllerHelper,
                                        @NotNull PermissionChecker permissionChecker) {
    myVersionedSettingsTokensControllerHelper = versionedSettingsTokensControllerHelper;
    myPermissionChecker = permissionChecker;
  }

  @Override
  @NotNull
  public VersionedSettingsTokens getTokens(@NotNull SProject project, @Nullable String status) {
    Map<String, SecureValue> tokensMap = myVersionedSettingsTokensControllerHelper.getTokens(project).getTokensMap();
    List<Map.Entry<String, SecureValue>> tokensList;
    if (StringUtil.isEmpty(status)) {
      tokensList = myVersionedSettingsTokensControllerHelper.getAllTokensList(tokensMap);
    } else if (VersionedSettingsTokenStatus.USED.toString().equals(status)) {
      tokensList = myVersionedSettingsTokensControllerHelper.getUsedTokensList(tokensMap);
    } else if (VersionedSettingsTokenStatus.UNUSED.toString().equals(status)) {
      tokensList = myVersionedSettingsTokensControllerHelper.getUnusedTokensList(tokensMap);
    } else if (VersionedSettingsTokenStatus.BROKEN.toString().equals(status)) {
      tokensList = myVersionedSettingsTokensControllerHelper.getBrokenTokensList(tokensMap);
    } else {
      throw new OperationException("Wrong token status '" + status + "' specified, " +
                                   "choose one from " + Arrays.stream(VersionedSettingsTokenStatus.values()).map(st -> "'" + st.toString() + "'").collect(Collectors.joining(", ")) +
                                   " or leave empty to get all tokens.");
    }
    List<VersionedSettingsToken> versionedSettingsTokenList = tokensList.stream()
                                                                        .map(e -> new VersionedSettingsToken(e.getKey(), e.getValue()).removeValue())
                                                                        .collect(Collectors.toList());
    return new VersionedSettingsTokens(versionedSettingsTokenList);
  }

  @Override
  public void setTokens(@NotNull SProject project, @NotNull VersionedSettingsTokens versionedSettingsTokens) {
    checkPermissions(project);
    Map<String, String> tokensMap = versionedSettingsTokens.getTokens().stream()
                                                           .filter(token -> token.getValue() != null)
                                                           .collect(Collectors.toMap(token -> token.getName(), token -> token.getValue()));
    myVersionedSettingsTokensControllerHelper.setTokens(project, tokensMap);
  }

  @Override
  public void deleteTokens(@NotNull SProject project, @NotNull VersionedSettingsTokens versionedSettingsTokens) {
    checkPermissions(project);
    if (versionedSettingsTokens.getTokens().stream().anyMatch(token -> myVersionedSettingsTokensControllerHelper.isTokenUsed(project, token.getName()))) {
      throw new BadRequestException("Cannot delete tokens in use");
    }
    List<String> tokensList = versionedSettingsTokens.getTokens().stream()
                                                     .map(token -> token.getName())
                                                     .collect(Collectors.toList());
    myVersionedSettingsTokensControllerHelper.removeTokensFromProject(project, tokensList);
  }

  private void checkPermissions(@NotNull SProject project) {
    if (myPermissionChecker.getCurrent().getAssociatedUser() != null &&
        !myVersionedSettingsTokensControllerHelper.canUserEditTokens(myPermissionChecker.getCurrent().getAssociatedUser(), project)) {
      throw new AuthorizationFailedException("No permissions to edit tokens");
    }
  }


  private enum VersionedSettingsTokenStatus {
    USED,
    UNUSED,
    BROKEN;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }
}
