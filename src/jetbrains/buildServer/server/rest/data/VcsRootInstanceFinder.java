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
import java.util.*;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.parameters.impl.AbstractMapParametersProvider;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.identifiers.VcsRootIdentifiersManager;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsManager;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.VcsRootInstanceEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.13
 */
public class VcsRootInstanceFinder extends AbstractFinder<VcsRootInstance> {
  private static final Logger LOG = Logger.getInstance(VcsRootInstanceFinder.class.getName());
  public static final String VCS_ROOT_DIMENSION = "vcsRoot";
  public static final String REPOSITORY_ID_STRING = "repositoryIdString";
  protected static final String TYPE = "type";
  protected static final String PROJECT = "project";
  protected static final String AFFECTED_PROJECT = "affectedProject";
  protected static final String PROPERTY = "property";
  protected static final String BUILD_TYPE = "buildType";

  @NotNull private final VcsRootFinder myVcsRootFinder;
  @NotNull private final VcsManager myVcsManager;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final BuildTypeFinder myBuildTypeFinder;
  @NotNull private final ProjectManager myProjectManager;
  @NotNull private final PermissionChecker myPermissionChecker;

  public VcsRootInstanceFinder(@NotNull VcsRootFinder vcsRootFinder,
                               @NotNull VcsManager vcsManager,
                               @NotNull ProjectFinder projectFinder,
                               @NotNull BuildTypeFinder buildTypeFinder,
                               @NotNull ProjectManager projectManager,
                               @NotNull VcsRootIdentifiersManager vcsRootIdentifiersManager,
                               final @NotNull PermissionChecker permissionChecker) {
    super(new String[]{DIMENSION_ID, TYPE, PROJECT, AFFECTED_PROJECT, PROPERTY, REPOSITORY_ID_STRING,
      BUILD_TYPE, VCS_ROOT_DIMENSION,
      Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME});
    myVcsRootFinder = vcsRootFinder;
    myVcsManager = vcsManager;
    myProjectFinder = projectFinder;
    myBuildTypeFinder = buildTypeFinder;
    myProjectManager = projectManager;
    myPermissionChecker = permissionChecker;
  }

  @Override
  public Long getDefaultPageItemsCount() {
    return (long)Constants.getDefaultPageItemsCount();
  }

  @NotNull
  public static String getLocator(@NotNull final VcsRootInstance vcsRootInstance) {
    return Locator.getStringLocator(DIMENSION_ID, String.valueOf(vcsRootInstance.getId()));
  }

  @NotNull
  public static String getLocatorByVcsRoot(@NotNull final SVcsRoot vcsRoot) {
    return Locator.getStringLocator(VCS_ROOT_DIMENSION, VcsRootFinder.getLocator(vcsRoot));
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
  protected VcsRootInstance findSingleItem(@NotNull final Locator locator) {
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's root instance id
      return getVcsRootInstanceById(locator.getSingleValueAsLong());
    }

    final Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      return getVcsRootInstanceById(id);
    }

    return null;
  }

  @NotNull
  private VcsRootInstance getVcsRootInstanceById(final @Nullable Long parsedId) {
    if (parsedId == null) {
      throw new BadRequestException("Expecting VCS root instance id, found empty value.");
    }
    VcsRootInstance root = myVcsManager.findRootInstanceById(parsedId);
    if (root == null) {
      throw new NotFoundException("No VCS root instance can be found by id '" + parsedId + "'.");
    }
    checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, root);
    return root;
  }

  @NotNull
  @Override
  protected ItemFilter<VcsRootInstance> getFilter(@NotNull final Locator locator) {

    final MultiCheckerFilter<VcsRootInstance> result = new MultiCheckerFilter<VcsRootInstance>();

    result.add(new FilterConditionChecker<VcsRootInstance>() {
      public boolean isIncluded(@NotNull final VcsRootInstance item) {
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
      result.add(new FilterConditionChecker<VcsRootInstance>() {
        public boolean isIncluded(@NotNull final VcsRootInstance item) {
          return type.equals(item.getVcsName());
        }
      });
    }

    //todo: rework to be "there are usages directly in the project", also add to getPrefilteredItems
    //todo: support usage as versioned settings root
    final String projectLocator = locator.getSingleDimensionValue(PROJECT); //todo: support multiple here for "from all not archived projects" case
    if (projectLocator != null) {
      final SProject project = myProjectFinder.getItem(projectLocator);
      result.add(new FilterConditionChecker<VcsRootInstance>() {
        public boolean isIncluded(@NotNull final VcsRootInstance item) {
          return project.equals(VcsRoot.getProjectByRoot(item.getParent()));
        }
      });
    }

    final String repositoryIdString = locator.getSingleDimensionValue(REPOSITORY_ID_STRING);
    if (repositoryIdString != null) {
      result.add(new FilterConditionChecker<VcsRootInstance>() {
        public boolean isIncluded(@NotNull final VcsRootInstance item) {
          return VcsRootFinder.repositoryIdStringMatches(item, repositoryIdString, myVcsManager);
        }
      });
    }

    final List<String> properties = locator.getDimensionValue(PROPERTY);
    if (!properties.isEmpty()) {
      final Matcher<ParametersProvider> parameterCondition = ParameterCondition.create(properties);
      result.add(new FilterConditionChecker<VcsRootInstance>() {
        public boolean isIncluded(@NotNull final VcsRootInstance item) {
          return parameterCondition.matches(new AbstractMapParametersProvider(item.getProperties()));
        }
      });
    }

    final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
    if (buildTypeLocator != null) {
      final List<VcsRootInstanceEntry> vcsRootInstanceEntries = getBuildTypeOrTemplate(buildTypeLocator).getVcsRootInstanceEntries();
      result.add(new FilterConditionChecker<VcsRootInstance>() {
        public boolean isIncluded(@NotNull final VcsRootInstance item) {
          return CollectionsUtil.contains(vcsRootInstanceEntries, new Filter<VcsRootInstanceEntry>() {
            public boolean accept(@NotNull final VcsRootInstanceEntry data) {
              return item.equals(data.getVcsRoot());
            }
          });
        }
      });
    }

    final String vcsRootLocator = locator.getSingleDimensionValue(VCS_ROOT_DIMENSION);
    if (vcsRootLocator != null) {
      final List<SVcsRoot> vcsRoots = myVcsRootFinder.getItems(vcsRootLocator).myEntries;
      result.add(new FilterConditionChecker<VcsRootInstance>() {
        public boolean isIncluded(@NotNull final VcsRootInstance item) {
          return vcsRoots.contains(item.getParent());
        }
      });
    }

    final String affectedProjectLocator = locator.getSingleDimensionValue(AFFECTED_PROJECT); //todo: support multiple here
    if (affectedProjectLocator != null) {
      final List<VcsRootInstance> vcsRootInstances = myProjectFinder.getItem(affectedProjectLocator).getVcsRootInstances();
      result.add(new FilterConditionChecker<VcsRootInstance>() {
        public boolean isIncluded(@NotNull final VcsRootInstance item) {
          return vcsRootInstances.contains(item);
        }
      });
    }


    return result;
  }

  @NotNull
  private BuildTypeOrTemplate getBuildTypeOrTemplate(final String buildTypeLocator) {
    return myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
  }

  @NotNull
  @Override
  protected ItemHolder<VcsRootInstance> getPrefilteredItems(@NotNull Locator locator) {
    final String vcsRootLocator = locator.getSingleDimensionValue(VCS_ROOT_DIMENSION);
    if (vcsRootLocator != null) {
      final List<SVcsRoot> vcsRoots = myVcsRootFinder.getItems(vcsRootLocator).myEntries;
      final Set<VcsRootInstance> result = new LinkedHashSet<VcsRootInstance>();
      for (SVcsRoot vcsRoot : vcsRoots) {
        for (SBuildType buildType : vcsRoot.getUsages().keySet()) {
          final VcsRootInstance rootInstance = buildType.getVcsRootInstanceForParent(vcsRoot);
          if (rootInstance != null) {
            try {
              checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, rootInstance); //this can actually be dropped as it is checked in filter
              result.add(rootInstance); //todo: need to sort?
            } catch (Exception e) {
              //ignore
            }
          }
        }
      }
      return getItemHolder(result);
    }

    final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
    if (buildTypeLocator != null) {
      final List<VcsRootInstanceEntry> vcsRootInstanceEntries = getBuildTypeOrTemplate(buildTypeLocator).getVcsRootInstanceEntries();
      return getItemHolder(CollectionsUtil.convertCollection(vcsRootInstanceEntries, new Converter<VcsRootInstance, VcsRootInstanceEntry>() {
        public VcsRootInstance createFrom(@NotNull final VcsRootInstanceEntry source) {
          return source.getVcsRoot();
        }
      }));
    }

    final String projectLocator = locator.getSingleDimensionValue(AFFECTED_PROJECT); //todo: support multiple here for "from all not archived projects" case
    if (projectLocator != null) {
      final SProject project = myProjectFinder.getItem(projectLocator);
      return getItemHolder(project.getVcsRootInstances());
    }

    //todo: (TeamCity) open API is there a better way to do this?
    //if reworked, can use checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, item);
    final Set<VcsRootInstance> rootInstancesSet = new LinkedHashSet<VcsRootInstance>();
    for (SBuildType buildType : myProjectManager.getAllBuildTypes()) {
      if (myPermissionChecker.isPermissionGranted(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, buildType.getProjectId())) {
        rootInstancesSet.addAll(buildType.getVcsRootInstances());
      }
    }
    final List<VcsRootInstance> result = new ArrayList<VcsRootInstance>(rootInstancesSet.size());
    result.addAll(rootInstancesSet);
    Collections.sort(result, new Comparator<VcsRootInstance>() {
      public int compare(final VcsRootInstance o1, final VcsRootInstance o2) {
        return (int)(o1.getId() - o2.getId());
      }
    });
    return getItemHolder(result);
  }

  public void checkPermission(@NotNull final Permission permission, @NotNull final VcsRootInstance rootInstance) {
    //todo: check and use AuthUtil.hasReadAccessTo(jetbrains.buildServer.serverSide.auth.AuthorityHolder, jetbrains.buildServer.vcs.VcsRootInstance)
    myVcsRootFinder.checkPermission(permission, rootInstance.getParent()); //todo: make this more precise, currently too demanding
  }
}
