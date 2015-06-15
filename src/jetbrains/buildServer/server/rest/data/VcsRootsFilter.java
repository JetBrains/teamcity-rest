/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
import java.util.Collection;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.vcs.*;
import jetbrains.vcs.api.services.tc.PersonalSupportService;
import jetbrains.vcs.api.services.tc.VcsMappingElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.13
 */
public class VcsRootsFilter extends AbstractFilter<SVcsRoot> {
  private static final Logger LOG = Logger.getInstance(VcsRootsFilter.class.getName());
  public static final String REPOSITORY_ID_STRING = "repositoryIdString";

  @Nullable private final String myVcsType;
  @Nullable private final String myRepositoryIdString;
  @Nullable private final SProject myProject;
  private final VcsManager myVcsManager;
  @NotNull private final VcsRootFinder myVcsRootFinder;

  public VcsRootsFilter(@NotNull final Locator locator, @NotNull ProjectFinder projectFinder, @NotNull VcsManager vcsManager, final @NotNull VcsRootFinder vcsRootFinder) {
    super(locator.getSingleDimensionValueAsLong(PagerData.START),
          locator.getSingleDimensionValueAsLong(PagerData.COUNT) != null ? locator.getSingleDimensionValueAsLong(PagerData.COUNT).intValue() : null,
          null);
    myVcsManager = vcsManager;
    myVcsRootFinder = vcsRootFinder;
    myVcsType = locator.getSingleDimensionValue("type");
    final String projectLocator = locator.getSingleDimensionValue("project");
    if (projectLocator != null) {
      myProject = projectFinder.getProject(projectLocator);
    } else {
      myProject = null;
    }
    myRepositoryIdString = locator.getSingleDimensionValue(REPOSITORY_ID_STRING);
  }

  @Override
  protected boolean isIncluded(@NotNull SVcsRoot root) {
    try {
      myVcsRootFinder.checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, root);
    } catch (AuthorizationFailedException e) {
      return false;
    }
    return (myVcsType == null || myVcsType.equals(root.getVcsName())) &&
           (myProject == null || myProject.equals(root.getProject())) &&
           (myRepositoryIdString == null || repositoryIdStringMatches(root, myRepositoryIdString, myVcsManager));
  }

  static boolean repositoryIdStringMatches(@NotNull final jetbrains.buildServer.vcs.VcsRoot root,
                                           @NotNull final String repositoryIdString,
                                           final VcsManager vcsManager) {
    //todo: handle errors
    final VcsSupportCore vcsSupport = vcsManager.findVcsByName(root.getVcsName());
    if (vcsSupport != null) {
      final PersonalSupportService personalSupportService =
              vcsManager.getVcsService(new VcsRootEntry(root, CheckoutRules.DEFAULT), PersonalSupportService.class);

      if (personalSupportService != null) {
        try {
          if (null != personalSupportService.mapPath(repositoryIdString, true).getMappedPath())
            return true;
        } catch (VcsException e) {
          LOG.debug("Error while retrieving mapping for VCS root " + LogUtil.describe(root) + " via mapFullPath, ignoring", e);
        }
      } else {
        LOG.debug("No personal support for VCS root " + LogUtil.describe(root) + " found, ignoring root in search");
        return false;
      }
    } else {
      LOG.debug("No VCS support for VCS root " + LogUtil.describe(root) + " found, ignoring root in search");
      return false;
    }

    try {
      Collection<VcsMappingElement> vcsMappingElements = VcsRoot.getRepositoryMappings(root, vcsManager);
      for (VcsMappingElement vcsMappingElement : vcsMappingElements) {
        if (repositoryIdString.equals(vcsMappingElement.getTo())) {
          return true;
        }
      }
    } catch (Exception e) {
      LOG.debug("Error while retrieving mapping for VCS root " + LogUtil.describe(root) + ". ignoring root in search", e);
    }
    return false;
  }


}
