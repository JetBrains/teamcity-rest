/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.parameters.impl.AbstractMapParametersProvider;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.identifiers.EntityId;
import jetbrains.buildServer.serverSide.identifiers.VcsRootIdentifiersManager;
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
public class VcsRootFinder extends AbstractFinder<SVcsRoot> {
  private static final Logger LOG = Logger.getInstance(VcsRootFinder.class.getName());
  public static final String REPOSITORY_ID_STRING = "repositoryIdString";
  protected static final String INTERNAL_ID = "internalId";
  protected static final String UUID = "uuid";
  protected static final String NAME = "name";
  protected static final String TYPE = "type";
  protected static final String PROJECT = "project";
  protected static final String AFFECTED_PROJECT = "affectedProject";
  protected static final String PROPERTY = "property";

  @NotNull private final VcsManager myVcsManager;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final BuildTypeFinder myBuildTypeFinder; //todo: add filtering by (multiple) build types???
  @NotNull private final ProjectManager myProjectManager;
  @NotNull private final VcsRootIdentifiersManager myVcsRootIdentifiersManager;
  @NotNull private final PermissionChecker myPermissionChecker;

  public VcsRootFinder(@NotNull VcsManager vcsManager,
                       @NotNull ProjectFinder projectFinder,
                       @NotNull BuildTypeFinder buildTypeFinder,
                       @NotNull ProjectManager projectManager,
                       @NotNull VcsRootIdentifiersManager vcsRootIdentifiersManager,
                       final @NotNull PermissionChecker permissionChecker) {
    super(new String[]{DIMENSION_ID, NAME, TYPE, PROJECT, AFFECTED_PROJECT, PROPERTY, REPOSITORY_ID_STRING, INTERNAL_ID, UUID,
      DIMENSION_LOOKUP_LIMIT, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME, PagerData.START, PagerData.COUNT});
    myVcsManager = vcsManager;
    myProjectFinder = projectFinder;
    myBuildTypeFinder = buildTypeFinder;
    myProjectManager = projectManager;
    myVcsRootIdentifiersManager = vcsRootIdentifiersManager;
    myPermissionChecker = permissionChecker;
  }

  @NotNull
  public static String getLocator(@NotNull final SVcsRoot vcsRoot) {
    return Locator.getStringLocator(DIMENSION_ID, String.valueOf(vcsRoot.getExternalId()));
  }

  @NotNull
  public static String getLocator(@NotNull final BuildProject project) {
    return Locator.getStringLocator(PROJECT, String.valueOf(ProjectFinder.getLocator(project)));
  }

  @NotNull
  @Override
  public Locator createLocator(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    final Locator result = super.createLocator(locatorText, locatorDefaults);
    result.addHiddenDimensions(PROPERTY); //experimental
    return result;
  }

  @Nullable
  @Override
  public ItemHolder<SVcsRoot> getAllItems() {
      final List<SVcsRoot> allRegisteredVcsRoots = myVcsManager.getAllRegisteredVcsRoots();
      final List<SVcsRoot> result = new ArrayList<SVcsRoot>(allRegisteredVcsRoots.size());
      for (SVcsRoot root : allRegisteredVcsRoots) {
        try {
          checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, root);
          result.add(root);
        } catch (AuthorizationFailedException e) {
          //ignore
        }
      }
      return getItemHolder(result);
  }

  @Nullable
  @Override
  protected SVcsRoot findSingleItem(@NotNull final Locator locator) {
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's an internal id or external id
      return getVcsRootByExternalOrInternalId(locator.getSingleValue());
    }

    final String id = locator.getSingleDimensionValue(DIMENSION_ID);
    if (id != null) {
      SVcsRoot root;
      if (TeamCityProperties.getBoolean(APIController.REST_COMPATIBILITY_ALLOW_EXTERNAL_ID_AS_INTERNAL)) {
        root = getVcsRootByExternalOrInternalId(id);
      } else {
        root = myProjectManager.findVcsRootByExternalId(id);
        if (root == null) {
          throw new NotFoundException("No VCS root can be found by id '" + id + "'.");
        }
        checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, root);
      }
      return root;
    }


    Long internalId = locator.getSingleDimensionValueAsLong(INTERNAL_ID);
    if (internalId != null) {
      SVcsRoot root = myVcsManager.findRootById(internalId);
      if (root == null) {
        throw new NotFoundException("No VCS root can be found by internal id '" + internalId + "'.");
      }
      checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, root);
      return root;
    }

    String uuid = locator.getSingleDimensionValue(UUID);
    if (uuid != null) {
      final EntityId<Long> internalVCSRootId = myVcsRootIdentifiersManager.findEntityIdByConfigId(uuid);
      if (internalVCSRootId != null) {
        SVcsRoot root = myVcsManager.findRootById(internalVCSRootId.getInternalId());
        if (root != null) {
          checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, root);
          return root;
        }
      }
      //protecting against brute force uuid guessing
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        //ignore
      }
      throw new NotFoundException("No VCS root can be found by uuid '" + uuid + "'.");
    }

    String rootName = locator.getSingleDimensionValue(NAME);
    if (rootName != null) {
      SVcsRoot root = myVcsManager.findRootByName(rootName);
      if (root == null) {
        throw new NotFoundException("No VCS root can be found by name '" + rootName + "'.");
      }
      checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, root);
      return root;
    }

    return null;
  }

  @NotNull
  @Override
  protected AbstractFilter<SVcsRoot> getFilter(final Locator locator) {

    Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    if (countFromFilter == null) {
      countFromFilter = (long)Constants.getDefaultPageItemsCount();
    }
    final Long lookupLimit = locator.getSingleDimensionValueAsLong(DIMENSION_LOOKUP_LIMIT);
    final MultiCheckerFilter<SVcsRoot> result =
      new MultiCheckerFilter<SVcsRoot>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter.intValue(), lookupLimit);

    result.add(new FilterConditionChecker<SVcsRoot>() {
      public boolean isIncluded(@NotNull final SVcsRoot item) {
        try {
          checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, item);
          return true;
        } catch (AuthorizationFailedException e) {
          return false;
        }
      }
    });

    final String type = locator.getSingleDimensionValue(TYPE);
    if (type != null) {
      result.add(new FilterConditionChecker<SVcsRoot>() {
        public boolean isIncluded(@NotNull final SVcsRoot item) {
          return type.equals(item.getVcsName());
        }
      });
    }

    final String projectLocator = locator.getSingleDimensionValue(PROJECT);
    if (projectLocator != null) {
      final SProject project = myProjectFinder.getItem(projectLocator);
      result.add(new FilterConditionChecker<SVcsRoot>() {
        public boolean isIncluded(@NotNull final SVcsRoot item) {
          return project.equals(VcsRoot.getProjectByRoot(item));
        }
      });
    }

    final String repositoryIdString = locator.getSingleDimensionValue(REPOSITORY_ID_STRING);
    if (repositoryIdString != null) {
      result.add(new FilterConditionChecker<SVcsRoot>() {
        public boolean isIncluded(@NotNull final SVcsRoot item) {
          return repositoryIdStringMatches(item, repositoryIdString, myVcsManager);
        }
      });
    }

    final List<String> properties = locator.getDimensionValue(PROPERTY);
    if (!properties.isEmpty()) {
      final Matcher<ParametersProvider> parameterCondition = ParameterCondition.create(properties);
      result.add(new FilterConditionChecker<SVcsRoot>() {
        public boolean isIncluded(@NotNull final SVcsRoot item) {
          return parameterCondition.matches(new AbstractMapParametersProvider(item.getProperties()));
        }
      });
    }

    return result;
  }

  @NotNull
  @Override
  protected ItemHolder<SVcsRoot> getPrefilteredItems(@NotNull Locator locator) {
    final String affectedProjectLocator = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectLocator != null) {
      final SProject affectedProject = myProjectFinder.getItem(affectedProjectLocator);
      myPermissionChecker.checkProjectPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, affectedProject.getProjectId());
      return getItemHolder(affectedProject.getVcsRoots());
    }

    final String projectLocator = locator.getSingleDimensionValue(PROJECT);
    if (projectLocator != null) {
      final SProject project = myProjectFinder.getItem(projectLocator);
      myPermissionChecker.checkProjectPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, project.getProjectId());
      return getItemHolder(project.getOwnVcsRoots()); //consistent with Project.java:183
    }

    return super.getPrefilteredItems(locator);
  }

  static boolean repositoryIdStringMatches(@NotNull final jetbrains.buildServer.vcs.VcsRoot root,
                                           @NotNull final String repositoryIdString,
                                           final VcsManager vcsManager) {
    try {
      final VcsSupportCore vcsSupport = vcsManager.findVcsByName(root.getVcsName());
      if (vcsSupport != null) {
        final PersonalSupportService personalSupportService =
          vcsManager.getVcsService(new VcsRootEntry(root, CheckoutRules.DEFAULT), PersonalSupportService.class);

        if (personalSupportService != null) {
          if (null != personalSupportService.mapPath(repositoryIdString, true).getMappedPath())
            return true;
        } else {
          LOG.debug("No personal support for VCS root " + LogUtil.describe(root) + " found, ignoring root in search");
          return false;
        }
      } else {
        LOG.debug("No VCS support for VCS root " + LogUtil.describe(root) + " found, ignoring root in search");
        return false;
      }
    } catch (Exception e) {
      LOG.debug("Error while retrieving mapping for VCS root " + LogUtil.describe(root) + " via mapFullPath, ignoring", e);
    }

    try {
      Collection<VcsMappingElement> vcsMappingElements = VcsRoot.getRepositoryMappings(root, vcsManager);
      for (VcsMappingElement vcsMappingElement : vcsMappingElements) {
        if (repositoryIdString.equals(vcsMappingElement.getTo())) {
          return true;
        }
      }
    } catch (Exception e) {
      LOG.debug("Error while retrieving mapping for VCS root " + LogUtil.describe(root) + ", ignoring root in search", e);
    }
    return false;
  }

  @NotNull
  private SVcsRoot getVcsRootByExternalOrInternalId(final String id) {
    assert id != null;
    SVcsRoot vcsRoot = myProjectManager.findVcsRootByExternalId(id);
    if (vcsRoot != null){
      checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, vcsRoot);
      return vcsRoot;
    }
    try {
      vcsRoot = myProjectManager.findVcsRootById(Long.parseLong(id));
      if (vcsRoot != null){
        checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, vcsRoot);
        return vcsRoot;
      }
    } catch (NumberFormatException e) {
      //ignore
    }
    throw new NotFoundException("No VCS root found by internal or external id '" + id + "'.");
  }

  public void checkPermission(@NotNull final Permission permission, @NotNull final SVcsRoot root) {
    //todo: check and use AuthUtil.hasReadAccessTo
    //see also jetbrains.buildServer.server.rest.model.change.VcsRoot.shouldRestrictSettingsViewing
    final SProject project = VcsRoot.getProjectByRoot(root);
    if (project == null){
      myPermissionChecker.checkGlobalPermission(permission);
    } else {
      myPermissionChecker.checkProjectPermission(permission, project.getProjectId(), " where VCS root with internal id '" + root.getId() + "' is defined");
    }
  }
}
