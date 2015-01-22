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
import java.util.*;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.identifiers.EntityId;
import jetbrains.buildServer.serverSide.identifiers.VcsRootIdentifiersManager;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsManager;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.13
 */
public class VcsRootFinder{
  private static final Logger LOG = Logger.getInstance(VcsRootFinder.class.getName());
  public static final String VCS_ROOT_DIMENSION = "vcsRoot";
  public static final String REPOSITORY_ID_STRING = "repositoryIdString";

  @NotNull private final VcsManager myVcsManager;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final BuildTypeFinder myBuildTypeFinder;
  @NotNull private final ProjectManager myProjectManager;
  @NotNull private final VcsRootIdentifiersManager myVcsRootIdentifiersManager;
  @NotNull private final PermissionChecker myPermissionChecker;

  public VcsRootFinder(@NotNull VcsManager vcsManager,
                       @NotNull ProjectFinder projectFinder,
                       @NotNull BuildTypeFinder buildTypeFinder,
                       @NotNull ProjectManager projectManager,
                       @NotNull VcsRootIdentifiersManager vcsRootIdentifiersManager,
                       final @NotNull PermissionChecker permissionChecker) {
    myVcsManager = vcsManager;
    myProjectFinder = projectFinder;
    myBuildTypeFinder = buildTypeFinder;
    myProjectManager = projectManager;
    myVcsRootIdentifiersManager = vcsRootIdentifiersManager;
    myPermissionChecker = permissionChecker;
  }

  @NotNull
  public SVcsRoot getVcsRoot(@Nullable final String locatorText) {
    if (StringUtil.isEmpty(locatorText)) {
      throw new BadRequestException("Empty VCS root locator is not supported.");
    }
    final Locator locator = createVcsRootLocator(locatorText);
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's an internal id or external id
      return getVcsRootByExternalOrInternalId(locator.getSingleValue());
    }

    locator.setDimension(PagerData.COUNT, "1"); //get only the first one that matches
    final PagedSearchResult<SVcsRoot> vcsRoots = getVcsRoots(locator);
    if (vcsRoots.myEntries.size() == 0) {
      throw new NotFoundException("No VCS roots are found by locator '" + locatorText + "'.");
    }
    assert vcsRoots.myEntries.size()== 1;
    final SVcsRoot vcsRoot = vcsRoots.myEntries.get(0);
    checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, vcsRoot);
    return vcsRoot;
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

  @NotNull
  public static Locator createVcsRootLocator(@Nullable final String locatorText){
    final Locator result =
      new Locator(locatorText, "id", "name", "type", "project", VcsRootsFilter.REPOSITORY_ID_STRING, "internalId", PagerData.COUNT, PagerData.START, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    result.addIgnoreUnusedDimensions(PagerData.COUNT);
    return result;
  }

  @NotNull
  public static Locator createVcsRootInstanceLocator(@Nullable final String locatorText){
    final Locator result =
      new Locator(locatorText, "id", "name", "type", "project", "buildType", VcsRootFinder.VCS_ROOT_DIMENSION, VcsRootFinder.REPOSITORY_ID_STRING, PagerData.COUNT, PagerData.START, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    result.addIgnoreUnusedDimensions(PagerData.COUNT);
    return result;
  }

  @NotNull
  public static String getVcsRootLocatorText(@NotNull final SVcsRoot vcsRoot) {
    return "id:" + vcsRoot.getExternalId();
  }

  @NotNull
  public static String getVcsRootInstancesLocatorText(@NotNull final SVcsRoot vcsRoot) {
    return VCS_ROOT_DIMENSION + ":(" + getVcsRootLocatorText(vcsRoot) + ")";
  }

  @NotNull
  public static String getLocatorText(@NotNull final SProject project) {
    return Locator.createEmptyLocator().setDimension("project", ProjectFinder.getLocator(project)).getStringRepresentation();
  }

  @NotNull
  public PagedSearchResult<SVcsRoot> getVcsRoots(@Nullable final Locator locator) {
    if (locator == null) {
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
      return new PagedSearchResult<SVcsRoot>(result, null, null);
    }

    if (locator.isSingleValue()){
      return new PagedSearchResult<SVcsRoot>(Collections.singletonList(getVcsRootByExternalOrInternalId(locator.getSingleValue())), null, null);
    }

    String externalId = locator.getSingleDimensionValue("id");
    if (externalId != null) {
      SVcsRoot root;
      if (TeamCityProperties.getBoolean(APIController.REST_COMPATIBILITY_ALLOW_EXTERNAL_ID_AS_INTERNAL)) {
        root = getVcsRootByExternalOrInternalId(externalId);
      } else {
        root = myProjectManager.findVcsRootByExternalId(externalId);
        if (root == null) {
          throw new NotFoundException("No VCS root can be found by id '" + externalId + "'.");
        }
        checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, root);
        final Long count = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
        if (count != null && count != 1) {
          throw new BadRequestException("Dimension 'id' is specified and 'count' is not 1.");
        }
      }
      locator.checkLocatorFullyProcessed();
      return new PagedSearchResult<SVcsRoot>(Collections.singletonList(root), null, null);
    }

    Long internalId = locator.getSingleDimensionValueAsLong("internalId");
    if (internalId != null) {
      SVcsRoot root = myVcsManager.findRootById(internalId);
      if (root == null) {
        throw new NotFoundException("No VCS root can be found by internal id '" + internalId + "'.");
      }
      final Long count = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
      if (count != null && count != 1) {
        throw new BadRequestException("Dimension 'internalId' is specified and 'count' is not 1.");
      }
      locator.checkLocatorFullyProcessed();
      checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, root);
      return new PagedSearchResult<SVcsRoot>(Collections.singletonList(root), null, null);
    }

    String uuid = locator.getSingleDimensionValue("uuid");
    if (uuid != null) {
      final EntityId<Long> internalVCSRootId = myVcsRootIdentifiersManager.findEntityIdByConfigId(uuid);
      if (internalVCSRootId != null) {
        SVcsRoot root = myVcsManager.findRootById(internalVCSRootId.getInternalId());
        if (root != null) {
          checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, root);
          locator.checkLocatorFullyProcessed();
          return new PagedSearchResult<SVcsRoot>(Collections.singletonList(root), null, null);
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

    String rootName = locator.getSingleDimensionValue("name");
    if (rootName != null) {
      SVcsRoot root = myVcsManager.findRootByName(rootName);
      if (root == null) {
        throw new NotFoundException("No VCS root can be found by name '" + rootName + "'.");
      }
      final Long count = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
      if (count != null && count != 1) {
        throw new BadRequestException("Dimension 'name' is specified and 'count' is not 1.");
      }
      locator.checkLocatorFullyProcessed();
      checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, root);
      return new PagedSearchResult<SVcsRoot>(Collections.singletonList(root), null, null);
    }

    VcsRootsFilter filter = new VcsRootsFilter(locator, myProjectFinder, myVcsManager, this);
    locator.checkLocatorFullyProcessed();

    return new PagedSearchResult<SVcsRoot>(getVcsRoots(filter), filter.getStart(), filter.getCount());
  }

  private List<SVcsRoot> getVcsRoots(final VcsRootsFilter filter) {
    final FilterItemProcessor<SVcsRoot> filterItemProcessor = new FilterItemProcessor<SVcsRoot>(filter);
    AbstractFilter.processList(myVcsManager.getAllRegisteredVcsRoots(), filterItemProcessor);
    return filterItemProcessor.getResult();
  }

  @NotNull
  public VcsRootInstance getVcsRootInstance(@Nullable final String locatorText) {
    if (StringUtil.isEmpty(locatorText)) {
      throw new BadRequestException("Empty VCS root intance locator is not supported.");
    }
    final Locator locator = createVcsRootInstanceLocator(locatorText);

    if (locator.isSingleValue()) {
      // no dimensions found, assume it's root instance id
      final Long parsedId = locator.getSingleValueAsLong();
      if (parsedId == null) {
        throw new BadRequestException("Expecting VCS root instance id, found empty value.");
      }
      VcsRootInstance root = myVcsManager.findRootInstanceById(parsedId);
      if (root == null) {
        throw new NotFoundException("No VCS root instance can be found by id '" + parsedId + "'.");
      }
      locator.checkLocatorFullyProcessed();
      checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, root);
      return root;
    }

    locator.setDimension(PagerData.COUNT, "1"); //get only the first one that matches
    final PagedSearchResult<VcsRootInstance> vcsRoots = getVcsRootInstances(locator);
    if (vcsRoots.myEntries.size() == 0) {
      throw new NotFoundException("No VCS root instances are found by locator '" + locatorText + "'.");
    }
    assert vcsRoots.myEntries.size()== 1;
    final VcsRootInstance vcsRootInstance = vcsRoots.myEntries.get(0);
    checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, vcsRootInstance);
    return vcsRootInstance;
  }

  public PagedSearchResult<VcsRootInstance> getVcsRootInstances(@Nullable final Locator locator) {
    if (locator == null) {
       return new PagedSearchResult<VcsRootInstance>(getAllVcsRootInstances(myProjectManager), null, null);
     }

     if (locator.isSingleValue()){
       throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported.");
     }

     Long rootId = locator.getSingleDimensionValueAsLong("id");
     if (rootId != null) {
       VcsRootInstance root = myVcsManager.findRootInstanceById(rootId);
       if (root == null) {
         throw new NotFoundException("No VCS root instance can be found by id '" + rootId + "'.");
       }
       locator.checkLocatorFullyProcessed();
       checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, root);
       return new PagedSearchResult<VcsRootInstance>(Collections.singletonList(root), null, null);
     }

    AbstractFilter<VcsRootInstance> filter = getVcsRootInstancesFilter(locator);
    locator.checkLocatorFullyProcessed();

    return new PagedSearchResult<VcsRootInstance>(getVcsRootInstances(filter), filter.getStart(), filter.getCount());
  }


  private List<VcsRootInstance> getVcsRootInstances(final AbstractFilter<VcsRootInstance> filter) {
    //todo: current implementation is not effective: consider pre-filtering by vcs root, project, type, if specified
    final FilterItemProcessor<VcsRootInstance> filterItemProcessor = new FilterItemProcessor<VcsRootInstance>(filter);
    AbstractFilter.processList(getAllVcsRootInstances(myProjectManager), filterItemProcessor);
    return filterItemProcessor.getResult();
  }

  private List<VcsRootInstance> getAllVcsRootInstances(ProjectManager projectManager) {
    //todo: (TeamCity) open API is there a better way to do this?
    final Set<VcsRootInstance> rootInstancesSet = new LinkedHashSet<VcsRootInstance>();
    for (SBuildType buildType : projectManager.getAllBuildTypes()) {
      if (myPermissionChecker.isPermissionGranted(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, buildType.getProjectId()))
        rootInstancesSet.addAll(buildType.getVcsRootInstances());
    }
    final List<VcsRootInstance> result = new ArrayList<VcsRootInstance>(rootInstancesSet.size());
    result.addAll(rootInstancesSet);
    Collections.sort(result, new Comparator<VcsRootInstance>() {
      public int compare(final VcsRootInstance o1, final VcsRootInstance o2) {
        return (int)(o1.getId() - o2.getId());
      }
    });
    return result;
  }


  public MultiCheckerFilter<VcsRootInstance> getVcsRootInstancesFilter(@NotNull final Locator locator) {
    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<VcsRootInstance> result =
      new MultiCheckerFilter<VcsRootInstance>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);

    result.add(new FilterConditionChecker<VcsRootInstance>() {
      public boolean isIncluded(@NotNull final VcsRootInstance item) {
        try {
          checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, item);
        } catch (AuthorizationFailedException e) {
          return false;
        }
        return true;
      }
    });

    final String vcsType = locator.getSingleDimensionValue("type");
    if (vcsType != null) {
      result.add(new FilterConditionChecker<VcsRootInstance>() {
        public boolean isIncluded(@NotNull final VcsRootInstance item) {
          return vcsType.equals(item.getVcsName());
        }
      });
    }


    final String projectLocator = locator.getSingleDimensionValue("project"); //uses project as "defined in", but might also need "accessible from" operation
    SProject project = null;
    if (projectLocator != null) {
      project = myProjectFinder.getProject(projectLocator);
      final SProject internalProject = project;
      result.add(new FilterConditionChecker<VcsRootInstance>() {
        public boolean isIncluded(@NotNull final VcsRootInstance item) {
          final SProject projectOfTheRoot = VcsRoot.getProjectByRoot(item.getParent());
          return projectOfTheRoot != null && internalProject.equals(projectOfTheRoot);
        }
      });
    }

    final String buildTypeLocator = locator.getSingleDimensionValue("buildType"); //uses buildType as "used in", but might also need "accessible from" operation
    if (buildTypeLocator != null) {
      final SBuildType buildType = myBuildTypeFinder.getBuildType(project, buildTypeLocator);
      result.add(new FilterConditionChecker<VcsRootInstance>() {
        public boolean isIncluded(@NotNull final VcsRootInstance item) {
          return item.getUsages().keySet().contains(buildType);  // todo: how to find usages in templates?
        }
      });
    }

    final String vcsRootLocator = locator.getSingleDimensionValue(VCS_ROOT_DIMENSION);
    if (vcsRootLocator != null) {
      final SVcsRoot vcsRoot = getVcsRoot(vcsRootLocator);
      result.add(new FilterConditionChecker<VcsRootInstance>() {
        public boolean isIncluded(@NotNull final VcsRootInstance item) {
          return vcsRoot.equals(item.getParent());
        }
      });
    }

    final String repositoryIdString = locator.getSingleDimensionValue(REPOSITORY_ID_STRING);
    if (repositoryIdString != null){
      result.add(new FilterConditionChecker<VcsRootInstance>() {
        public boolean isIncluded(@NotNull final VcsRootInstance item) {
          return VcsRootsFilter.repositoryIdStringMatches(item.getParent(), repositoryIdString, myVcsManager);
        }
      });
    }
    return result;
  }

  public void checkPermission(@NotNull final Permission permission, @NotNull final VcsRootInstance rootInstance) {
    checkPermission(permission, rootInstance.getParent());
  }

  public void checkPermission(@NotNull final Permission permission, @NotNull final SVcsRoot root) {
    final SProject project = VcsRoot.getProjectByRoot(root);
    if (project == null){
      myPermissionChecker.checkGlobalPermission(permission);
    } else {
      myPermissionChecker.checkProjectPermission(permission, project.getProjectId(), " where VCS root with internal id '" + root.getId() + "' is defined");
    }
  }
}
