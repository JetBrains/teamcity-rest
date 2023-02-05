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

package jetbrains.buildServer.server.rest.data.finder.impl;

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.parameters.impl.AbstractMapParametersProvider;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.finder.AbstractFinder;
import jetbrains.buildServer.server.rest.data.finder.Finder;
import jetbrains.buildServer.server.rest.data.util.ItemFilter;
import jetbrains.buildServer.server.rest.data.util.Matcher;
import jetbrains.buildServer.server.rest.data.util.MultiCheckerFilter;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.CommonLocatorDimensionsList;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorDimensionDataType;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.identifiers.EntityId;
import jetbrains.buildServer.serverSide.identifiers.VcsRootIdentifiersManager;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsManager;
import jetbrains.buildServer.vcs.VcsSupportCore;
import jetbrains.vcs.api.VcsSettings;
import jetbrains.vcs.api.services.tc.PersonalSupportBatchService;
import jetbrains.vcs.api.services.tc.VcsMappingElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.13
 */
@LocatorResource(value = LocatorName.VCS_ROOT,
    extraDimensions = {AbstractFinder.DIMENSION_ID, AbstractFinder.DIMENSION_LOOKUP_LIMIT, PagerData.START, PagerData.COUNT, CommonLocatorDimensionsList.PROPERTY, AbstractFinder.DIMENSION_ITEM},
    baseEntity = "VcsRoot",
    examples = {
        "`type:jetbrains.git` — find all `Git`-typed VCS roots.",
        "`project:<projectLocator>` — find all VCS roots defined under project found by `projectLocator`."
    }
)
@JerseyContextSingleton
@Component("restVcsRootFinder")
public class VcsRootFinder extends AbstractFinder<SVcsRoot> {
  private static final Logger LOG = Logger.getInstance(VcsRootFinder.class.getName());
  public static final String REPOSITORY_ID_STRING = "repositoryIdString";
  @LocatorDimension(value = "internalId", dataType = LocatorDimensionDataType.INTEGER)
  protected static final String INTERNAL_ID = "internalId";
  @LocatorDimension("uuid")
  protected static final String UUID = "uuid";
  @LocatorDimension("name")
  protected static final String NAME = "name";
  @LocatorDimension(value = "type", notes = "Type of VCS (e.g. jetbrains.git).")
  protected static final String TYPE = "type";
  @LocatorDimension(value = "project", format = LocatorName.PROJECT, notes = "Project (direct parent) locator.")
  protected static final String PROJECT = "project";
  @LocatorDimension(value = "affectedProject", format = LocatorName.PROJECT, notes = "Project (direct or indirect parent) locator.")
  protected static final String AFFECTED_PROJECT = "affectedProject";
  protected static final String PROPERTY = "property";

  @NotNull private final VcsManager myVcsManager;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final Finder<BuildTypeOrTemplate> myBuildTypeFinder; //todo: add filtering by (multiple) build types???
  @NotNull private final ProjectManager myProjectManager;
  @NotNull private final VcsRootIdentifiersManager myVcsRootIdentifiersManager;
  @NotNull private final PermissionChecker myPermissionChecker;

  public VcsRootFinder(@NotNull VcsManager vcsManager,
                       @NotNull ProjectFinder projectFinder,
                       @NotNull BuildTypeFinder buildTypeFinder,
                       @NotNull ProjectManager projectManager,
                       @NotNull VcsRootIdentifiersManager vcsRootIdentifiersManager,
                       final @NotNull PermissionChecker permissionChecker) {
    super(DIMENSION_ID, NAME, TYPE, PROJECT, AFFECTED_PROJECT, PROPERTY, REPOSITORY_ID_STRING, INTERNAL_ID, UUID,
          Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    myVcsManager = vcsManager;
    myProjectFinder = projectFinder;
    myBuildTypeFinder = buildTypeFinder;
    myProjectManager = projectManager;
    myVcsRootIdentifiersManager = vcsRootIdentifiersManager;
    myPermissionChecker = permissionChecker;
  }

  @Override
  public Long getDefaultPageItemsCount() {
    return (long)Constants.getDefaultPageItemsCount();
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final SVcsRoot sVcsRoot) {
    return VcsRootFinder.getLocator(sVcsRoot);
  }

  @NotNull
  public static String getLocator(@NotNull final SVcsRoot vcsRoot) {
    return Locator.getStringLocator(DIMENSION_ID, String.valueOf(vcsRoot.getExternalId()));
  }

  @NotNull
  public static String getLocator(@NotNull final BuildProject project) {
    return Locator.getStringLocator(PROJECT, String.valueOf(ProjectFinder.getLocator(project)));
  }

  @Nullable
  @Override
  public SVcsRoot findSingleItem(@NotNull final Locator locator) {
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
        //todo: this and other invocations except for in getFilter can be removed as the filter is applied to all items
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

    return null;
  }

  @NotNull
  @Override
  public ItemFilter<SVcsRoot> getFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<SVcsRoot> result = new MultiCheckerFilter<SVcsRoot>();

    result.add(vcsRoot -> {
      try {
        checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, vcsRoot);
        return true;
      } catch (AuthorizationFailedException e) {
        return false;
      }
    });

    final String type = locator.getSingleDimensionValue(TYPE);
    if (type != null) {
      result.add(vcsRoot -> type.equals(vcsRoot.getVcsName()));
    }

    if (locator.isUnused(PROJECT)) {
      final String projectLocator = locator.getSingleDimensionValue(PROJECT);
      if (projectLocator != null) {
        Set<SProject> projects = new HashSet<>(myProjectFinder.getItemsNotEmpty(projectLocator).getEntries());
        result.add(item -> projects.contains(VcsRoot.getProjectByRoot(item)));
      }
    }

    final String repositoryIdString = locator.getSingleDimensionValue(REPOSITORY_ID_STRING);
    if (repositoryIdString != null) {
      result.add(vcsRoot -> repositoryIdStringMatches(vcsRoot, repositoryIdString, myVcsManager));
    }

    final List<String> properties = locator.getDimensionValue(PROPERTY);
    if (!properties.isEmpty()) {
      final Matcher<ParametersProvider> parameterCondition = ParameterCondition.create(properties);
      result.add(vcsRoot -> parameterCondition.matches(new AbstractMapParametersProvider(vcsRoot.getProperties())));
    }

    final String rootName = locator.getSingleDimensionValue(NAME);
    if (rootName != null) {
      result.add(vcsRoot -> vcsRoot.getName().equals(rootName));
    }

    return result;
  }

  @NotNull
  @Override
  public ItemHolder<SVcsRoot> getPrefilteredItems(@NotNull Locator locator) {
    final String affectedProjectLocator = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectLocator != null) {
      List<SProject> projects = myProjectFinder.getItems(affectedProjectLocator).getEntries();
      projects.forEach(project -> myPermissionChecker.checkProjectPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, project.getProjectId()));
      return ItemHolder.of(projects.stream().flatMap(p -> p.getVcsRoots().stream()).collect(Collectors.toSet()));
    }

    final String projectLocator = locator.getSingleDimensionValue(PROJECT);
    if (projectLocator != null) {
      List<SProject> projects = myProjectFinder.getItemsNotEmpty(projectLocator).getEntries();
      projects.forEach(project -> myPermissionChecker.checkProjectPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, project.getProjectId()));
      return ItemHolder.of(projects.stream().flatMap(p -> p.getOwnVcsRoots().stream())); //consistent with Project.java:183
    }

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
    return ItemHolder.of(result);
  }

  static boolean repositoryIdStringMatches(@NotNull final jetbrains.buildServer.vcs.VcsRoot root,
                                           @NotNull final String repositoryIdString,
                                           final VcsManager vcsManager) {
    String repositoryIdStringWithoutType;
    //see also PersonalPatchUtil.mapPathEx()
    final int index = repositoryIdString.indexOf(VcsMappingElement.SEPARATOR);
    if (index >= 0) {
      final String vcsName = repositoryIdString.substring(0, index);
      if (!vcsName.equals(root.getVcsName())) return false;
      repositoryIdStringWithoutType = repositoryIdString.substring(index + VcsMappingElement.SEPARATOR.length());
    } else {
      repositoryIdStringWithoutType = repositoryIdString; //pre-TeamCity 10 compatibility
    }

    try {
      final VcsSupportCore vcsSupport = vcsManager.findVcsByName(root.getVcsName());
      if (vcsSupport != null) {
        final PersonalSupportBatchService personalSupportService = vcsManager.getGenericService(root.getVcsName(), PersonalSupportBatchService.class);

        if (personalSupportService != null) {
//          if (null != personalSupportService.mapPath(Arrays.asList(new VcsSettings(root, "")), repositoryIdStringWithoutType, true).getMappedPath())
//          return true;
          List<Boolean> canAffectList = personalSupportService.canAffect(Arrays.asList(new VcsSettings(root, "")), Collections.singletonList(repositoryIdStringWithoutType), true);
          for (Boolean aBoolean : canAffectList) {
            if (aBoolean) return true;
          }
          return false;
        } else {
          LOG.debug("No personal support for VCS root " + LogUtil.describe(root) + " found, ignoring root in search");
        }
      } else {
        LOG.debug("No VCS support for VCS root " + LogUtil.describe(root) + " found, ignoring root in search");
      }
    } catch (Exception e) {
      LOG.debug("Error while retrieving mapping for VCS root " + LogUtil.describe(root) + " via mapFullPath, ignoring", e);
    }

    try {
      Collection<VcsMappingElement> vcsMappingElements = VcsRoot.getRepositoryMappings(root, vcsManager);
      for (VcsMappingElement vcsMappingElement : vcsMappingElements) {
        if (vcsMappingElement.getTo().startsWith(repositoryIdString) || repositoryIdString.startsWith(vcsMappingElement.getTo())) {
//        if (repositoryIdString.equals(vcsMappingElement.getTo())) {
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
    if (vcsRoot != null) {
      checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, vcsRoot);
      return vcsRoot;
    }
    try {
      vcsRoot = myProjectManager.findVcsRootById(Long.parseLong(id));
      if (vcsRoot != null) {
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
    if (project == null) {
      myPermissionChecker.checkGlobalPermission(permission);
    } else {
      myPermissionChecker.checkProjectPermission(permission, project.getProjectId(), " where VCS root with internal id '" + root.getId() + "' is defined");
    }
  }
}
