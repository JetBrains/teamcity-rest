/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.model.project.PropEntityProjectFeature;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.13
 */
public class ProjectFinder extends AbstractFinder<SProject> {
  private static final Logger LOG = Logger.getInstance(ProjectFinder.class.getName());

  public static final String DIMENSION_ID = AbstractFinder.DIMENSION_ID;
  public static final String DIMENSION_INTERNAL_ID = "internalId";
  public static final String DIMENSION_UUID = "uuid";
  public static final String DIMENSION_PROJECT = "project";
  public static final String DIMENSION_PARENT_PROJECT = "parentProject";
  private static final String DIMENSION_AFFECTED_PROJECT = "affectedProject";
  public static final String DIMENSION_NAME = "name";
  public static final String DIMENSION_ARCHIVED = "archived";
  public static final String DIMENSION_READ_ONLY_UI = "readOnlyUI";
  protected static final String DIMENSION_PARAMETER = "parameter";
  protected static final String DIMENSION_SELECTED = "selectedByUser";
  public static final String BUILD = "build";
  public static final String BUILD_TYPE = "buildType";
  public static final String VCS_ROOT = "vcsRoot";
  public static final String FEATURE = "projectFeature";

  @NotNull private final ProjectManager myProjectManager;
  private final PermissionChecker myPermissionChecker;
  @NotNull private final ServiceLocator myServiceLocator;

  public ProjectFinder(@NotNull final ProjectManager projectManager, final PermissionChecker permissionChecker, @NotNull final ServiceLocator serviceLocator){
    super(DIMENSION_ID, DIMENSION_INTERNAL_ID, DIMENSION_UUID, DIMENSION_PROJECT, DIMENSION_AFFECTED_PROJECT, DIMENSION_NAME, DIMENSION_ARCHIVED,
          BUILD, BUILD_TYPE, VCS_ROOT, FEATURE, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    setHiddenDimensions(DIMENSION_PARAMETER, DIMENSION_SELECTED, DIMENSION_READ_ONLY_UI,
                        DIMENSION_LOOKUP_LIMIT,
                        DIMENSION_PARENT_PROJECT //compatibility mode for versions <9.1
    );
    myProjectManager = projectManager;
    myPermissionChecker = permissionChecker;
    myServiceLocator = serviceLocator;
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final SProject project) {
    return ProjectFinder.getLocator(project);
  }

  public static String getLocator(final BuildProject project) {
    return Locator.getStringLocator(DIMENSION_ID, project.getExternalId());
  }

  @Nullable
  @Override
  public SProject findSingleItem(@NotNull final Locator locator) {
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's a name or internal id or external id
      SProject project = null;
      @SuppressWarnings("ConstantConditions") @NotNull final String singleValue = locator.getSingleValue();
      project = myProjectManager.findProjectByExternalId(singleValue);
      if (project != null) {
        return project;
      }
      final List<SProject> projectsByName = findProjectsByName(null, singleValue, true);
      if (projectsByName.size() == 1) {
        return projectsByName.get(0);
      }
      project = myProjectManager.findProjectById(singleValue);
      if (project != null) {
        return project;
      }
      throw new NotFoundException("No project found by name or internal/external id '" + singleValue + "'.");
    }

    String id = locator.getSingleDimensionValue(DIMENSION_ID);
    if (id != null) {
      SProject project = myProjectManager.findProjectByExternalId(id);
      if (project == null) {
        if (TeamCityProperties.getBoolean(APIController.REST_COMPATIBILITY_ALLOW_EXTERNAL_ID_AS_INTERNAL)) {
          project = myProjectManager.findProjectById(id);
          if (project == null) {
            throw new NotFoundException("No project found by locator '" + locator.getStringRepresentation() +
                                        "' in compatibility mode. Project cannot be found by external or internal id '" + id + "'.");
          }
        } else {
          throw new NotFoundException("No project found by locator '" + locator.getStringRepresentation() + "'. Project cannot be found by external id '" + id + "'.");
        }
      }
      return project;
    }

    String internalId = locator.getSingleDimensionValue(DIMENSION_INTERNAL_ID);
    if (internalId != null) {
      SProject project = myProjectManager.findProjectById(internalId);
      if (project == null) {
        throw new NotFoundException("No project found by locator '" + locator.getStringRepresentation() + "'. Project cannot be found by internal id '" + internalId + "'.");
      }
      return project;
    }

    String uuid = locator.getSingleDimensionValue(DIMENSION_UUID);
    if (!StringUtil.isEmpty(uuid)) {

      SProject project = myProjectManager.findProjectByConfigId(uuid);
      if (project == null) {
        //protecting against brute force uuid guessing
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          //ignore
        }
        throw new NotFoundException("No project found by locator '" + locator.getStringRepresentation() + "'. Project cannot be found by uuid '" + uuid + "'.");
      }
      return project;
    }

    String buildLocator = locator.getSingleDimensionValue(BUILD);
    if (!StringUtil.isEmpty(buildLocator)) {
      BuildPromotion build = myServiceLocator.getSingletonService(BuildPromotionFinder.class).getItem(buildLocator);
      SBuildType buildType = build.getBuildType();
      if (buildType != null) {
        return buildType.getProject();
      }
    }

    String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
    if (!StringUtil.isEmpty(buildTypeLocator)) {
      BuildTypeOrTemplate buildType = myServiceLocator.getSingletonService(BuildTypeFinder.class).getItem(buildTypeLocator);
      return buildType.getProject();
    }

    String vcsRootLocator = locator.getSingleDimensionValue(VCS_ROOT);
    if (!StringUtil.isEmpty(vcsRootLocator)) {
      SVcsRoot vcsRoot = myServiceLocator.getSingletonService(VcsRootFinder.class).getItem(vcsRootLocator);
      return vcsRoot.getProject();
    }

    return null;
  }

  @Nullable
  private SProject getParentProject(final @NotNull Locator locator) {
    String parentProjectLocator = locator.getSingleDimensionValue(DIMENSION_PARENT_PROJECT); //compatibility mode for versions <9.1
    if (parentProjectLocator == null) parentProjectLocator = locator.getSingleDimensionValue(DIMENSION_AFFECTED_PROJECT);
    return parentProjectLocator == null ? null : getItem(parentProjectLocator);
  }

  @NotNull
  @Override
  public ItemFilter<SProject> getFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<SProject> result = new MultiCheckerFilter<SProject>();

    final String name = locator.getSingleDimensionValue(DIMENSION_NAME);
    if (name != null) {
      result.add(new FilterConditionChecker<SProject>() {
        public boolean isIncluded(@NotNull final SProject item) {
          return name.equals(item.getName());
        }
      });
    }

    final Boolean archived = locator.getSingleDimensionValueAsBoolean(DIMENSION_ARCHIVED);
    if (archived != null) {
      result.add(new FilterConditionChecker<SProject>() {
        public boolean isIncluded(@NotNull final SProject item) {
          return FilterUtil.isIncludedByBooleanFilter(archived, item.isArchived());
        }
      });
    }

    final Boolean readOnlyUI = locator.getSingleDimensionValueAsBoolean(DIMENSION_READ_ONLY_UI);
    if (readOnlyUI != null) {
      result.add(new FilterConditionChecker<SProject>() {
        public boolean isIncluded(@NotNull final SProject item) {
          return FilterUtil.isIncludedByBooleanFilter(readOnlyUI, item.isReadOnly());
        }
      });
    }

    final String parameterDimension = locator.getSingleDimensionValue(DIMENSION_PARAMETER);
    if (parameterDimension != null) {
      final ParameterCondition parameterCondition = ParameterCondition.create(parameterDimension);
      result.add(new FilterConditionChecker<SProject>() {
        public boolean isIncluded(@NotNull final SProject item) {
          final boolean canView = !Project.shouldRestrictSettingsViewing(item, myPermissionChecker);
          if (!canView) {
            LOG.debug("While filtering projects by " + DIMENSION_PARAMETER + " user does not have enough permissions to see settings. Excluding project: " + item.describe(false));
            return false;
          }
          return parameterCondition.matches(item);
        }
      });
    }

    if (locator.isUnused(DIMENSION_PROJECT)) {
      final String directParentLocator = locator.getSingleDimensionValue(DIMENSION_PROJECT);
      if (directParentLocator != null) {
        final SProject directParent = getItem(directParentLocator);
        result.add(new FilterConditionChecker<SProject>() {
          public boolean isIncluded(@NotNull final SProject item) {
            return directParent.equals(item.getParent());
          }
        });
      }
    }

    if (locator.isUnused(DIMENSION_AFFECTED_PROJECT)) {
      final SProject parentProject = getParentProject(locator);
      if (parentProject != null) {
        result.add(new FilterConditionChecker<SProject>() {
          public boolean isIncluded(@NotNull final SProject item) {
            return isSameOrParent(parentProject, item);
          }
        });
      }
    }

    final String featureDimension = locator.getSingleDimensionValue(FEATURE);
    if (featureDimension != null) {
      result.add(new FilterConditionChecker<SProject>() {
        public boolean isIncluded(@NotNull final SProject item) {
          final boolean canView = !Project.shouldRestrictSettingsViewing(item, myPermissionChecker);
          if (!canView) {
            LOG.debug("While filtering projects by " + DIMENSION_PARAMETER + " user does not have enough permissions to see settings. Excluding project: " + item.describe(false));
            return false;
          }
          return new PropEntityProjectFeature.ProjectFeatureFinder(item).getItems(featureDimension).myEntries.size() > 0;
        }
      });
    }

    return result;
  }

  @NotNull
  @Override
  public ItemHolder<SProject> getPrefilteredItems(@NotNull final Locator locator) {

    //this should be the first one as the order returned here is important!
    final String selectedForUser = locator.getSingleDimensionValue(DIMENSION_SELECTED);
    if (selectedForUser != null) {
      final SUser user = myServiceLocator.getSingletonService(UserFinder.class).getItem(selectedForUser);
      return getItemHolder(getSelectedProjects(user));
    }

    final SProject parentProject = getParentProject(locator);
    final String name = locator.getSingleDimensionValue(DIMENSION_NAME);
    if (name != null) {
      if (parentProject != null) {
        return getItemHolder(findProjectsByName(parentProject, name, true));
      }
      String directParent = locator.getSingleDimensionValue(DIMENSION_PROJECT);
      if (directParent != null){
        return getItemHolder(findProjectsByName(getItem(directParent), name, false));
      }
    }

    final String directParent = locator.getSingleDimensionValue(DIMENSION_PROJECT);
    if (directParent != null) {
      return getItemHolder(getItem(directParent).getOwnProjects());
    }

    if (parentProject != null) {
      return getItemHolder(parentProject.getProjects());
    }

    return getItemHolder(myProjectManager.getProjects());
  }

  @NotNull
  public Collection<SProject> getSelectedProjects(@NotNull final SUser user) {
    //TeamCity API issue: the order of the projects is not completely clear: is project's hierarchy is applied (seems like it is not)
    // also, if user has not configured visibility, what the order will be?
    final List<String> visibleProjects = user.getVisibleProjects();
    return visibleProjects == null ? Collections.<SProject>emptyList() : CollectionsUtil.convertAndFilterNulls(visibleProjects, new Converter<SProject, String>() {
      public SProject createFrom(@NotNull final String projectInternalId) {
        return myProjectManager.findProjectById(projectInternalId);
      }
    });
  }

  @NotNull
  public SProject getRootProject() {
    return myProjectManager.getRootProject();
  }

  /**
   * Finds projects with the given name under the project specified
   * @param parentProject Project under which to search. If 'null' - process all projects including root one.
   * @param name
   * @param recursive
   * @return
   */
  @NotNull
  private List<SProject> findProjectsByName(@Nullable SProject parentProject, @NotNull final String name, final boolean recursive) {
    final ArrayList<SProject> result = new ArrayList<SProject>();
    if (parentProject == null) {
      parentProject = getRootProject();
      if (name.equals(parentProject.getName())) { //process root project as well
        result.add(parentProject);
      }
    }
    final List<SProject> projects = recursive ? parentProject.getProjects() : parentProject.getOwnProjects();
    for (SProject project : projects) {
      if (name.equals(project.getName())){
        result.add(project);
      }
    }
    return result;
  }

  @Nullable
  public BuildProject findProjectByInternalId(final String projectInternalId) {
    return myProjectManager.findProjectById(projectInternalId);
  }

  @NotNull
  public static SProject getProjectByInternalId(@NotNull final String projectInternalId, @NotNull final ProjectManager projectManager) {
    final SProject project = projectManager.findProjectById(projectInternalId);
    if (project == null) {
      throw new NotFoundException("No project found by internal id '" + projectInternalId + "'.");
    }
    return project;
  }

  public static boolean isSameOrParent(@NotNull final BuildProject parent, @NotNull final BuildProject project) {
    if (parent.getProjectId().equals(project.getProjectId())) return true;
    if (project.getParentProject() == null) return false;
    return isSameOrParent(parent, project.getParentProject());
  }

  @NotNull
  public SProject getItem(@Nullable final String locatorText, final boolean checkViewSettingsPermission) {
    final SProject result = getItem(locatorText);
    if (checkViewSettingsPermission) {
      check(result, myPermissionChecker);
    }
    return result;
  }

  @NotNull
  public PagedSearchResult<SProject> getItems(final @Nullable SProject parentProject, final @Nullable String projectLocator) {
    String actualLocator = projectLocator;
    if (parentProject != null && (projectLocator == null || !(new Locator(projectLocator)).isSingleValue())) {
      actualLocator = Locator.setDimensionIfNotPresent(projectLocator, DIMENSION_PROJECT, ProjectFinder.getLocator(parentProject));
    }

    return getItems(actualLocator);
  }

  public static void check(@NotNull SProject project, @NotNull final PermissionChecker permissionChecker) {
    if (Project.shouldRestrictSettingsViewing(project, permissionChecker)) {
      throw new AuthorizationFailedException(
        "User does not have '" + Permission.VIEW_BUILD_CONFIGURATION_SETTINGS.getName() + "' permission in project " + project.describe(false));
    }
  }
}
