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
import java.util.*;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.dependency.Dependency;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsManager;
import jetbrains.buildServer.vcs.VcsRootInstanceEntry;
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
  public static final String DIMENSION_UUID = "uuid";
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
  protected static final String SNAPSHOT_DEPENDENCY = "snapshotDependency";
  protected static final String DIMENSION_SELECTED = "selectedByUser";
  public static final String VCS_ROOT_DIMENSION = "vcsRoot";
  public static final String VCS_ROOT_INSTANCE_DIMENSION = "vcsRootInstance";
  public static final String BUILD = "build";

  private final ProjectFinder myProjectFinder;
  @NotNull private final AgentFinder myAgentFinder;
  private final ProjectManager myProjectManager;
  private final ServiceLocator myServiceLocator;
  private final PermissionChecker myPermissionChecker;

  public BuildTypeFinder(@NotNull final ProjectManager projectManager,
                         @NotNull final ProjectFinder projectFinder,
                         @NotNull final AgentFinder agentFinder,
                         final PermissionChecker permissionChecker,
                         @NotNull final ServiceLocator serviceLocator) {
    super(DIMENSION_ID, DIMENSION_INTERNAL_ID, DIMENSION_UUID, DIMENSION_PROJECT, AFFECTED_PROJECT, DIMENSION_NAME, TEMPLATE_FLAG_DIMENSION_NAME,
      TEMPLATE_DIMENSION_NAME, PAUSED, VCS_ROOT_DIMENSION, VCS_ROOT_INSTANCE_DIMENSION, BUILD,
      Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    setHiddenDimensions(COMPATIBLE_AGENT, COMPATIBLE_AGENTS_COUNT, PARAMETER, FILTER_BUILDS, SNAPSHOT_DEPENDENCY, DIMENSION_SELECTED, DIMENSION_LOOKUP_LIMIT);
    myProjectManager = projectManager;
    myProjectFinder = projectFinder;
    myAgentFinder = agentFinder;
    myPermissionChecker = permissionChecker;
    myServiceLocator = serviceLocator;
  }

  @NotNull
  public String getItemLocator(@NotNull final BuildTypeOrTemplate buildType) {
    return Locator.getStringLocator(DIMENSION_ID, buildType.getId());
  }

  @NotNull
  public static String getLocator(@NotNull final SBuildType buildType) {
    return Locator.createEmptyLocator().setDimension(DIMENSION_ID, buildType.getExternalId()).getStringRepresentation();
  }

  @NotNull
  public static String getLocator(@NotNull final BuildTypeTemplate template) {
    return Locator.createEmptyLocator().setDimension(DIMENSION_ID, template.getExternalId()).getStringRepresentation();
  }

  @Override
  @Nullable
  public BuildTypeOrTemplate findSingleItem(@NotNull final Locator locator) {
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
      final BuildTypeOrTemplate buildTypeByName = findBuildTypebyName(value, null, null);
      if (buildTypeByName != null) {
        return buildTypeByName;
      }
      throw new NotFoundException("No build type or template is found by id, internal id or name '" + value + "'.");
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

    String uuid = locator.getSingleDimensionValue(DIMENSION_UUID);
    if (!StringUtil.isEmpty(uuid)) {
      Boolean template = locator.getSingleDimensionValueAsBoolean(TEMPLATE_FLAG_DIMENSION_NAME);
      BuildTypeOrTemplate buildType = findBuildTypeOrTemplateByUuid(uuid, template);
      if (buildType != null) {
        return buildType;
      }
      //protecting against brute force uuid guessing
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        //ignore
      }
      throw new NotFoundException("No " + getName(template) + " is found by uuid '" + uuid + "'.");
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

    String buildLocator = locator.getSingleDimensionValue(BUILD);
    if (!StringUtil.isEmpty(buildLocator)) {
      BuildPromotion build = myServiceLocator.getSingletonService(BuildPromotionFinder.class).getItem(buildLocator);
      SBuildType buildType = build.getBuildType();
      if (buildType != null) {
        return new BuildTypeOrTemplate(buildType);
      }
    }

    return null;
  }


  @NotNull
  @Override
  public ItemFilter<BuildTypeOrTemplate> getFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<BuildTypeOrTemplate> result = new MultiCheckerFilter<BuildTypeOrTemplate>();

    final String name = locator.getSingleDimensionValue(DIMENSION_NAME);
    if (name != null) {
      result.add(new FilterConditionChecker<BuildTypeOrTemplate>() {
        public boolean isIncluded(@NotNull final BuildTypeOrTemplate item) {
          return name.equalsIgnoreCase(item.getName());
        }
      });
    }

    final String projectLocator = locator.getSingleDimensionValue(DIMENSION_PROJECT);
    if (projectLocator != null) {
      final List<SProject> projects = myProjectFinder.getItems(projectLocator).myEntries;
      if (projects.size() == 1) {
        final SProject internalProject = projects.iterator().next();
        result.add(new FilterConditionChecker<BuildTypeOrTemplate>() {
          public boolean isIncluded(@NotNull final BuildTypeOrTemplate item) {
            return internalProject.getProjectId().equals(item.getProject().getProjectId());
          }
        });
      } else {
        result.add(new FilterConditionChecker<BuildTypeOrTemplate>() {
          public boolean isIncluded(@NotNull final BuildTypeOrTemplate item) {
            return projects.contains(item.getProject());
          }
        });
      }
    }

    final String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null) {
      @NotNull final SProject parentProject = myProjectFinder.getItem(affectedProjectDimension);
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
      final List<SBuildAgent> agents = myAgentFinder.getItems(compatibleAagentLocator).myEntries;
      final List<SAgentType> agentTypes = CollectionsUtil.convertCollection(agents, new Converter<SAgentType, SBuildAgent>() {
        @Override
        public SAgentType createFrom(@NotNull final SBuildAgent source) {
          return AgentFinder.getAgentType(source);
        }
      });
      result.add(new FilterConditionChecker<BuildTypeOrTemplate>() {
        public boolean isIncluded(@NotNull final BuildTypeOrTemplate item) {
          if (item.getBuildType() == null) return false;
          for (SAgentType agentType : agentTypes) {
            if (AgentFinder.canActuallyRun(agentType, item.getBuildType())) return true;
          }
          return false;
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
          final boolean canView = !BuildType.shouldRestrictSettingsViewing(item.get(), myPermissionChecker);
          if (!canView) {
            LOG.debug("While filtering build types by " + PARAMETER + " user does not have enough permissions to see settings. Excluding build type: " + item.describe(false));
            return false;
          }
          return parameterCondition.matches(item.get());
        }
      });
    }

    final Boolean template = locator.getSingleDimensionValueAsBoolean(TEMPLATE_FLAG_DIMENSION_NAME);
    if (template != null) {
      result.add(new FilterConditionChecker<BuildTypeOrTemplate>() {
        public boolean isIncluded(@NotNull final BuildTypeOrTemplate item) {
          return FilterUtil.isIncludedByBooleanFilter(template, item.isTemplate());
        }
      });
    }

    final String filterBuilds = locator.getSingleDimensionValue(FILTER_BUILDS); //experimental
    if (filterBuilds != null) {
      final Locator filterBuildsLocator = new Locator(filterBuilds, "search", "match");  // support for conditions like "in which the last build is successful"
      final String search = filterBuildsLocator.getSingleDimensionValue("search");
      final String match = filterBuildsLocator.getSingleDimensionValue("match");
      if (search != null) {
        result.add(new FilterConditionChecker<BuildTypeOrTemplate>() {
          @Override
          public boolean isIncluded(@NotNull final BuildTypeOrTemplate item) {
            if (item.getBuildType() == null) return false;
            final BuildPromotionFinder buildFinder = myServiceLocator.getSingletonService(BuildPromotionFinder.class);
            final String patchedSearch = Locator.setDimensionIfNotPresent(search, PagerData.COUNT, "1");
            final List<BuildPromotion> buildPromotions = buildFinder.getBuildPromotions(item.getBuildType(), patchedSearch).myEntries;
            if (buildPromotions.isEmpty()) {
              return false;
            }
            if (match == null) {
              return buildPromotions.size() > 0;
            }

            final ItemFilter<BuildPromotion> filter = buildFinder.getFilter(match);
            for (BuildPromotion buildPromotion : buildPromotions) {
              if (!filter.isIncluded(buildPromotion)) return false;
            }
            return true;
          }
        });
      }
    }

    final String snapshotDependencies = locator.getSingleDimensionValue(SNAPSHOT_DEPENDENCY);
    if (snapshotDependencies != null) {
      final GraphFinder<BuildTypeOrTemplate> graphFinder = new GraphFinder<BuildTypeOrTemplate>(this, new SnapshotDepsTraverser(myPermissionChecker));
      final List<BuildTypeOrTemplate> boundingList = graphFinder.getItems(snapshotDependencies).myEntries;
      result.add(new FilterConditionChecker<BuildTypeOrTemplate>() {
        public boolean isIncluded(@NotNull final BuildTypeOrTemplate item) {
          return boundingList.contains(item);
        }
      });
    }

    final String templateLocator = locator.getSingleDimensionValue(TEMPLATE_DIMENSION_NAME);
    if (templateLocator != null) {
      try {
        final BuildTypeTemplate buildTemplate = getBuildTemplate(null, templateLocator, true); //only this can throw exceptions caught later
        final List<BuildTypeOrTemplate> boundingList = BuildTypes.fromBuildTypes(buildTemplate.getUsages());
        result.add(new FilterConditionChecker<BuildTypeOrTemplate>() {
          public boolean isIncluded(@NotNull final BuildTypeOrTemplate item) {
            return boundingList.contains(item);
          }
        });
      } catch (NotFoundException e) {
        //legacy support for boolean template
        Boolean legacyTemplateFlag = null;
        try {
          legacyTemplateFlag = locator.getSingleDimensionValueAsBoolean(TEMPLATE_DIMENSION_NAME);
        } catch (LocatorProcessException eNested) {
          //not a boolean, throw original error
          throw new NotFoundException("No templates found by locator '" + templateLocator + "' specified in '" + TEMPLATE_DIMENSION_NAME + "' dimension : " + e.getMessage());
        }
        //legacy request detected
        if (legacyTemplateFlag != null) {
          final boolean legacyTemplateFlagFinal = legacyTemplateFlag;
          result.add(new FilterConditionChecker<BuildTypeOrTemplate>() {
            public boolean isIncluded(@NotNull final BuildTypeOrTemplate item) {
              return FilterUtil.isIncludedByBooleanFilter(legacyTemplateFlagFinal, item.isTemplate());
            }
          });
        }
      } catch (BadRequestException e) {
        throw new BadRequestException(
          "Error while searching for templates by locator '" + templateLocator + "' specified in '" + TEMPLATE_DIMENSION_NAME + "' dimension : " + e.getMessage(), e);
      }
    }

    if (locator.isUnused(VCS_ROOT_DIMENSION)) {
      final String vcsRoot = locator.getSingleDimensionValue(VCS_ROOT_DIMENSION);
      if (vcsRoot != null) {
        final Set<SVcsRoot> vcsRoots = new HashSet<SVcsRoot>(myServiceLocator.getSingletonService(VcsRootFinder.class).getItems(vcsRoot).myEntries);
        result.add(new FilterConditionChecker<BuildTypeOrTemplate>() {
          public boolean isIncluded(@NotNull final BuildTypeOrTemplate item) {
            for (VcsRootInstanceEntry vcsRootInstanceEntry : item.getVcsRootInstanceEntries()) {
              if (vcsRoots.contains(vcsRootInstanceEntry.getVcsRoot().getParent())) return true;
            }
            return false;
          }
        });
      }
    }

    if (locator.isUnused(VCS_ROOT_INSTANCE_DIMENSION)) {
      final String vcsRootInstance = locator.getSingleDimensionValue(VCS_ROOT_INSTANCE_DIMENSION);
      if (vcsRootInstance != null) {
        final Set<jetbrains.buildServer.vcs.VcsRootInstance> vcsRootInstances =
          new HashSet<jetbrains.buildServer.vcs.VcsRootInstance>(myServiceLocator.getSingletonService(VcsRootInstanceFinder.class).getItems(vcsRootInstance).myEntries);
        result.add(new FilterConditionChecker<BuildTypeOrTemplate>() {
          public boolean isIncluded(@NotNull final BuildTypeOrTemplate item) {
            for (VcsRootInstanceEntry vcsRootInstanceEntry : item.getVcsRootInstanceEntries()) {
              if (vcsRootInstances.contains(vcsRootInstanceEntry.getVcsRoot())) return true;
            }
            return false;
          }
        });
      }
    }
    return result;
  }

  @NotNull
  @Override
  public ItemHolder<BuildTypeOrTemplate> getPrefilteredItems(@NotNull final Locator locator) {
    //this should be the first one as the order returned here is important!
    final String selectedForUser = locator.getSingleDimensionValue(DIMENSION_SELECTED);
    if (selectedForUser != null) {
      final SUser user = myServiceLocator.getSingletonService(UserFinder.class).getItem(selectedForUser);
      List<SProject> projects = null;
      final String projectLocator = locator.getSingleDimensionValue(DIMENSION_PROJECT);
      if (projectLocator != null) {
        projects = myProjectFinder.getItems(projectLocator).myEntries;
      }
      return getItemHolder(getBuildTypesSelectedForUser(user, projects));
    }

    final String snapshotDependencies = locator.getSingleDimensionValue(SNAPSHOT_DEPENDENCY);
    if (snapshotDependencies != null) {
      final GraphFinder<BuildTypeOrTemplate> graphFinder = new GraphFinder<BuildTypeOrTemplate>(this, new SnapshotDepsTraverser(myPermissionChecker));
      return getItemHolder(graphFinder.getItems(snapshotDependencies).myEntries);
    }

    final String vcsRoot = locator.getSingleDimensionValue(VCS_ROOT_DIMENSION);
    if (vcsRoot != null) {
      final Set<SVcsRoot> vcsRoots = new HashSet<SVcsRoot>(myServiceLocator.getSingletonService(VcsRootFinder.class).getItems(vcsRoot).myEntries);
      final VcsManager vcsManager = myServiceLocator.getSingletonService(VcsManager.class);
      final LinkedHashSet<BuildTypeOrTemplate> result = new LinkedHashSet<BuildTypeOrTemplate>();
      for (SVcsRoot root : vcsRoots) {
        //can optimize more by checking template flag here
        result.addAll(BuildTypes.fromBuildTypes(root.getUsagesInConfigurations()));
        result.addAll(BuildTypes.fromTemplates(vcsManager.getAllTemplateUsages(root)));
      }
      //order of the result is not well defined here, might need to resort...
      return getItemHolder(result);
    }

    final String vcsRootInstance = locator.getSingleDimensionValue(VCS_ROOT_INSTANCE_DIMENSION);
    if (vcsRootInstance != null) {
      final Set<jetbrains.buildServer.vcs.VcsRootInstance> vcsRootInstances =
        new HashSet<jetbrains.buildServer.vcs.VcsRootInstance>(myServiceLocator.getSingletonService(VcsRootInstanceFinder.class).getItems(vcsRootInstance).myEntries);
      final List<SBuildType> result = new ArrayList<SBuildType>();
      for (jetbrains.buildServer.vcs.VcsRootInstance root : vcsRootInstances) {
        result.addAll(root.getUsages().keySet());
        //cannot find templates by instances
      }
      //order of the result is not well defined here, might need to resort...
      return getItemHolder(BuildTypes.fromBuildTypes(result));
    }

    List<SProject> projects = null;
    final String projectLocator = locator.getSingleDimensionValue(DIMENSION_PROJECT);
    if (projectLocator != null) {
      projects = myProjectFinder.getItems(projectLocator).myEntries;
    }

    SProject affectedProject = null;
    final String affectedProjectLocator = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectLocator != null) {
      affectedProject = myProjectFinder.getItem(affectedProjectLocator);
    }

    final String templateLocator = locator.getSingleDimensionValue(TEMPLATE_DIMENSION_NAME);
    if (templateLocator != null) {
      final BuildTypeTemplate buildTemplate;
      try {
        buildTemplate = getBuildTemplate(null, templateLocator, true);
      } catch (NotFoundException e) {
        throw new NotFoundException("No templates found by locator '" + templateLocator + "' specified in '" + TEMPLATE_DIMENSION_NAME + "' dimension : " + e.getMessage());
      } catch (BadRequestException e) {
        throw new BadRequestException(
          "Error while searching for templates by locator '" + templateLocator + "' specified in '" + TEMPLATE_DIMENSION_NAME + "' dimension : " + e.getMessage(), e);
      }
      return getItemHolder(BuildTypes.fromBuildTypes(buildTemplate.getUsages()));
    }

    List<BuildTypeOrTemplate> result = new ArrayList<BuildTypeOrTemplate>();
    Boolean template = locator.getSingleDimensionValueAsBoolean(TEMPLATE_FLAG_DIMENSION_NAME);
    if (template == null || !template) {
      if (projects != null) {
        result.addAll(getBuildTypes(projects));
      } else if (affectedProject != null) {
        result.addAll(BuildTypes.fromBuildTypes(affectedProject.getBuildTypes()));
      } else {
        result.addAll(BuildTypes.fromBuildTypes(myProjectManager.getAllBuildTypes()));
      }
    }
    if (template == null || template) {
      if (projects != null) {
        result.addAll(getTemplates(projects));
      } else if (affectedProject != null) {
        result.addAll(BuildTypes.fromTemplates(affectedProject.getBuildTypeTemplates()));
      } else {
        result.addAll(BuildTypes.fromTemplates(myProjectManager.getAllTemplates()));
      }
    }

    return getItemHolder(result);
  }

  private Collection<BuildTypeOrTemplate> getBuildTypes(final List<SProject> projects) {
    final ArrayList<BuildTypeOrTemplate> result = new ArrayList<BuildTypeOrTemplate>();
    for (SProject project : projects) {
      result.addAll(BuildTypes.fromBuildTypes(project.getOwnBuildTypes()));
    }
    return result;
  }

  private Collection<BuildTypeOrTemplate> getTemplates(final List<SProject> projects) {
    final ArrayList<BuildTypeOrTemplate> result = new ArrayList<BuildTypeOrTemplate>();
    for (SProject project : projects) {
      result.addAll(BuildTypes.fromTemplates(project.getOwnBuildTypeTemplates()));
    }
    return result;
  }

  @NotNull
  public BuildTypeOrTemplate getBuildTypeOrTemplate(@Nullable final SProject project, @Nullable final String buildTypeLocator, final boolean checkViewSettingsPermission) {
    final BuildTypeOrTemplate result;
    if (project == null) {
      result = getItem(buildTypeLocator);
    } else {
      final Locator locator = buildTypeLocator != null ? new Locator(buildTypeLocator) : null;
      if (locator == null || !locator.isSingleValue()) {
        result = getItem(Locator.setDimensionIfNotPresent(buildTypeLocator, DIMENSION_PROJECT, ProjectFinder.getLocator(project)));
      } else {
        // single value locator
        result = getItem(buildTypeLocator);
        if (!result.getProject().getProjectId().equals(project.getProjectId())) {
          throw new BadRequestException("Found " + LogUtil.describe(result) + " but it does not belong to project " + LogUtil.describe(project) + ".");
        }
      }
    }

    if (checkViewSettingsPermission) {
      check(result.get(), myPermissionChecker);
    }
    return result;
  }

  public static void check(@NotNull BuildTypeSettings buildType, @NotNull final PermissionChecker permissionChecker) {
    if (BuildType.shouldRestrictSettingsViewing(buildType, permissionChecker)) {
      throw new AuthorizationFailedException(
        "User does not have '" + Permission.VIEW_BUILD_CONFIGURATION_SETTINGS.getName() + "' permission in project " + buildType.getProject().describe(false));
    }
  }

  private String getName(final Boolean template) {
    if (template == null) {
      return "build type nor template";
    }
    return template ? "template" : "build type";
  }

  @NotNull
  public SBuildType getBuildType(@Nullable final SProject project, @Nullable final String buildTypeLocator, final boolean checkViewSettingsPermission) {
    final BuildTypeOrTemplate buildTypeOrTemplate = getBuildTypeOrTemplate(project, buildTypeLocator, checkViewSettingsPermission);
    if (buildTypeOrTemplate.getBuildType() != null) {
      return buildTypeOrTemplate.getBuildType();
    }
    throw new NotFoundException("No build type is found by locator '" + buildTypeLocator + "'. Template is found instead.");
  }

  @NotNull
  public BuildTypeTemplate getBuildTemplate(@Nullable final SProject project, @Nullable final String buildTypeLocator, final boolean checkViewSettingsPermission) {
    final BuildTypeOrTemplate buildTypeOrTemplate = getBuildTypeOrTemplate(project, buildTypeLocator, checkViewSettingsPermission);
    if (buildTypeOrTemplate.getTemplate() != null) {
      return buildTypeOrTemplate.getTemplate();
    }
    throw new BadRequestException("No build type template found by locator '" + buildTypeLocator + "'. Build type is found instead.");
  }

  @NotNull
  public List<SBuildType> getBuildTypes(@Nullable final SProject project, @Nullable final String buildTypeLocator) {
    final PagedSearchResult<BuildTypeOrTemplate> items = getBuildTypesPaged(project, buildTypeLocator, true);
    return CollectionsUtil.convertCollection(items.myEntries, new Converter<SBuildType, BuildTypeOrTemplate>() {
      public SBuildType createFrom(@NotNull final BuildTypeOrTemplate source) {
        if (project != null && !source.getProject().equals(project)) {
          throw new BadRequestException("Found " + LogUtil.describe(source.getBuildType()) + " but it does not belong to project " + LogUtil.describe(project) + ".");
        }
        return source.getBuildType();
      }
    });
  }

  @NotNull
  public PagedSearchResult<BuildTypeOrTemplate> getBuildTypesPaged(final @Nullable SProject project, final @Nullable String buildTypeLocator, final boolean buildType) {
    if (buildTypeLocator != null && (new Locator(buildTypeLocator)).isSingleValue()){
      return getItems(buildTypeLocator);
    }

    String actualLocator = Locator.setDimensionIfNotPresent(buildTypeLocator, TEMPLATE_FLAG_DIMENSION_NAME, String.valueOf(!buildType));

    if (project != null) {
      actualLocator = Locator.setDimensionIfNotPresent(actualLocator, DIMENSION_PROJECT, ProjectFinder.getLocator(project));
    }

    return getItems(actualLocator);
  }

  @Nullable
  public SBuildType getBuildTypeIfNotNull(@Nullable final String buildTypeLocator) {
    return buildTypeLocator == null ? null : getBuildType(null, buildTypeLocator, false);
  }

  @Nullable
  public SBuildType deriveBuildTypeFromLocator(@Nullable SBuildType contextBuildType, @Nullable final String buildTypeLocator) {
    if (buildTypeLocator != null) {
      final SBuildType buildTypeFromLocator = getBuildType(null, buildTypeLocator, false);
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

  @Nullable
  private BuildTypeOrTemplate findBuildTypebyName(@NotNull final String name, @Nullable List<SProject> projects, final Boolean isTemplate) {
    if (projects == null) {
      projects = myProjectManager.getProjects();
    }
    BuildTypeOrTemplate firstFound = null;
    for (SProject project : projects) {
      final BuildTypeOrTemplate found = getOwnBuildTypeOrTemplateByName(project, name, isTemplate);
      if (found != null) {
        if (firstFound != null) {
          String message = "Several matching ";
          if (isTemplate == null) {
            message += "build types/templates";
          } else if (isTemplate) {
            message += "templates";
          } else {
            message += "build types";
          }
          throw new BadRequestException(message + " found by name for single value locator '" + name + "'. Try another locator");
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
  private BuildTypeOrTemplate findBuildTypeOrTemplateByUuid(@NotNull final String uuid, @Nullable final Boolean isTemplate) {
    if (isTemplate == null || !isTemplate) {
      SBuildType buildType = myProjectManager.findBuildTypeByConfigId(uuid);
      if (buildType != null) {
        return new BuildTypeOrTemplate(buildType);
      }
    }
    if (isTemplate == null || isTemplate) {
      final BuildTypeTemplate buildTypeTemplate = myProjectManager.findBuildTypeTemplateByConfigId(uuid);
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
      try {
        result.add(getBuildTypeByInternalId(buildTypeId, projectManager));
      } catch (NotFoundException e) {
        LOG.debug("No build type is found by internal id '" + buildTypeId + "', ignoring");
      }
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

  private class SnapshotDepsTraverser implements GraphFinder.Traverser<BuildTypeOrTemplate> {
    @NotNull private final PermissionChecker myPermissionChecker;

    public SnapshotDepsTraverser(@NotNull final PermissionChecker permissionChecker) {
      myPermissionChecker = permissionChecker;
    }

    @NotNull
    public GraphFinder.LinkRetriever<BuildTypeOrTemplate> getChildren() {
      return new GraphFinder.LinkRetriever<BuildTypeOrTemplate>() {
        @NotNull
        public List<BuildTypeOrTemplate> getLinked(@NotNull final BuildTypeOrTemplate item) {
          if (BuildType.shouldRestrictSettingsViewing(item.get(), myPermissionChecker)){
            return new ArrayList<BuildTypeOrTemplate>(); //conceal dependencies
          }
          return getNotNullBuildTypes(item.get().getDependencies());
        }
      };
    }

    @NotNull
    public GraphFinder.LinkRetriever<BuildTypeOrTemplate> getParents() {
      return new GraphFinder.LinkRetriever<BuildTypeOrTemplate>() {
        @NotNull
        public List<BuildTypeOrTemplate> getLinked(@NotNull final BuildTypeOrTemplate item) {
          final SBuildType buildType = item.getBuildType();
          if (buildType == null){
            return new ArrayList<BuildTypeOrTemplate>(); //template should have no dependencies on it
          }
          return getDependingOn(buildType, myPermissionChecker);
        }
      };
    }
  }

  @NotNull
  private List<BuildTypeOrTemplate> getDependingOn(@NotNull final SBuildType buildType, @NotNull final PermissionChecker permissionChecker) {
    final Set<String> internalIds = ((BuildTypeEx)buildType).getDependedOnMe().keySet(); //TeamCity open API issue
    final ArrayList<BuildTypeOrTemplate> result = new ArrayList<BuildTypeOrTemplate>();
    for (String internalId : internalIds) {
      final SBuildType buildTypeById = myProjectManager.findBuildTypeById(internalId);
      if (buildTypeById != null && !BuildType.shouldRestrictSettingsViewing(buildTypeById, permissionChecker)){ //conceal dependencies
        result.add(new BuildTypeOrTemplate(buildTypeById));
      }
    }
    return result;
  }

  @NotNull
  private List<BuildTypeOrTemplate> getNotNullBuildTypes(@NotNull final List<Dependency> dependencies) {
    final ArrayList<BuildTypeOrTemplate> result = new ArrayList<BuildTypeOrTemplate>();
    for (Dependency dependency : dependencies) {
      final SBuildType dependOn = dependency.getDependOn();
      if (dependOn != null) {
        result.add(new BuildTypeOrTemplate(dependOn));
      }
      //todo: else expose this somehow
    }
    return result;
  }

  @NotNull
  public List<BuildTypeOrTemplate> getBuildTypesSelectedForUser(@NotNull final SUser user, @Nullable final List<SProject> projects) {
    Collection<SProject> selectedProjects = projects;
    if (selectedProjects == null){
      selectedProjects = myProjectFinder.getSelectedProjects(user);
    }
    final List<BuildTypeOrTemplate> result = new ArrayList<BuildTypeOrTemplate>();
    for (SProject project : selectedProjects) {
      result.addAll(CollectionsUtil.convertCollection(user.getOrderedBuildTypes(project), new Converter<BuildTypeOrTemplate, SBuildType>() {
        public BuildTypeOrTemplate createFrom(@NotNull final SBuildType source) {
          return new BuildTypeOrTemplate(source);
        }
      }));
    }
    return result;
  }
}
