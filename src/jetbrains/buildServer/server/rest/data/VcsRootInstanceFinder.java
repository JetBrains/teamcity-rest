/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.ServiceLocator;
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
import jetbrains.buildServer.serverSide.versionedSettings.VersionedSettingsManager;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.vcs.*;
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
  protected static final String BUILD = "build";
  protected static final String STATUS = "status";
  protected static final String FINISH_VCS_CHECKING_FOR_CHANGES = "checkingForChangesFinishDate";  // experimental
  protected static final String REPOSITORY_STATE = "repositoryState";  // experimental
  protected static final String HAS_VERSIONED_SETTINGS_ONLY = "versionedSettings"; //actually means "withoutBuildTypeUsagesWithinScope"
  protected static final String COMMIT_HOOK_MODE = "commitHookMode"; // experimental
  protected static final Comparator<VcsRootInstance> VCS_ROOT_INSTANCE_COMPARATOR = new Comparator<VcsRootInstance>() {
    public int compare(final VcsRootInstance o1, final VcsRootInstance o2) {
      return (int)(o1.getId() - o2.getId());
    }
  };

  @NotNull private final VcsRootFinder myVcsRootFinder;
  @NotNull private final VcsManager myVcsManager;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final BuildTypeFinder myBuildTypeFinder;
  @NotNull private final ProjectManager myProjectManager;
  @NotNull private final PermissionChecker myPermissionChecker;
  @NotNull private final VersionedSettingsManager myVersionedSettingsManager;
  @NotNull private final TimeCondition myTimeCondition;
  @NotNull private final ServiceLocator myServiceLocator;

  public VcsRootInstanceFinder(@NotNull VcsRootFinder vcsRootFinder,
                               @NotNull VcsManager vcsManager,
                               @NotNull ProjectFinder projectFinder,
                               @NotNull BuildTypeFinder buildTypeFinder,
                               @NotNull ProjectManager projectManager,
                               @NotNull VersionedSettingsManager versionedSettingsManager,
                               @NotNull TimeCondition timeCondition,
                               final @NotNull PermissionChecker permissionChecker,
                               @NotNull final ServiceLocator serviceLocator) {
    super(DIMENSION_ID, TYPE, PROJECT, AFFECTED_PROJECT, PROPERTY, REPOSITORY_ID_STRING,
          BUILD_TYPE, BUILD, VCS_ROOT_DIMENSION, HAS_VERSIONED_SETTINGS_ONLY,
          Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    myServiceLocator = serviceLocator;
    myVersionedSettingsManager = versionedSettingsManager;
    myTimeCondition = timeCondition;
    setHiddenDimensions(STATUS, FINISH_VCS_CHECKING_FOR_CHANGES, REPOSITORY_STATE, COMMIT_HOOK_MODE);
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
  @Override
  public String getItemLocator(@NotNull final VcsRootInstance vcsRootInstance) {
    return VcsRootInstanceFinder.getLocator(vcsRootInstance);
  }

  @NotNull
  public static String getLocator(@NotNull final VcsRootInstance vcsRootInstance) {
    return Locator.getStringLocator(DIMENSION_ID, String.valueOf(vcsRootInstance.getId()));
  }

  @NotNull
  public static String getLocatorByVcsRoot(@NotNull final SVcsRoot vcsRoot) {
    return Locator.getStringLocator(VCS_ROOT_DIMENSION, VcsRootFinder.getLocator(vcsRoot));
  }

  @Nullable
  @Override
  public VcsRootInstance findSingleItem(@NotNull final Locator locator) {
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
  public ItemFilter<VcsRootInstance> getFilter(@NotNull final Locator locator) {

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

    if (locator.isUnused(BUILD)) {
      final String build = locator.getSingleDimensionValue(BUILD);
      if (build != null) {
        Set<Long> vcsRootInstanceIds = getVcsRootInstancesByBuilds(build).map(vcsRE -> vcsRE.getId()).collect(Collectors.toSet());
        result.add(item -> vcsRootInstanceIds.contains(item.getId()));
      }
    }

    //todo: rework to be "there are usages directly in the project", also add to getPrefilteredItems
    //todo: support usage as versioned settings root
    final String projectLocator = locator.getSingleDimensionValue(PROJECT); //todo: support multiple here for "from all not archived projects" case
    if (projectLocator != null) {
      final SProject project = myProjectFinder.getItem(projectLocator);
      VcsRootInstance settingsInstance = myVersionedSettingsManager.getVersionedSettingsVcsRootInstance(project);
      final Boolean nonVersionedSettings = locator.lookupSingleDimensionValueAsBoolean(HAS_VERSIONED_SETTINGS_ONLY);
      result.add(new FilterConditionChecker<VcsRootInstance>() {
        public boolean isIncluded(@NotNull final VcsRootInstance item) {
          return project.equals(VcsRoot.getProjectByRoot(item.getParent())) || //todo: rework project dimensions for the instance to mean smth. more meaningful
                 (nonVersionedSettings == null || nonVersionedSettings) && item.equals(settingsInstance);
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

    final String status = locator.getSingleDimensionValue(STATUS);
    if (status != null) {
      TypedFinderBuilder<VcsRootInstanceEx> builder = new TypedFinderBuilder<VcsRootInstanceEx>();
      builder.dimensionEnum(TypedFinderBuilder.Dimension.single(), VcsRootStatus.Type.class).description("status of the VCS root instance").
        valueForDefaultFilter(root -> root.getStatus().getType());

      final TypedFinderBuilder<VcsRootCheckStatus> statusFilterBuilder = new TypedFinderBuilder<VcsRootCheckStatus>();
      statusFilterBuilder.dimensionEnum(new TypedFinderBuilder.Dimension<>("status"), VcsRootStatus.Type.class).description("type of operation")
                         .valueForDefaultFilter(vcsRootCheckStatus -> vcsRootCheckStatus.myStatus.getType());
      statusFilterBuilder.dimensionTimeCondition(new TypedFinderBuilder.Dimension<>("timestamp"), myTimeCondition).description("time of the operation start")
                         .valueForDefaultFilter(vcsRootCheckStatus -> vcsRootCheckStatus.myStatus.getTimestamp());
      statusFilterBuilder.dimensionEnum(new TypedFinderBuilder.Dimension<>("requestorType"), OperationRequestor.class).description("requestor of the operation")
                         .valueForDefaultFilter(vcsRootCheckStatus -> vcsRootCheckStatus.myRequestor);
      Finder<VcsRootCheckStatus> vcsRootCheckStatusFinder = statusFilterBuilder.build();

      builder.dimensionFinderFilter(new TypedFinderBuilder.Dimension<>("current"), vcsRootCheckStatusFinder, "VCS check status condition")
             .description("current VCS root status").valueForDefaultFilter(root -> new VcsRootCheckStatus(root.getStatus(), root.getLastRequestor()));
      builder.dimensionFinderFilter(new TypedFinderBuilder.Dimension<>("previous"), vcsRootCheckStatusFinder, "VCS check status condition")
             .description("previous VCS root status").valueForDefaultFilter(root -> new VcsRootCheckStatus(root.getPreviousStatus(), null));

      final ItemFilter<VcsRootInstanceEx> filter = builder.build().getFilter(status);
      result.add(new FilterConditionChecker<VcsRootInstance>() {
        public boolean isIncluded(@NotNull final VcsRootInstance item) {
          return filter.isIncluded((VcsRootInstanceEx)item);
        }
      });
    }

    final Boolean commitHookMode = locator.getSingleDimensionValueAsBoolean("commitHookMode");
    if (commitHookMode != null){
      result.add(new FilterConditionChecker<VcsRootInstance>() {
        public boolean isIncluded(@NotNull final VcsRootInstance item) {
          return FilterUtil.isIncludedByBooleanFilter(commitHookMode, !((VcsRootInstanceEx)item).isPollingMode());
        }
      });
    }

    TimeCondition.FilterAndLimitingDate<VcsRootInstance> finishFiltering =
      myTimeCondition.processTimeConditions(FINISH_VCS_CHECKING_FOR_CHANGES, locator, (vcsRootInstance) -> getFinishCheckingForChanges(vcsRootInstance), null);
    if (finishFiltering != null) result.add(finishFiltering.getFilter());

    final String repositoryState = locator.getSingleDimensionValue(REPOSITORY_STATE);
    if (repositoryState != null) {
      TypedFinderBuilder<RepositoryState> builder = new TypedFinderBuilder<RepositoryState>();
      builder.dimensionTimeCondition(new TypedFinderBuilder.Dimension<>("timestamp"), myTimeCondition).description("time of the repository state creation").
        valueForDefaultFilter(item -> item.getCreateTimestamp());

      builder.dimensionValueCondition(new TypedFinderBuilder.Dimension<>("branchName")).description("branch name").filter((valueCondition, item) -> {
        for (String branchName : item.getBranchRevisions().keySet()) {
          if (valueCondition.matches(branchName)) return true;
        }
        return false;
      });

      final ItemFilter<RepositoryState> filter = builder.build().getFilter(repositoryState);
      result.add(new FilterConditionChecker<VcsRootInstance>() {
        public boolean isIncluded(@NotNull final VcsRootInstance item) {
          return filter.isIncluded(((VcsRootInstanceEx)item).getLastUsedState());
        }
      });

    }

    final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
    if (buildTypeLocator != null) {
      BuildTypeOrTemplate buildType = getBuildTypeOrTemplate(buildTypeLocator);
      Boolean versionedSettingsUsagesOnly = locator.lookupSingleDimensionValueAsBoolean(HAS_VERSIONED_SETTINGS_ONLY);
      if (versionedSettingsUsagesOnly != null && versionedSettingsUsagesOnly) {
        //special case to include versioned settings root if directly requested
        Set<VcsRootInstance> settingsRootInstances = getSettingsRootInstances(Collections.singleton(buildType.getProject()));
        result.add(new FilterConditionChecker<VcsRootInstance>() {
          public boolean isIncluded(@NotNull final VcsRootInstance item) {
            return settingsRootInstances.contains(item);
          }
        });
      } else {
        List<VcsRootInstanceEntry> vcsRootInstanceEntries = buildType.getVcsRootInstanceEntries();
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
      final Set<VcsRootInstance> vcsRootInstances = getVcsRootInstancesUnderProject(myProjectFinder.getItem(affectedProjectLocator),
                                                                                    locator.getSingleDimensionValueAsBoolean(HAS_VERSIONED_SETTINGS_ONLY));
      result.add(new FilterConditionChecker<VcsRootInstance>() {
        public boolean isIncluded(@NotNull final VcsRootInstance item) {
          return vcsRootInstances.contains(item);
        }
      });
    }

    // should check HAS_VERSIONED_SETTINGS_ONLY only in prefiltered items as it should consider the current scope - no way to filter in Filter

    return result;
  }

  @NotNull
  private Stream<VcsRootInstance> getVcsRootInstancesByBuilds(@NotNull final String buildsLocator) {
    return myServiceLocator.getSingletonService(BuildPromotionFinder.class).getItemsNotEmpty(buildsLocator).myEntries.stream().
      flatMap(buildPromotion -> buildPromotion.getVcsRootEntries().stream().map(vcsE -> vcsE.getVcsRoot())).distinct();
  }

  private static class VcsRootCheckStatus {
    @NotNull final VcsRootStatus myStatus;
    @Nullable final OperationRequestor myRequestor;

    public VcsRootCheckStatus(@NotNull final VcsRootStatus status, @Nullable final OperationRequestor requestor) {
      myStatus = status;
      myRequestor = requestor;
    }
  }
  private static Date getFinishCheckingForChanges(@NotNull final VcsRootInstance vcsRootInstance) {
    return ((VcsRootInstanceEx)vcsRootInstance).getLastFinishChangesCollectingTime();
  }

  @NotNull
  private BuildTypeOrTemplate getBuildTypeOrTemplate(final String buildTypeLocator) {
    return myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
  }

  @NotNull
  @Override
  public ItemHolder<VcsRootInstance> getPrefilteredItems(@NotNull Locator locator) {
    Boolean versionedSettingsUsagesOnly = locator.getSingleDimensionValueAsBoolean(HAS_VERSIONED_SETTINGS_ONLY);  // should check it not in Filter as it considers current scope

    final String build = locator.getSingleDimensionValue(BUILD);
    if (build != null) {
      return FinderDataBinding.getItemHolder(getVcsRootInstancesByBuilds(build));
    }

    final String vcsRootLocator = locator.getSingleDimensionValue(VCS_ROOT_DIMENSION);
    if (vcsRootLocator != null) {
      final List<SVcsRoot> vcsRoots = myVcsRootFinder.getItems(vcsRootLocator).myEntries;
      final Set<VcsRootInstance> result = new LinkedHashSet<VcsRootInstance>();
      for (SVcsRoot vcsRoot : vcsRoots) {
        result.addAll(getInstances(vcsRoot, versionedSettingsUsagesOnly));
      }
      return getItemHolder(result);
    }

    final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
    if (buildTypeLocator != null) {
      final BuildTypeOrTemplate buildType = getBuildTypeOrTemplate(buildTypeLocator);
      if (versionedSettingsUsagesOnly != null && versionedSettingsUsagesOnly){
        //special case to include versioned settings root if directly requested
        return getItemHolder(getSettingsRootInstances(Collections.singleton(buildType.getProject())));
      }
      final List<VcsRootInstanceEntry> vcsRootInstanceEntries = buildType.getVcsRootInstanceEntries();
      return getItemHolder(CollectionsUtil.convertCollection(vcsRootInstanceEntries, new Converter<VcsRootInstance, VcsRootInstanceEntry>() {
        public VcsRootInstance createFrom(@NotNull final VcsRootInstanceEntry source) {
          return source.getVcsRoot();
        }
      }));
    }

    final String projectLocator = locator.getSingleDimensionValue(AFFECTED_PROJECT); //todo: support multiple here for "from all not archived projects" case
    if (projectLocator != null) {
      return getItemHolder(getVcsRootInstancesUnderProject(myProjectFinder.getItem(projectLocator), versionedSettingsUsagesOnly));
    }

    //todo: (TeamCity) open API is there a better way to do this?
    //if reworked, can use checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, item);
    // when implemented, can also add to jetbrains.buildServer.usageStatistics.impl.providers.StaticServerUsageStatisticsProvider.publishNumberOfVcsRoots()
    final Set<VcsRootInstance> result = new TreeSet<>(VCS_ROOT_INSTANCE_COMPARATOR);

    if (versionedSettingsUsagesOnly == null || !versionedSettingsUsagesOnly) {
      for (SBuildType buildType : myProjectManager.getAllBuildTypes()) {
        if (myPermissionChecker.isPermissionGranted(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, buildType.getProjectId())) {
          result.addAll(buildType.getVcsRootInstances());
        }
      }
    }
    if (versionedSettingsUsagesOnly == null || versionedSettingsUsagesOnly) {
      result.addAll(getSettingsRootInstances(myProjectManager.getProjects()));
    }
    return getItemHolder(result);
  }

  @NotNull
  private TreeSet<VcsRootInstance> getVcsRootInstancesUnderProject(@NotNull final SProject project, @Nullable final Boolean versionedSettingsUsagesOnly) {
    TreeSet<VcsRootInstance> result = new TreeSet<>(VCS_ROOT_INSTANCE_COMPARATOR);
    if (versionedSettingsUsagesOnly == null || !versionedSettingsUsagesOnly){
      result.addAll((project.getVcsRootInstances()));  //todo: includes versioned settings???
    }
    if (versionedSettingsUsagesOnly == null || versionedSettingsUsagesOnly){
      result.addAll(getSettingsRootInstances(Collections.singleton(project)));
      result.addAll(getSettingsRootInstances(project.getProjects()));
    }
    return result;
  }

  @NotNull
  private Set<VcsRootInstance> getInstances(@NotNull final SVcsRoot vcsRoot, @Nullable final Boolean versionedSettingsUsagesOnly) {
    TreeSet<VcsRootInstance> result = new TreeSet<>(VCS_ROOT_INSTANCE_COMPARATOR);
    if (versionedSettingsUsagesOnly == null || !versionedSettingsUsagesOnly) {
      for (SBuildType buildType : vcsRoot.getUsagesInConfigurations()) {
        final VcsRootInstance rootInstance = buildType.getVcsRootInstanceForParent(vcsRoot);
        if (rootInstance != null) {
          try {
            checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, rootInstance); //minor performance optimization not to return roots which will be filtered in the filter
            result.add(rootInstance);
          } catch (Exception e) {
            //ignore
          }
        }
      }
    }
    if (versionedSettingsUsagesOnly == null || versionedSettingsUsagesOnly) {
      result.addAll(getSettingsRootInstances(myVersionedSettingsManager.getProjectsBySettingsRoot(vcsRoot)));
    }
    return result;
  }

  //todo: use getAllProjectUsages here?
  private Set<VcsRootInstance> getSettingsRootInstances(@NotNull final Collection<SProject> projectsInRoot) {
    HashSet<VcsRootInstance> result = new HashSet<>();
    for (SProject project : projectsInRoot) {
      VcsRootInstance instance = myVersionedSettingsManager.getVersionedSettingsVcsRootInstance(project);
      if (instance != null) {
        try {
          checkPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, instance); //minor performance optimization not to return roots which will be filtered in the filter
          result.add(instance);
        } catch (Exception e) {
          //ignore
        }
      }
    }
    return result;
  }

  public void checkPermission(@NotNull final Permission permission, @NotNull final VcsRootInstance rootInstance) {
    //todo: check and use AuthUtil.hasReadAccessTo(jetbrains.buildServer.serverSide.auth.AuthorityHolder, jetbrains.buildServer.vcs.VcsRootInstance)
    myVcsRootFinder.checkPermission(permission, rootInstance.getParent()); //todo: make this more precise, currently too demanding
  }
}
