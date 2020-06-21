/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.parameters.impl.AbstractMapParametersProvider;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.server.rest.swagger.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.LocatorResource;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.versionedSettings.VersionedSettingsManager;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.vcs.*;
import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.13
 */
@LocatorResource(value = "VcsRootInstanceLocator", extraDimensions = {AbstractFinder.DIMENSION_ID, AbstractFinder.DIMENSION_LOOKUP_LIMIT, PagerData.START, PagerData.COUNT, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME})
public class VcsRootInstanceFinder extends AbstractFinder<VcsRootInstance> {
  private static final Logger LOG = Logger.getInstance(VcsRootInstanceFinder.class.getName());
  @LocatorDimension("vcsRoot") public static final String VCS_ROOT_DIMENSION = "vcsRoot";
  @LocatorDimension("repositoryIdString") public static final String REPOSITORY_ID_STRING = "repositoryIdString";
  @LocatorDimension("type") protected static final String TYPE = "type";
  @LocatorDimension("project") protected static final String PROJECT = "project";
  @LocatorDimension("affectedProject") protected static final String AFFECTED_PROJECT = "affectedProject";
  @LocatorDimension("property") protected static final String PROPERTY = "property";
  @LocatorDimension("buildType") protected static final String BUILD_TYPE = "buildType";
  @LocatorDimension("build") protected static final String BUILD = "build";
  protected static final String STATUS = "status";
  protected static final String FINISH_VCS_CHECKING_FOR_CHANGES = "checkingForChangesFinishDate";  // experimental
  protected static final String REPOSITORY_STATE = "repositoryState";  // experimental
  @LocatorDimension("versionedSettings") protected static final String HAS_VERSIONED_SETTINGS_ONLY = "versionedSettings"; //whether to include usages in project's versioned settings or not. By default "false" if "buildType" dimension is present and "any" otherwise
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

  private void setLocatorDefaults(@NotNull final Locator locator) {
    if (locator.isSingleValue()) {
      return;
    }

    if (locator.isAnyPresent(BUILD_TYPE)) {
      locator.setDimensionIfNotPresent(HAS_VERSIONED_SETTINGS_ONLY, "false");
    }
  }

  @NotNull
  @Override
  public ItemFilter<VcsRootInstance> getFilter(@NotNull final Locator locator) {

    final MultiCheckerFilter<VcsRootInstance> result = new MultiCheckerFilter<VcsRootInstance>();

    result.add(item -> hasPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, item));

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

    if (locator.isUnused(VCS_ROOT_DIMENSION)) {
      final String vcsRootLocator = locator.getSingleDimensionValue(VCS_ROOT_DIMENSION);
      if (vcsRootLocator != null) {
        final List<SVcsRoot> vcsRoots = myVcsRootFinder.getItems(vcsRootLocator).myEntries;
        result.add(item -> vcsRoots.contains(item.getParent()));
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

    final Boolean commitHookMode = locator.getSingleDimensionValueAsBoolean(COMMIT_HOOK_MODE);
    if (commitHookMode != null){
      result.add(item -> FilterUtil.isIncludedByBooleanFilter(commitHookMode, !((VcsRootInstanceEx)item).isPollingMode()));
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

    if (locator.isUnused(BUILD_TYPE)) {
      final String buildTypesLocator = locator.getSingleDimensionValue(BUILD_TYPE);
      if (buildTypesLocator != null) {
        Set<VcsRootInstance> vcsRootInstances = getInstances(buildTypesLocator, locator.lookupSingleDimensionValueAsBoolean(HAS_VERSIONED_SETTINGS_ONLY));
        result.add(item -> vcsRootInstances.contains(item));
      }
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
  private List<BuildTypeOrTemplate> getBuildTypeOrTemplates(@NotNull final String buildTypeLocator) {
    List<BuildTypeOrTemplate> buildTypes = myBuildTypeFinder.getItemsNotEmpty(buildTypeLocator).myEntries;
    buildTypes.forEach(bt -> BuildTypeFinder.check(bt.get(), myPermissionChecker));
    return buildTypes;
  }

  @NotNull
  @Override
  public ItemHolder<VcsRootInstance> getPrefilteredItems(@NotNull Locator locator) {
    setLocatorDefaults(locator);
    Boolean versionedSettingsUsagesOnly = locator.getSingleDimensionValueAsBoolean(HAS_VERSIONED_SETTINGS_ONLY);  // should check it not in Filter as it considers current scope

    final String build = locator.getSingleDimensionValue(BUILD);
    if (build != null) {
      Stream<VcsRootInstance> vcsRootInstancesByBuilds = getVcsRootInstancesByBuilds(build);
      if (BooleanUtils.isTrue(versionedSettingsUsagesOnly)) {
        vcsRootInstancesByBuilds = vcsRootInstancesByBuilds.filter(vcsRootInstance -> vcsRootInstance.equals(myVersionedSettingsManager.getVersionedSettingsVcsRootInstance(vcsRootInstance.getParent().getProject())));
      } else if (BooleanUtils.isFalse(versionedSettingsUsagesOnly)) {
        vcsRootInstancesByBuilds = vcsRootInstancesByBuilds.filter(vcsRootInstance -> !vcsRootInstance.equals(myVersionedSettingsManager.getVersionedSettingsVcsRootInstance(vcsRootInstance.getParent().getProject())));
      }
      return FinderDataBinding.getItemHolder(vcsRootInstancesByBuilds);
    }

    final String vcsRootLocator = locator.getSingleDimensionValue(VCS_ROOT_DIMENSION);
    if (vcsRootLocator != null) {
      final List<SVcsRoot> vcsRoots = myVcsRootFinder.getItemsNotEmpty(vcsRootLocator).myEntries;
      final Set<VcsRootInstance> result = new TreeSet<>(VCS_ROOT_INSTANCE_COMPARATOR);

      final String buildTypesLocator = locator.getSingleDimensionValue(BUILD_TYPE);
      Predicate<SBuildType> filter;
      Set<SProject> projects;
      if (buildTypesLocator != null) {
        if (versionedSettingsUsagesOnly == null || !versionedSettingsUsagesOnly) {  //is used below in the same condition
          ItemFilter<BuildTypeOrTemplate> buildTypeFilter = myBuildTypeFinder.getFilter(buildTypesLocator);
          filter = sBuildType -> buildTypeFilter.isIncluded(new BuildTypeOrTemplate(sBuildType));
        } else {
          filter = (a) -> true;
        }

        if (versionedSettingsUsagesOnly == null || versionedSettingsUsagesOnly) { //is used below in the same condition
          projects = myBuildTypeFinder.getItemsNotEmpty(buildTypesLocator).myEntries.stream().map(BuildTypeOrTemplate::getProject).collect(Collectors.toSet());
        } else {
          projects = null;
        }
      } else {
        filter = (a) -> true;
        projects = null;
      }

      filterOutUnrelatedWithoutParameterResolution(locator, vcsRoots);

      for (SVcsRoot vcsRoot : vcsRoots) {
        if (versionedSettingsUsagesOnly == null || !versionedSettingsUsagesOnly) {
          vcsRoot.getUsagesInConfigurations().stream().filter(filter).collect(Collectors.toList()).stream().map(buildType -> buildType.getVcsRootInstanceForParent(vcsRoot)).filter(Objects::nonNull)
                 .filter(rootInstance -> hasPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, rootInstance)) //minor performance optimization not to return roots which will be filtered in the filter
                 .forEach(result::add);
        }

        if (versionedSettingsUsagesOnly == null || versionedSettingsUsagesOnly) {
          Set<SProject> projectsBySettingsRoot = myVersionedSettingsManager.getProjectsBySettingsRoot(vcsRoot);
          result.addAll(getSettingsRootInstances(projects == null ? projectsBySettingsRoot : CollectionsUtil.intersect(projectsBySettingsRoot, projects)));
        }
      }
      return getItemHolder(result);
    }

    final String buildTypesLocator = locator.getSingleDimensionValue(BUILD_TYPE);
    if (buildTypesLocator != null) {
      return getItemHolder(getInstances(buildTypesLocator, versionedSettingsUsagesOnly));
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

  private static class CannedException extends RuntimeException {
    static final CannedException INSTANCE = new CannedException();
    private CannedException(){ super();}
  }

  private void filterOutUnrelatedWithoutParameterResolution(@NotNull final Locator locator, @NotNull final List<SVcsRoot> vcsRoots) {
    final List<String> properties = locator.lookupDimensionValue(PROPERTY);
    if (properties.isEmpty()) return;

    final Matcher<ParametersProvider> parameterCondition = ParameterCondition.create(properties);

    for (Iterator<SVcsRoot> iterator = vcsRoots.iterator(); iterator.hasNext(); ) {
      SVcsRoot vcsRoot = iterator.next();

      Map<String, String> propertiesMap = vcsRoot.getProperties();
      try {
        //this assumes something about matcher: e.g. that it's logic does not change while processing items
        boolean matches = parameterCondition.matches(new ParametersProvider() {
          @Override
          public String get(@NotNull final String key) {
            String value = propertiesMap.get(key);
            if (value != null && ReferencesResolverUtil.containsReference(value)) {
              throw CannedException.INSTANCE;
            }
            return value;
          }

          @Override
          public int size() {
            return propertiesMap.size();
          }

          @Override
          public Map<String, String> getAll() {
            if (propertiesMap.entrySet().stream().anyMatch(e -> ReferencesResolverUtil.containsReference(e.getValue()))) {
              throw CannedException.INSTANCE;
            }
            return propertiesMap;
          }
        });

        //while filtering, no reference was encountered: remove from the collection if it does not match the condition
        if (!matches) iterator.remove();

      } catch (CannedException ignore) {
        //encountered a reference: preserve in the collection
      }
    }
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
  private Set<VcsRootInstance> getInstances(final @NotNull String buildTypesLocator, @Nullable final Boolean versionedSettingsUsagesOnly) {
    List<BuildTypeOrTemplate> buildTypes = getBuildTypeOrTemplates(buildTypesLocator);
    TreeSet<VcsRootInstance> result = new TreeSet<>(VCS_ROOT_INSTANCE_COMPARATOR);
    if (versionedSettingsUsagesOnly == null || !versionedSettingsUsagesOnly) {
      buildTypes.stream().flatMap(bt -> bt.getVcsRootInstanceEntries().stream()).map(vcsRE -> vcsRE.getVcsRoot()).forEach(result::add);
    }
    if (versionedSettingsUsagesOnly == null || versionedSettingsUsagesOnly) {
      result.addAll(getSettingsRootInstances(buildTypes.stream().map(bt -> bt.getProject()).collect(Collectors.toSet())));
    }
    return result;
  }

  //todo: use getAllProjectUsages here?
  private Set<VcsRootInstance> getSettingsRootInstances(@NotNull final Collection<SProject> projectsInRoot) {
    Set<VcsRootInstance> result = new TreeSet<>(VCS_ROOT_INSTANCE_COMPARATOR);
    for (SProject project : projectsInRoot) {
      VcsRootInstance instance = myVersionedSettingsManager.getVersionedSettingsVcsRootInstance(project);
      if (instance != null) {
        if (hasPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, instance)) { //minor performance optimization not to return roots which will be filtered in the filter
          result.add(instance);
        }
      }
    }
    return result;
  }

  private boolean hasPermission(@NotNull final Permission permission, @NotNull final VcsRootInstance rootInstance) {
    try {
      myVcsRootFinder.checkPermission(permission, rootInstance.getParent());
      return true;
    } catch (AuthorizationFailedException e) {
      return false;
    }
  }

  public void checkPermission(@NotNull final Permission permission, @NotNull final VcsRootInstance rootInstance) {
    //todo: check and use AuthUtil.hasReadAccessTo(jetbrains.buildServer.serverSide.auth.AuthorityHolder, jetbrains.buildServer.vcs.VcsRootInstance)
    myVcsRootFinder.checkPermission(permission, rootInstance.getParent()); //todo: make this more precise, currently too demanding
  }
}
