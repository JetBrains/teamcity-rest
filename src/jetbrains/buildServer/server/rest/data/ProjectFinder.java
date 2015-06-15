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
import java.util.List;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.parameters.impl.MapParametersProviderImpl;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.StringUtil;
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
  protected static final String DIMENSION_PARAMETER = "parameter";

  @NotNull private final ProjectManager myProjectManager;

  public ProjectFinder(@NotNull final ProjectManager projectManager){
    super(new String[]{DIMENSION_ID, DIMENSION_INTERNAL_ID, DIMENSION_UUID, DIMENSION_PROJECT, DIMENSION_AFFECTED_PROJECT, DIMENSION_NAME, DIMENSION_ARCHIVED,
      Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME,
      PagerData.START,
      PagerData.COUNT
    });
    myProjectManager = projectManager;
  }

  public static String getLocator(final BuildProject project) {
    return Locator.getStringLocator(DIMENSION_ID, project.getExternalId());
  }


  @NotNull
  @Override
  public Locator createLocator(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    final Locator result = super.createLocator(locatorText, locatorDefaults);
    result.addHiddenDimensions(DIMENSION_PARAMETER, DIMENSION_PARENT_PROJECT); //hide these for now
    return result;
  }

  @Nullable
  @Override
  protected SProject findSingleItem(@NotNull final Locator locator) {
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

    return null;
  }

  @Nullable
  private SProject getParentProject(final @NotNull Locator locator) {
    String parentProjectLocator = locator.getSingleDimensionValue(DIMENSION_PARENT_PROJECT); //compatibility mode for versions <9.1
    if (parentProjectLocator == null) parentProjectLocator = locator.getSingleDimensionValue(DIMENSION_AFFECTED_PROJECT);
    return parentProjectLocator == null ? null : getItem(parentProjectLocator);
  }

  @Nullable
  @Override
  public ItemHolder<SProject> getAllItems() {
    return getItemHolder(myProjectManager.getProjects());
  }

  @NotNull
  @Override
  protected AbstractFilter<SProject> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<SProject> result =
      new MultiCheckerFilter<SProject>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);

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

    final String parameterDimension = locator.getSingleDimensionValue(DIMENSION_PARAMETER);
    if (parameterDimension != null) {
      final ParameterCondition parameterCondition = ParameterCondition.create(parameterDimension);
      result.add(new FilterConditionChecker<SProject>() {
        public boolean isIncluded(@NotNull final SProject item) {
          return parameterCondition.matches(new MapParametersProviderImpl(item.getOwnParameters()));
        }
      });
    }

    return result;
  }

  @NotNull
  @Override
  protected ItemHolder<SProject> getPrefilteredItems(@NotNull final Locator locator) {

    SProject parentProject = getParentProject(locator);

    String name = locator.getSingleDimensionValue(DIMENSION_NAME);
    if (name != null) {
      if (parentProject != null) {
        return getItemHolder(findProjectsByName(parentProject, name, true));
      }
      String directParent = locator.getSingleDimensionValue(DIMENSION_PROJECT);
      if (directParent != null){
        return getItemHolder(findProjectsByName(getItem(directParent), name, false));
      }
    }

    if (parentProject != null) {
      return getItemHolder(parentProject.getProjects());
    }

    String directParent = locator.getSingleDimensionValue(DIMENSION_PROJECT);
    if (directParent != null){
      return getItemHolder(getItem(directParent).getOwnProjects());
    }
    return super.getPrefilteredItems(locator);
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
}
