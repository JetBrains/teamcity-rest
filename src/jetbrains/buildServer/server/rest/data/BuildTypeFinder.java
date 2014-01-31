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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.parameters.impl.MapParametersProviderImpl;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.13
 */
public class BuildTypeFinder extends AbstractFinder<BuildTypeOrTemplate> {
  private static final Logger LOG = Logger.getInstance(BuildTypeFinder.class.getName());

  public static final String TEMPLATE_ID_PREFIX = "template:"; //used for old ids parsing

  public static final String DIMENSION_ID = AbstractFinder.DIMENSION_ID;
  public static final String DIMENSION_INTERNAL_ID = "internalId";
  public static final String DIMENSION_PROJECT = "project";
  private static final String AFFECTED_PROJECT = "affectedProject";
  public static final String DIMENSION_NAME = "name";
  public static final String TEMPLATE_DIMENSION_NAME = "template";
  public static final String TEMPLATE_FLAG_DIMENSION_NAME = "templateFlag";
  public static final String PAUSED = "paused";
  protected static final String COMPATIBLE_AGENT = "compatibleAgent";
  protected static final String COMPATIBLE_AGENTS_COUNT = "compatibleAgentsCount";
  protected static final String PARAMETER = "parameter";
  protected static final String FILTER_BUILDS = "filterByBuilds";

  private final ProjectFinder myProjectFinder;
  @NotNull private final AgentFinder myAgentFinder;
  private final ProjectManager myProjectManager;
  private ServiceLocator myServiceLocator;

  public BuildTypeFinder(@NotNull final ProjectManager projectManager,
                         @NotNull final ProjectFinder projectFinder,
                         @NotNull final AgentFinder agentFinder,
                         @NotNull final ServiceLocator serviceLocator) {
    super(new String[]{DIMENSION_ID, DIMENSION_INTERNAL_ID, DIMENSION_PROJECT, AFFECTED_PROJECT, DIMENSION_NAME, TEMPLATE_FLAG_DIMENSION_NAME, TEMPLATE_DIMENSION_NAME, PAUSED,
      Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME,
      PagerData.START,
      PagerData.COUNT
    });
    myProjectManager = projectManager;
    myProjectFinder = projectFinder;
    myAgentFinder = agentFinder;
    myServiceLocator = serviceLocator;
  }

  @NotNull
  public static String getLocator(@NotNull final SBuildType buildType) {
    return Locator.createEmptyLocator().setDimension(DIMENSION_ID, buildType.getExternalId()).getStringRepresentation();
  }

  @NotNull
  public static String getLocator(@NotNull final BuildTypeTemplate template) {
    return Locator.createEmptyLocator().setDimension(DIMENSION_ID, template.getExternalId()).getStringRepresentation();
  }

  @NotNull
  @Override
  public Locator createLocator(@Nullable final String locatorText) {
    final Locator result = super.createLocator(locatorText);
    result.addHiddenDimensions(COMPATIBLE_AGENT, COMPATIBLE_AGENTS_COUNT, PARAMETER, FILTER_BUILDS); //hide these for now
    return result;
  }

  @NotNull
  @Override
  public List<BuildTypeOrTemplate> getAllItems() {
    final List<BuildTypeOrTemplate> result = new ArrayList<BuildTypeOrTemplate>();
    result.addAll(BuildTypes.fromBuildTypes(myProjectManager.getAllBuildTypes()));
    result.addAll(BuildTypes.fromTemplates(myProjectManager.getAllTemplates()));
    return result;
  }

  @Override
  @Nullable
  protected BuildTypeOrTemplate findSingleItem(@NotNull final Locator locator) {
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's an internal id, external id or name
      final String value = locator.getSingleValue();
      assert value != null;
      BuildTypeOrTemplate buildType = findBuildTypeOrTemplateByInternalId(value, null);
      if (buildType != null) {
        return buildType;
      }

      buildType = findBuildTypeOrTemplateByExternalId(value, null);
      if (buildType != null) {
        return buildType;
      }

      // assume it's a name
      final BuildTypeOrTemplate buildTypeByName = findBuildTypeByName(null, value, null);
      if (buildTypeByName != null) {
        return buildTypeByName;
      }
      throw new NotFoundException("No build type or template is found by id, internal id or name '" + value + "'.");
    }

    @Nullable SProject project = null;
    String projectLocator = locator.getSingleDimensionValue(DIMENSION_PROJECT);
    if (projectLocator != null) {
      project = myProjectFinder.getProject(projectLocator);
    }

    String internalId = locator.getSingleDimensionValue(DIMENSION_INTERNAL_ID);
    if (!StringUtil.isEmpty(internalId)) {
      Boolean template = locator.getSingleDimensionValueAsBoolean(TEMPLATE_FLAG_DIMENSION_NAME);
      if (template == null) {
        //legacy support for boolean value
        try {
          template = locator.getSingleDimensionValueAsBoolean(TEMPLATE_DIMENSION_NAME);
        } catch (LocatorProcessException e) {
          //override default message as it might be confusing here due to legacy support
          throw new BadRequestException("Try omitting dimension '" + TEMPLATE_DIMENSION_NAME + "' here");
        }
      }
      BuildTypeOrTemplate buildType = findBuildTypeOrTemplateByInternalId(internalId, template);
      if (buildType != null) {
        return buildType;
      }
      throw new NotFoundException("No " + getName(template) + " is found by internal id '" + internalId + "'.");
    }

    String id = locator.getSingleDimensionValue(DIMENSION_ID);
    if (!StringUtil.isEmpty(id)) {
      Boolean template = locator.getSingleDimensionValueAsBoolean(TEMPLATE_FLAG_DIMENSION_NAME);
      if (template == null) {
        //legacy support for boolean value
        try {
          template = locator.getSingleDimensionValueAsBoolean(TEMPLATE_DIMENSION_NAME);
        } catch (LocatorProcessException e) {
          //override default message as it might be confusing here due to legacy support
          throw new BadRequestException("Try omitting dimension '" + TEMPLATE_DIMENSION_NAME + "' here");
        }
      }
      BuildTypeOrTemplate buildType = findBuildTypeOrTemplateByExternalId(id, template);
      if (buildType != null) {
        return buildType;
      }

      // support pre-8.0 style of template ids
      final BuildTypeOrTemplate templateByOldIdWithPrefix = findTemplateByOldIdWithPrefix(id);
      if (templateByOldIdWithPrefix != null) {
        return templateByOldIdWithPrefix;
      }

      if (TeamCityProperties.getBoolean(APIController.REST_COMPATIBILITY_ALLOW_EXTERNAL_ID_AS_INTERNAL)) {
        buildType = findBuildTypeOrTemplateByInternalId(id, template);
        if (buildType != null) {
          return buildType;
        }
        throw new NotFoundException("No " + getName(template) + " is found by id '" + id + "' in compatibility mode." +
                                    " Cannot be found by external or internal id '" + id + "'.");
      }
      throw new NotFoundException("No " + getName(template) + " is found by id '" + id + "'.");
    }

    String name = locator.getSingleDimensionValue(DIMENSION_NAME);
    if (name != null) {
      Boolean template = locator.getSingleDimensionValueAsBoolean(TEMPLATE_FLAG_DIMENSION_NAME);
      if (template == null) {
        //legacy support for boolean value
        try {
          template = locator.getSingleDimensionValueAsBoolean(TEMPLATE_DIMENSION_NAME);
        } catch (LocatorProcessException e) {
          //override default message as it might be confusing here due to legacy support
          throw new BadRequestException("Try omitting dimension '" + TEMPLATE_DIMENSION_NAME + "' here");
        }
      }
      final BuildTypeOrTemplate buildTypeByName = findBuildTypeByName(project, name, template);
      if (buildTypeByName != null) {
        return buildTypeByName;
      }
      throw new NotFoundException(
        "No " + getName(template) + " is found by name '" + name + "'" + (project != null ? " in project '" + LogUtil.describe(project) + "'" : "") + ".");
    }

    return null;
  }


  @NotNull
  @Override
  protected AbstractFilter<BuildTypeOrTemplate> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<BuildTypeOrTemplate> result =
      new MultiCheckerFilter<BuildTypeOrTemplate>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);

    final String projectLocator = locator.getSingleDimensionValue(DIMENSION_PROJECT);
    SProject project = null;
    if (projectLocator != null) {
      project = myProjectFinder.getProject(projectLocator);
      final SProject internalProject = project;
      result.add(new FilterConditionChecker<BuildTypeOrTemplate>() {
        public boolean isIncluded(@NotNull final BuildTypeOrTemplate item) {
          return internalProject.equals(item.getProject());
        }
      });
    }

    final String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null) {
      @NotNull final SProject parentProject = myProjectFinder.getProject(affectedProjectDimension);
      result.add(new FilterConditionChecker<BuildTypeOrTemplate>() {
        public boolean isIncluded(@NotNull final BuildTypeOrTemplate item) {
          return ProjectFinder.isSameOrParent(parentProject, item.getProject());
        }
      });
    }

    final Boolean paused = locator.getSingleDimensionValueAsBoolean(PAUSED);
    if (paused != null) {
      result.add(new FilterConditionChecker<BuildTypeOrTemplate>() {
        public boolean isIncluded(@NotNull final BuildTypeOrTemplate item) {
          final Boolean pausedState = item.isPaused();
          return FilterUtil.isIncludedByBooleanFilter(paused, pausedState != null && pausedState);
        }
      });
    }

    final String compatibleAagentLocator = locator.getSingleDimensionValue(COMPATIBLE_AGENT); //experimental
    if (compatibleAagentLocator != null) {
      final SBuildAgent agent = myAgentFinder.getItem(compatibleAagentLocator);
      result.add(new FilterConditionChecker<BuildTypeOrTemplate>() {
        public boolean isIncluded(@NotNull final BuildTypeOrTemplate item) {
          return item.getBuildType() != null && item.getBuildType().getCompatibleAgents().contains(agent); //ineffective!
        }
      });
    }

    final Long compatibleAgentsCount = locator.getSingleDimensionValueAsLong(COMPATIBLE_AGENTS_COUNT); //experimental
    if (compatibleAgentsCount != null) {
      result.add(new FilterConditionChecker<BuildTypeOrTemplate>() {
        public boolean isIncluded(@NotNull final BuildTypeOrTemplate item) {
          return item.getBuildType() != null && compatibleAgentsCount.equals(Integer.valueOf(item.getBuildType().getCompatibleAgents().size()).longValue());
        }
      });
    }

    final String parameterDimension = locator.getSingleDimensionValue(PARAMETER);
    if (parameterDimension != null) {
      final ParameterCondition parameterCondition = ParameterCondition.create(parameterDimension);
      result.add(new FilterConditionChecker<BuildTypeOrTemplate>() {
        public boolean isIncluded(@NotNull final BuildTypeOrTemplate item) {
          return parameterCondition.matches(new MapParametersProviderImpl(item.get().getParameters()));
        }
      });
    }

    final String filterBuilds = locator.getSingleDimensionValue(FILTER_BUILDS); //experimental
    if (filterBuilds != null) {
      final Locator filterBuildsLocator = new Locator(filterBuilds, "search", "match");
      final String search = filterBuildsLocator.getSingleDimensionValue("search");
      final String match = filterBuildsLocator.getSingleDimensionValue("match");
      if (search != null) {
        result.add(new FilterConditionChecker<BuildTypeOrTemplate>() {
          public boolean isIncluded(@NotNull final BuildTypeOrTemplate item) {
            if (item.getBuildType() == null) return false;
            final BuildFinder buildFinder = myServiceLocator.getSingletonService(BuildFinder.class);
            final List<BuildPromotion> buildPromotions = BuildFinder.getBuildPromotions(buildFinder.getBuildsSimplified(item.getBuildType(), search));
            if (buildPromotions.isEmpty()) {
              return false;
            }
            if (match == null) {
              return buildPromotions.size() > 0;
            }
            final BuildPromotion buildPromotion = buildPromotions.get(0);
            final SBuild associatedBuild = buildPromotion.getAssociatedBuild();
            if (associatedBuild == null){
              return false; //queued builds are not yet supported
            }
            return buildFinder.getBuildsFilter(null, match).isIncluded(associatedBuild);
          }
        });
      }
    }

    return result;
  }

  @Override
  protected List<BuildTypeOrTemplate> getPrefilteredItems(@NotNull final Locator locator) {
    List<BuildTypeOrTemplate> result = new ArrayList<BuildTypeOrTemplate>();

    SProject project = null;
    final String projectLocator = locator.getSingleDimensionValue(DIMENSION_PROJECT);
    if (projectLocator != null) {
      project = myProjectFinder.getProject(projectLocator);
    }

    SProject affectedProject = null;
    final String affectedProjectLocator = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectLocator != null) {
      affectedProject = myProjectFinder.getProject(affectedProjectLocator);
    }

    final String templateLocator = locator.getSingleDimensionValue(TEMPLATE_DIMENSION_NAME);
    if (templateLocator != null) {
      final BuildTypeTemplate buildTemplate = getBuildTemplate(null, templateLocator);
      return BuildTypes.fromBuildTypes(buildTemplate.getUsages());
    }

    Boolean template = locator.getSingleDimensionValueAsBoolean(TEMPLATE_FLAG_DIMENSION_NAME);
    if (template == null || !template) {
      if (project != null) {
        result.addAll(BuildTypes.fromBuildTypes(project.getOwnBuildTypes()));
      } else if (affectedProject != null) {
        result.addAll(BuildTypes.fromBuildTypes(affectedProject.getBuildTypes()));
      } else {
        result.addAll(BuildTypes.fromBuildTypes(myProjectManager.getAllBuildTypes()));
      }
    }
    if (template == null || template) {
      if (project != null) {
        result.addAll(BuildTypes.fromTemplates(project.getOwnBuildTypeTemplates()));
      } else if (affectedProject != null) {
        result.addAll(BuildTypes.fromTemplates(affectedProject.getBuildTypeTemplates()));
      } else {
        result.addAll(BuildTypes.fromTemplates(myProjectManager.getAllTemplates()));
      }
    }

    return result;
  }




  @NotNull
  public BuildTypeOrTemplate getBuildTypeOrTemplate(@Nullable final SProject project, @Nullable final String buildTypeLocator) {
    if (StringUtil.isEmpty(buildTypeLocator)) {
      throw new BadRequestException("Empty build type locator is not supported.");
    }
    String actualLocator = buildTypeLocator;
    if (project != null) {
      actualLocator = Locator.setDimensionIfNotPresent(buildTypeLocator, DIMENSION_PROJECT, ProjectFinder.getLocator(project));
    }
    final BuildTypeOrTemplate result = getItem(actualLocator);
    if (project != null && !result.getProject().equals(project)) {
      throw new BadRequestException("Found " + LogUtil.describe(result) + " but it does not belong to project " + LogUtil.describe(project) + ".");
    }
    return result;
  }

  private String getName(final Boolean template) {
    if (template == null) {
      return "build type nor template";
    }
    return template ? "template" : "build type";
  }

  @NotNull
  public SBuildType getBuildType(@Nullable final SProject project, @Nullable final String buildTypeLocator) {
    final BuildTypeOrTemplate buildTypeOrTemplate = getBuildTypeOrTemplate(project, buildTypeLocator);
    if (buildTypeOrTemplate.getBuildType() != null) {
      return buildTypeOrTemplate.getBuildType();
    }
    throw new NotFoundException("No build type is found by locator '" + buildTypeLocator + "'. Template is found instead.");
  }

  @NotNull
  public BuildTypeTemplate getBuildTemplate(@Nullable final SProject project, @Nullable final String buildTypeLocator) {
    final BuildTypeOrTemplate buildTypeOrTemplate = getBuildTypeOrTemplate(project, buildTypeLocator);
    if (buildTypeOrTemplate.getTemplate() != null) {
      return buildTypeOrTemplate.getTemplate();
    }
    throw new BadRequestException("No build type template by locator '" + buildTypeLocator + "'. Build type is found instead.");
  }

  @NotNull
  public List<SBuildType> getBuildTypes(@Nullable final SProject project, @Nullable final String buildTypeLocator) {
    String actualLocator = Locator.setDimension(buildTypeLocator, TEMPLATE_FLAG_DIMENSION_NAME, "false");

    if (project != null) {
        actualLocator = Locator.setDimensionIfNotPresent(actualLocator, DIMENSION_PROJECT, ProjectFinder.getLocator(project));
    }

    final PagedSearchResult<BuildTypeOrTemplate> items = getItems(actualLocator);
    return CollectionsUtil.convertCollection(items.myEntries, new Converter<SBuildType, BuildTypeOrTemplate>() {
      public SBuildType createFrom(@NotNull final BuildTypeOrTemplate source) {
        if (project != null && !source.getProject().equals(project)) {
          throw new BadRequestException("Found " + LogUtil.describe(source.getBuildType()) + " but it does not belong to project " + LogUtil.describe(project) + ".");
        }
        return source.getBuildType();
      }
    });
  }

  @Nullable
  public SBuildType getBuildTypeIfNotNull(@Nullable final String buildTypeLocator) {
    return buildTypeLocator == null ? null : getBuildType(null, buildTypeLocator);
  }

  @Nullable
  public SBuildType deriveBuildTypeFromLocator(@Nullable SBuildType contextBuildType, @Nullable final String buildTypeLocator) {
    if (buildTypeLocator != null) {
      final SBuildType buildTypeFromLocator = getBuildType(null, buildTypeLocator);
      if (contextBuildType == null) {
        return buildTypeFromLocator;
      } else if (!contextBuildType.getBuildTypeId().equals(buildTypeFromLocator.getBuildTypeId())) {
        throw new BadRequestException("Explicit build type (" + contextBuildType.getBuildTypeId() +
                                      ") does not match build type in 'buildType' locator (" + buildTypeLocator + ").");
      }
    }
    return contextBuildType;
  }


  @Nullable
  private static BuildTypeOrTemplate getOwnBuildTypeOrTemplateByName(@NotNull final SProject project, @NotNull final String name, final Boolean isTemplate) {
    if (isTemplate == null || !isTemplate) {
      final SBuildType buildType = project.findBuildTypeByName(name);
      if (buildType != null) {
        return new BuildTypeOrTemplate(buildType);
      }
      if (isTemplate != null) return null;
    }
    final BuildTypeTemplate buildTypeTemplate = project.findBuildTypeTemplateByName(name);
    if (buildTypeTemplate != null) {
      return new BuildTypeOrTemplate(buildTypeTemplate);
    }
    return null;
  }

  /**
   *
   * @param project project to search build type in (subprojects are also searched). Can be 'null' to search in all the build types on the server.
   * @param name    name of the build type to search for.
   * @param isTemplate null to search for both build types and temapltes or true/false
   * @return build type with the name 'name'. If 'project' is not null, the search is performed only within 'project'.
   * @throws jetbrains.buildServer.server.rest.errors.BadRequestException if several build types with the same name are found
   */
  @Nullable
  private BuildTypeOrTemplate findBuildTypeByName(@Nullable final SProject project, @NotNull final String name, final Boolean isTemplate) {
    if (project != null) {
      BuildTypeOrTemplate result = getOwnBuildTypeOrTemplateByName(project, name, isTemplate);
      if (result != null){
        return result;
      }
      // try to find in subprojects if not found in the project directly
      return findBuildTypeinProjects(name, project.getProjects(), isTemplate);
    }

    return findBuildTypeinProjects(name, myProjectManager.getProjects(), isTemplate);
  }

  @Nullable
  private static BuildTypeOrTemplate findBuildTypeinProjects(final String name, final List<SProject> projects, final Boolean isTemplate) {
    BuildTypeOrTemplate firstFound = null;
    for (SProject project : projects) {
      final BuildTypeOrTemplate found = getOwnBuildTypeOrTemplateByName(project, name, isTemplate);
      if (found != null) {
        if (firstFound != null) {
          throw new BadRequestException("Several matching build types/templates found for name '" + name + "'.");
        }
        firstFound = found;
      }
    }
    return firstFound;
  }

  @Nullable
  private BuildTypeOrTemplate findBuildTypeOrTemplateByInternalId(@NotNull final String internalId, @Nullable final Boolean isTemplate) {
    if (isTemplate == null || !isTemplate) {
      SBuildType buildType = myProjectManager.findBuildTypeById(internalId);
      if (buildType != null) {
        return new BuildTypeOrTemplate(buildType);
      }
    }
    if (isTemplate == null || isTemplate) {
      final BuildTypeTemplate buildTypeTemplate = myProjectManager.findBuildTypeTemplateById(internalId);
      if (buildTypeTemplate != null) {
        return new BuildTypeOrTemplate(buildTypeTemplate);
      }
    }
    return null;
  }

  @Nullable
  private BuildTypeOrTemplate findBuildTypeOrTemplateByExternalId(@NotNull final String internalId, @Nullable final Boolean isTemplate) {
    if (isTemplate == null || !isTemplate) {
      SBuildType buildType = myProjectManager.findBuildTypeByExternalId(internalId);
      if (buildType != null) {
        return new BuildTypeOrTemplate(buildType);
      }
    }
    if (isTemplate == null || isTemplate) {
      final BuildTypeTemplate buildTypeTemplate = myProjectManager.findBuildTypeTemplateByExternalId(
        internalId);
      if (buildTypeTemplate != null) {
        return new BuildTypeOrTemplate(buildTypeTemplate);
      }
    }
    return null;
  }

  @Nullable
  private BuildTypeOrTemplate findTemplateByOldIdWithPrefix(@NotNull final String idWithPrefix) {
    if (!idWithPrefix.startsWith(TEMPLATE_ID_PREFIX)) {
      return null;
    }

    String templateId = idWithPrefix.substring(TEMPLATE_ID_PREFIX.length());
    final BuildTypeTemplate buildTypeTemplateByStrippedId = myProjectManager.findBuildTypeTemplateById(templateId);
    if (buildTypeTemplateByStrippedId != null) {
      return new BuildTypeOrTemplate(buildTypeTemplateByStrippedId);
    }
    return null;
  }

  @NotNull
  public static List<SBuildType> getBuildTypesByInternalIds(@NotNull final Collection<String> buildTypeIds, @NotNull final ProjectManager projectManager) {
    final ArrayList<SBuildType> result = new ArrayList<SBuildType>(buildTypeIds.size());
    for (String buildTypeId : buildTypeIds) {
      final SBuildType buildType = getBuildTypeByInternalId(buildTypeId, projectManager);
      result.add(buildType);
    }
    return result;
  }

  @NotNull
  public static SBuildType getBuildTypeByInternalId(@NotNull final String buildTypeInternalId, @NotNull final ProjectManager projectManager) {
    final SBuildType result = projectManager.findBuildTypeById(buildTypeInternalId);
    if (result == null) {
      throw new NotFoundException("No buildType found by internal id '" + buildTypeInternalId + "'.");
    }
    return result;
  }
}
