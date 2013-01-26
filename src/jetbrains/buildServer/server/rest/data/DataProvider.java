/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.groups.UserGroupManager;
import jetbrains.buildServer.plugins.PluginManager;
import jetbrains.buildServer.plugins.bean.PluginInfo;
import jetbrains.buildServer.plugins.bean.ServerPluginInfo;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Constants;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.auth.*;
import jetbrains.buildServer.serverSide.dependency.Dependency;
import jetbrains.buildServer.serverSide.statistics.ValueProviderRegistry;
import jetbrains.buildServer.serverSide.statistics.build.BuildDataStorage;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 28.03.2009
 */
public class DataProvider {
  private static final Logger LOG = Logger.getInstance(DataProvider.class.getName());
  public static final String TEMPLATE_ID_PREFIX = "template:";

  @NotNull private final SBuildServer myServer;
  @NotNull private final BuildHistory myBuildHistory;
  @NotNull private final UserModel myUserModel;
  @NotNull private final RolesManager myRolesManager;
  @NotNull private final UserGroupManager myGroupManager;
  @NotNull private final VcsManager myVcsManager;
  @NotNull private final BuildAgentManager myAgentManager;
  @NotNull private final WebLinks myWebLinks;
  @NotNull private final ServerPluginInfo myPluginInfo;
  @NotNull private final ServerListener myServerListener;
  @NotNull private final SecurityContext mySecurityContext;
  @NotNull private final SourceVersionProvider mySourceVersionProvider;
  @NotNull private final PluginManager myPluginManager;
  @NotNull private final RunningBuildsManager myRunningBuildsManager;
  @NotNull private final ValueProviderRegistry myValueProviderRegistry;
  @NotNull private final BuildDataStorage myBuildDataStorage;
  @NotNull private final BuildPromotionManager myPromotionManager;

  public DataProvider(@NotNull final SBuildServer myServer,
                      @NotNull final BuildHistory myBuildHistory,
                      @NotNull final UserModel userModel,
                      @NotNull final RolesManager rolesManager,
                      @NotNull final UserGroupManager groupManager,
                      @NotNull final VcsManager vcsManager,
                      @NotNull final BuildAgentManager agentManager,
                      @NotNull final WebLinks webLinks,
                      @NotNull final ServerPluginInfo pluginInfo,
                      @NotNull final ServerListener serverListener,
                      @NotNull final SecurityContext securityContext,
                      @NotNull final SourceVersionProvider sourceVersionProvider,
                      @NotNull final PluginManager pluginManager,
                      @NotNull final RunningBuildsManager runningBuildsManager,
                      @NotNull final ValueProviderRegistry valueProviderRegistry,
                      @NotNull final BuildDataStorage buildDataStorage,
                      @NotNull final BuildPromotionManager promotionManager
  ) {
    this.myServer = myServer;
    this.myBuildHistory = myBuildHistory;
    this.myUserModel = userModel;
    myRolesManager = rolesManager;
    myGroupManager = groupManager;
    myVcsManager = vcsManager;
    myAgentManager = agentManager;
    myWebLinks = webLinks;
    myPluginInfo = pluginInfo;
    myServerListener = serverListener;
    mySecurityContext = securityContext;
    mySourceVersionProvider = sourceVersionProvider;
    myPluginManager = pluginManager;
    myRunningBuildsManager = runningBuildsManager;
    myValueProviderRegistry = valueProviderRegistry;
    myBuildDataStorage = buildDataStorage;
    myPromotionManager = promotionManager;
  }

  public Builds getBuildsForRequest(final SBuildType buildType,
                                    final String status,
                                    final String userLocator,
                                    final boolean includePersonal,
                                    final boolean includeCanceled,
                                    final boolean onlyPinned,
                                    final List<String> tags,
                                    final String agentName,
                                    final String sinceBuildLocator,
                                    final String sinceDate,
                                    final Long start,
                                    final Integer count,
                                    final Locator locator,
                                    final UriInfo uriInfo,
                                    final HttpServletRequest request, final ApiUrlBuilder apiUrlBuilder) {
    BuildsFilter buildsFilter;
    if (locator != null) {
      buildsFilter = getBuildsFilterByLocator(buildType, locator);
      locator.checkLocatorFullyProcessed();
    } else {
      // preserve 5.0 logic for personal/canceled/pinned builds
      buildsFilter = new GenericBuildsFilter(buildType,
                                      status, null,
                                      getUserIfNotNull(userLocator),
                                      includePersonal ? null : false, includeCanceled ? null : false,
                                      false, onlyPinned ? true : null, tags, null, agentName,
                                      null, getRangeLimit(buildType, sinceBuildLocator, parseDate(sinceDate)),
                                      null,
                                      start, count, null);
    }

    // override start and count if set in URL query parameters
    if (start != null){
      buildsFilter.setStart(start);
    }

    if (count != null){
      buildsFilter.setCount(count);
    }else{
      final Integer c = buildsFilter.getCount();
      if (c != null){
        buildsFilter.setCount(c != -1 ? c : null);
      }else{
        buildsFilter.setCount(jetbrains.buildServer.server.rest.request.Constants.DEFAULT_PAGE_ITEMS_COUNT_INT);
      }
    }

    final List<SBuild> buildsList = this.getBuilds(buildsFilter);
    return new Builds(buildsList, this, new PagerData(uriInfo.getRequestUriBuilder(), request, buildsFilter.getStart(), buildsFilter.getCount(), buildsList.size()),
                      apiUrlBuilder);
  }

  @Nullable
  public String getFieldValue(@NotNull final SBuild build, @Nullable final String field) {
    if ("number".equals(field)) {
      return build.getBuildNumber();
    } else if ("status".equals(field)) {
      return build.getStatusDescriptor().getStatus().getText();
    } else if ("id".equals(field)) {
      return (new Long(build.getBuildId())).toString();
    } else if ("startDate".equals(field)) {
      return Util.formatTime(build.getStartDate());
    } else if ("finishDate".equals(field)) {
      return Util.formatTime(build.getFinishDate());
    } else if ("buildTypeId".equals(field)) {
      return (build.getBuildTypeId());
    } else if ("branchName".equals(field)) {
      Branch branch = build.getBranch();
      return branch == null ? "" : branch.getDisplayName();
    } else if ("branch".equals(field)) {
      Branch branch = build.getBranch();
      return branch == null ? "" : branch.getName();
    } else if ("defaultBranch".equals(field)) {
      Branch branch = build.getBranch();
      return branch == null ? "" : String.valueOf(branch.isDefaultBranch());
    } else if ("unspecifiedBranch".equals(field)) {
      Branch branch = build.getBranch();
      return branch == null ? "" : String.valueOf(Branch.UNSPECIFIED_BRANCH_NAME.equals(branch.getName()));
    } else if ("promotionId".equals(field)) { //this is not exposed in any other way
      return (String.valueOf(build.getBuildPromotion().getId()));
    }
    throw new NotFoundException("Field '" + field + "' is not supported.");
  }

  @Nullable
  public String getServerFieldValue(@Nullable final String field) {
    // Note: "build", "majorVersion" and "minorVersion" for backward compatibility.
    if ("version".equals(field)) {
      return myServer.getFullServerVersion();
    } else if ("buildNumber".equals(field) || "build".equals(field)) {
      return myServer.getBuildNumber();
    } else if ("versionMajor".equals(field) || "majorVersion".equals(field)) {
      return Byte.toString(myServer.getServerMajorVersion());
    } else if ("versionMinor".equals(field) || "minorVersion".equals(field)) {
      return Byte.toString(myServer.getServerMinorVersion());
    } else if ("startTime".equals(field)) {
      return Util.formatTime(getServerStartTime());
    } else if ("currentTime".equals(field)) {
      return Util.formatTime(new Date());
    }
    throw new NotFoundException("Field '" + field + "' is not supported. Supported are: version, versionMajor, versionMinor, buildNumber, startTime, currentTime");
  }

  /**
   * Supported build locators:
   *  213 - build with id=213
   *  213 when buildType is specified - build in the specified buildType with build number 213
   *  id:213 - build with id=213
   *  buildType:bt37 - specify Build Configuration by internal id. If specified, other locator parts should select the build
   *  number:213 when buildType is specified - build in the specified buildType with build number 213
   *  status:SUCCESS when buildType is specified - last build with the specified status in the specified buildType
   */
  @NotNull
  public SBuild getBuild(@Nullable SBuildType buildType, @Nullable final String buildLocator) {
    if (StringUtil.isEmpty(buildLocator)) {
      throw new BadRequestException("Empty build locator is not supported.");
    }

    final Locator locator = new Locator(buildLocator);

    if (locator.isSingleValue()) {
      if (buildType == null) {
        // no dimensions found and no build type, assume it's build id

        @SuppressWarnings("ConstantConditions") SBuild build = myServer.findBuildInstanceById(locator.getSingleValueAsLong()); //todo: report non-number more user-friendly
        if (build == null) {
          throw new BadRequestException("Cannot find build by id '" + locator.getSingleValue() + "'.");
        }
        return build;
      }
      // no dimensions found and build type is specified, assume it's build number
      @SuppressWarnings("ConstantConditions") SBuild build = myServer.findBuildInstanceByBuildNumber(buildType.getBuildTypeId(), buildLocator);
      if (build == null) {
        throw new NotFoundException("No build can be found by number '" + buildLocator + "' in build configuration " + buildType + ".");
      }
      return build;
    }

    String buildTypeLocator = locator.getSingleDimensionValue("buildType");
    buildType = deriveBuildTypeFromLocator(buildType, buildTypeLocator);

    Long id = locator.getSingleDimensionValueAsLong("id");
    if (id != null) {
      SBuild build = myServer.findBuildInstanceById(id);
      if (build == null) {
        throw new NotFoundException("No build can be found by id '" + id + "'.");
      }
      if (buildType != null && !buildType.getBuildTypeId().equals(build.getBuildTypeId())) {
        throw new NotFoundException("No build can be found by id '" + locator.getSingleDimensionValue("id") + "' in build type '" + buildType + "'.");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Build locator '" + buildLocator + "' has 'id' dimension and others. Others are ignored.");
      }
      return build;
    }

    String number = locator.getSingleDimensionValue("number");
    if (number != null) {
      if (buildType != null) {
        SBuild build = myServer.findBuildInstanceByBuildNumber(buildType.getBuildTypeId(), number);
        if (build == null) {
          throw new NotFoundException("No build can be found by number '" + number + "' in build configuration " + buildType + ".");
        }
        if (locator.getDimensionsCount() > 1) {
          LOG.info("Build locator '" + buildLocator + "' has 'number' dimension and others. Others are ignored.");
        }
        return build;
      }else{
        throw new NotFoundException("Build number is specified without build configuration. Cannot find build by build number only.");
      }
    }
    {
      Long promotionId = locator.getSingleDimensionValueAsLong("promotionId");
      if (promotionId != null) {
        final BuildPromotion promotion = myPromotionManager.findPromotionById(promotionId);
        if (promotion == null) {
          throw new NotFoundException("No promotion can be found by promotionId '" + promotionId + "'.");
        }
        SBuild build = promotion.getAssociatedBuild();
        if (build == null) {
          throw new NotFoundException("No associated build can be found for promotion with id '" + promotionId + "'.");
        }
        if (buildType != null && !buildType.getBuildTypeId().equals(build.getBuildTypeId())) {
          throw new NotFoundException("No build can be found by promotionId '" + promotionId + "' in build type '" + buildType + "'.");
        }
        if (locator.getDimensionsCount() > 1) {
          LOG.info("Build locator '" + buildLocator + "' has 'promotionId' dimension and others. Others are ignored.");
        }
        return build;
      }
    }
    final BuildsFilter buildsFilter = getBuildsFilterByLocator(buildType, locator);
    buildsFilter.setCount(1);

    locator.checkLocatorFullyProcessed();
    
    final List<SBuild> filteredBuilds = getBuilds(buildsFilter);
    if (filteredBuilds.size() == 0){
      throw new NotFoundException("No build found by filter: " + buildsFilter.toString() + ".");
    }

    if (filteredBuilds.size() == 1){
      return filteredBuilds.get(0);
    }
    //todo: check for unknown dimension names in all the returns

    throw new BadRequestException("Build locator '" + buildLocator + "' is not supported (" + filteredBuilds.size() + " builds found)");
  }

  @Nullable
  private SBuildType deriveBuildTypeFromLocator(@Nullable SBuildType contextBuildType, @Nullable final String buildTypeLocator) {
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

  @NotNull
  public BuildsFilter getBuildsFilterByLocator(@Nullable final SBuildType buildType, @NotNull final Locator locator) {
    //todo: report unknown locator dimensions
    final SBuildType actualBuildType = deriveBuildTypeFromLocator(buildType, locator.getSingleDimensionValue("buildType"));

    final String userLocator = locator.getSingleDimensionValue("user");
    final String tagsString = locator.getSingleDimensionValue("tags");
    final String singleTagString = locator.getSingleDimensionValue("tag");
    if (tagsString != null && singleTagString != null){
      throw new BadRequestException("Both 'tags' and 'tag' dimensions specified. Only one can be present.");
    }
    List<String> tagsList = null;
    if (singleTagString != null) {
      tagsList = Collections.singletonList(singleTagString);
    }else if (tagsString != null) {
      tagsList = Arrays.asList(tagsString.split(","));
    }

    final Long count = locator.getSingleDimensionValueAsLong("count");

    Locator branchLocator = null;
    final String branchLocatorValue = locator.getSingleDimensionValue("branch");
    if (!StringUtil.isEmpty(branchLocatorValue)) {
      try {
        branchLocator = new Locator(branchLocatorValue);
      } catch (LocatorProcessException e) {
        throw new LocatorProcessException("Invalid sub-locator 'branch':" + e.getMessage());
      }
    }
    return new GenericBuildsFilter(actualBuildType,
                            locator.getSingleDimensionValue("status"),
                            locator.getSingleDimensionValue("number"),
                            userLocator != null ? getUser(userLocator) : null,
                            locator.getSingleDimensionValueAsBoolean("personal"),
                            locator.getSingleDimensionValueAsBoolean("canceled"),
                            locator.getSingleDimensionValueAsBoolean("running", false),
                            locator.getSingleDimensionValueAsBoolean("pinned"),
                            tagsList,
                            branchLocator,
                            //todo: support agent locator here
                            locator.getSingleDimensionValue("agentName"),
                            ParameterCondition.create(locator.getSingleDimensionValue("property")),
                            getRangeLimit(actualBuildType, locator.getSingleDimensionValue("sinceBuild"),
                                          parseDate(locator.getSingleDimensionValue("sinceDate"))),
                            getRangeLimit(actualBuildType, locator.getSingleDimensionValue("untilBuild"),
                                          parseDate(locator.getSingleDimensionValue("untilDate"))),
                            locator.getSingleDimensionValueAsLong("start"),
                            count == null?null:count.intValue(),
                            locator.getSingleDimensionValueAsLong("lookupLimit")
    );
  }

  @NotNull
  public BuildTypeTemplate getBuildTemplate(@Nullable final SProject project, @Nullable final String buildTypeLocator) {
    final BuildTypeOrTemplate buildTypeOrTemplate = getBuildTypeOrTemplate(project, buildTypeLocator);
    if (buildTypeOrTemplate.isBuildType()){
      throw new BadRequestException("Could not find template by locator '" + buildTypeLocator + "'. Build type found instead.");
    }
    return buildTypeOrTemplate.getTemplate();
  }

  @NotNull
  public BuildTypeOrTemplate getBuildTypeOrTemplate(@Nullable final SProject project, @Nullable final String buildTypeLocator) {
    if (StringUtil.isEmpty(buildTypeLocator)) {
      throw new BadRequestException("Empty build type locator is not supported.");
    }
    assert buildTypeLocator != null;

    final Locator locator = new Locator(buildTypeLocator);
    if (locator.isSingleValue()) {
      // no dimensions found
      if (project != null) {
        // assume it's a name
        return findBuildTypeByName(project, buildTypeLocator);
      } else {
        //assume it's id
        return findBuildTypeOrTemplateByGeneralId(buildTypeLocator);
      }
    }

    String id = locator.getSingleDimensionValue("id");
    if (!StringUtil.isEmpty(id)) {
      assert id != null;
      BuildTypeOrTemplate buildType = findBuildTypeOrTemplateByGeneralId(id);
      if (project != null && !buildType.getProject().equals(project)) {
        throw new NotFoundException(buildType.getText() + " with id '" + id + "' does not belong to project " + project + ".");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Build type locator '" + buildTypeLocator + "' has 'id' dimension and others. Others are ignored.");
      }
      return buildType;
    }

    String name = locator.getSingleDimensionValue("name");
    if (name != null) {
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Build type locator '" + buildTypeLocator + "' has 'name' dimension and others. Others are ignored.");
      }
      return findBuildTypeByName(project, name);
    }
    throw new BadRequestException("Build type locator '" + buildTypeLocator + "' is not supported.");
  }

  @NotNull
  public SBuildType getBuildType(@Nullable final SProject project, @Nullable final String buildTypeLocator) {
    final BuildTypeOrTemplate buildTypeOrTemplate = getBuildTypeOrTemplate(project, buildTypeLocator);
    if (buildTypeOrTemplate.isBuildType()){
      return buildTypeOrTemplate.getBuildType();
    }
    throw new NotFoundException("No build type is found by locator '" + buildTypeLocator + "' (template is found instead).");
  }

  @NotNull
  private SBuildType findBuildTypeById(@NotNull final String id) {
    SBuildType buildType = myServer.getProjectManager().findBuildTypeById(id);
    if (buildType == null) {
      throw new NotFoundException("No build type is found by id '" + id + "'.");
    }
    return buildType;
  }

  @NotNull
  private BuildTypeOrTemplate findBuildTypeOrTemplateByGeneralId(@NotNull final String id) {
    if (!id.startsWith(TEMPLATE_ID_PREFIX)){
      SBuildType buildType = myServer.getProjectManager().findBuildTypeById(id);
      if (buildType == null) {
        final BuildTypeTemplate buildTypeTemplate = myServer.getProjectManager().findBuildTypeTemplateById(id);
        if (buildTypeTemplate == null){
          throw new NotFoundException("No build type nor template is found by id '" + id + "'.");
        }
        return new BuildTypeOrTemplate(buildTypeTemplate);
      }
      return new BuildTypeOrTemplate(buildType);

    }
    String templateId = id.substring(TEMPLATE_ID_PREFIX.length());
    final BuildTypeTemplate buildTypeTemplate = myServer.getProjectManager().findBuildTypeTemplateById(templateId);
    if (buildTypeTemplate == null) {
      throw new NotFoundException("No build type template is found by id '" + templateId + "'.");
    }
    return new BuildTypeOrTemplate(buildTypeTemplate);
  }

  @NotNull
  public SBuildServer getServer() {
    return myServer;
  }

  @NotNull
  public SProject getProject(String projectLocator) {
    if (StringUtil.isEmpty(projectLocator)) {
      throw new BadRequestException("Empty project locator is not supported.");
    }

    final Locator locator = new Locator(projectLocator);

    if (locator.isSingleValue()) {
      // no dimensions found, assume it's a name or internal id or external id
      SProject project=null;
      project = myServer.getProjectManager().findProjectByExternalId(projectLocator);
      if (project != null) {
        return project;
      }
      project = myServer.getProjectManager().findProjectByName(projectLocator);
      if (project != null) {
        return project;
      }
      project = myServer.getProjectManager().findProjectById(projectLocator);
      if (project != null) {
        return project;
      }
      throw new NotFoundException(
        "No project found by locator '" + projectLocator + "'. Project cannot be found by name or internal/external id '" + projectLocator + "'.");
    }

    String id = locator.getSingleDimensionValue("id");
    if (id != null) {
      SProject project = myServer.getProjectManager().findProjectByExternalId(id);
      if (project == null) {
        if (TeamCityProperties.getBoolean("rest.compatibility.allowExternalIdAsInternal")){
          project = myServer.getProjectManager().findProjectById(id);
          if (project == null) {
            throw new NotFoundException("No project found by locator '" + projectLocator +
                                        " in compatibility mode. Project cannot be found by external or internal id '" + id + "'.");
          }
        }else{
          throw new NotFoundException("No project found by locator '" + projectLocator + ". Project cannot be found by external id '" + id + "'.");
        }
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Project locator '" + projectLocator + "' has 'id' dimension and others. Others are ignored.");
      }
      return project;
    }

    String internalId = locator.getSingleDimensionValue("internalId");
    if (internalId != null) {
      SProject project = myServer.getProjectManager().findProjectById(internalId);
      if (project == null) {
        throw new NotFoundException("No project found by locator '" + projectLocator + ". Project cannot be found by internal id '" + internalId + "'.");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Project locator '" + projectLocator + "' has 'internalId' dimension and others. Others are ignored.");
      }
      return project;
    }

    String name = locator.getSingleDimensionValue("name");
    if (name != null) {
      SProject project = myServer.getProjectManager().findProjectByName(name);
      if (project == null) {
        throw new NotFoundException("No project found by locator '" + projectLocator + ". Project cannot be found by name '" + name + "'.");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Project locator '" + projectLocator + "' has 'name' dimension and others. Others are ignored.");
      }
      return project;
    }
    throw new BadRequestException("Project locator '" + projectLocator + "' is not supported.");
  }
  
  @NotNull
  public SProject getProjectByInternalId(@NotNull String projectInternalId){
    final SProject project = myServer.getProjectManager().findProjectById(projectInternalId);
    if (project == null){
      throw new NotFoundException("Could not find project by internal id '" + projectInternalId + "'.");
    }
    return project;
  }
  



  /**
   * @param project project to search build type in. Can be 'null' to search in all the build types on the server.
   * @param name    name of the build type to search for.
   * @return build type with the name 'name'. If 'project' is not null, the search is performed only within 'project'.
   * @throws jetbrains.buildServer.server.rest.errors.BadRequestException
   *          if several build types with the same name are found
   */
  @NotNull
  public BuildTypeOrTemplate findBuildTypeByName(@Nullable final SProject fixedProject, @NotNull final String name) {
    if (fixedProject != null) {
      final BuildTypeOrTemplate result = getBuildTypeOrTemplateByName(fixedProject, name);
      if (result != null){
        return result;
      }
      throw new NotFoundException("No build type or template is found by name '" + name + "' in project '" + fixedProject.getName() +"'.");
    }

    final List<SProject> projects = myServer.getProjectManager().getProjects();
    BuildTypeOrTemplate firstFound = null;
    for (SProject project : projects) {
      final BuildTypeOrTemplate found = getBuildTypeOrTemplateByName(project, name);
      if (found != null) {
        if (firstFound != null) {
          throw new BadRequestException("Several matching build types/templates found for name '" + name + "'.");
        }
        firstFound = found;
      }
    }
    if (firstFound != null) {
      return firstFound;
    }
    throw new NotFoundException("No build type or template is found by name '" + name + "'.");
  }

  @Nullable
  private BuildTypeOrTemplate getBuildTypeOrTemplateByName(@NotNull final SProject project, @NotNull final String name) {
    final SBuildType buildType = project.findBuildTypeByName(name);
    if (buildType != null) {
      return new BuildTypeOrTemplate(buildType);

    }
    final BuildTypeTemplate buildTypeTemplate = project.findBuildTypeTemplateByName(name);
    if (buildTypeTemplate != null) {
      return new BuildTypeOrTemplate(buildTypeTemplate);
    }
    return null;
  }

  /**
   * Finds builds by the specified criteria within specified range
   * This is slow!
   *
   * @param buildsFilter the filter for the builds to find
   * @return the builds found
   */
  public List<SBuild> getBuilds(@NotNull final BuildsFilter buildsFilter) {
    final ArrayList<SBuild> result = new ArrayList<SBuild>();
    //todo: sort and ensure there are no duplicates
    result.addAll(BuildsFilterProcessor.getMatchingRunningBuilds(buildsFilter, myRunningBuildsManager));
    final Integer originalCount = buildsFilter.getCount();
    if (originalCount == null || result.size() < originalCount) {
      final BuildsFilter patchedBuildsFilter = new BuildsFilterWithBuildExcludes(buildsFilter, result);
      if (originalCount != null){
        patchedBuildsFilter.setCount(originalCount - result.size());
      }
      result.addAll(BuildsFilterProcessor.getMatchingFinishedBuilds(patchedBuildsFilter, myBuildHistory));
    }
    return result;
  }

  @NotNull
  public SUser getUser(String userLocator) {
    if (StringUtil.isEmpty(userLocator)) {
      throw new BadRequestException("Empty user locator is not supported.");
    }

    final Locator locator = new Locator(userLocator);
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's username
      SUser user = myUserModel.findUserAccount(null, userLocator);
      if (user == null) {
        if (!"current".equals(userLocator)) {
          throw new NotFoundException("No user can be found by username '" + userLocator + "'.");
        }
        // support for predefined "current" keyword to get current user
        final SUser currentUser = getCurrentUser();
        if (currentUser == null) {
          throw new NotFoundException("No current user.");
        } else {
          return currentUser;
        }
      }
      return user;
    }

    Long id = locator.getSingleDimensionValueAsLong("id");
    if (id != null) {
      SUser user = myUserModel.findUserById(id);
      if (user == null) {
        throw new NotFoundException("No user can be found by id '" + id + "'.");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("User locator '" + userLocator + "' has 'id' dimension and others. Others are ignored.");
      }
      return user;
    }

    String username = locator.getSingleDimensionValue("username");
    if (username != null) {
      SUser user = myUserModel.findUserAccount(null, username);
      if (user == null) {
        throw new NotFoundException("No user can be found by username '" + username + "'.");
      }
      return user;
    }
    throw new NotFoundException("User locator '" + userLocator + "' is not supported.");
  }

  @NotNull
  public Role getRoleById(String roleId) {
    if (StringUtil.isEmpty(roleId)) {
      throw new BadRequestException("Cannot file role by empty id.");
    }
    Role role = myRolesManager.findRoleById(roleId);
    if (role == null) {
      throw new NotFoundException("Cannot find role by id '" + roleId + "'.");
    }
    return role;
  }

  @NotNull
  public static RoleScope getScope(@NotNull String scopeData) {
    if ("g".equalsIgnoreCase(scopeData)) {
      return RoleScope.globalScope();
    }

    if (!scopeData.startsWith("p:")) {
      throw new NotFoundException("Cannot find scope by '" + scopeData + "' Valid formats are: 'g' or 'p:<projectId>'.");
    }

    return RoleScope.projectScope(scopeData.substring(2));
  }

  public static String getScopeRepresentation(@NotNull final RoleScope scope) {
    if (scope.isGlobal()) {
      return "g";
    }
    return "p:" + scope.getProjectId(); //todo: (TeamCity) open API: is it internal or external project id?
  }

  @NotNull
  public SUserGroup getGroup(final String groupLocator) {
    if (StringUtil.isEmpty(groupLocator)) {
      throw new BadRequestException("Empty group locator is not supported.");
    }

    final Locator locator = new Locator(groupLocator);
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's group key
      SUserGroup group = myGroupManager.findUserGroupByKey(groupLocator);
      if (group == null) {
        throw new NotFoundException("No group can be found by key '" + groupLocator + "'.");
      }
      return group;
    }

    String groupKey = locator.getSingleDimensionValue("key");
    if (groupKey != null) {
      SUserGroup group = myGroupManager.findUserGroupByKey(groupKey);
      if (group == null) {
        throw new NotFoundException("No group can be found by key '" + groupKey + "'.");
      }
      return group;
    }

    String groupName = locator.getSingleDimensionValue("name");
    if (groupName != null) {
      SUserGroup group = myGroupManager.findUserGroupByName(groupName);
      if (group == null) {
        throw new NotFoundException("No group can be found by name '" + groupName + "'.");
      }
      return group;
    }
    throw new NotFoundException("Group locator '" + groupLocator + "' is not supported.");
  }


  public Collection<User> getAllUsers() {
    final Collection<SUser> serverUsers = myUserModel.getAllUsers().getUsers();
    final Collection<User> result = new ArrayList<User>(serverUsers.size());
    for (SUser group : serverUsers) {
      result.add(group);
    }
    return result;
  }

  public Collection<UserGroup> getAllGroups() {
    final Collection<SUserGroup> serverUserGroups = myGroupManager.getUserGroups();
    final Collection<UserGroup> result = new ArrayList<UserGroup>(serverUserGroups.size());
    for (SUserGroup group : serverUserGroups) {
      result.add(group);
    }
    return result;
  }

  public Collection<VcsRoot> getAllVcsRoots() {
    final Collection<SVcsRoot> serverVcsRoots = myVcsManager.getAllRegisteredVcsRoots();
    final Collection<VcsRoot> result = new ArrayList<VcsRoot>(serverVcsRoots.size());
    for (SVcsRoot root : serverVcsRoots) {
      result.add(root);
    }
    return result;
  }

  @NotNull
  public SVcsRoot getVcsRoot(final String vcsRootLocator) {
    if (StringUtil.isEmpty(vcsRootLocator)) {
      throw new BadRequestException("Empty VCS root locator is not supported.");
    }

    final Locator locator = new Locator(vcsRootLocator);
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's root id
      @SuppressWarnings("ConstantConditions") SVcsRoot root = myVcsManager.findRootById(locator.getSingleValueAsLong());
      if (root == null) {
        throw new NotFoundException("No root can be found by id '" + vcsRootLocator + "'.");
      }
      return root;
    }

    Long rootId = locator.getSingleDimensionValueAsLong("id");
    if (rootId != null){
      SVcsRoot root = myVcsManager.findRootById(rootId);
      if (root == null) {
        throw new NotFoundException("No root can be found by id '" + vcsRootLocator + "'.");
      }
      return root;
    }

    String rootName = locator.getSingleDimensionValue("name");
    if (rootName != null) {
      SVcsRoot root = myVcsManager.findRootByName(rootName);
      if (root == null) {
        throw new NotFoundException("No root can be found by name '" + rootName + "'.");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("VCS root locator '" + vcsRootLocator + "' has 'name' dimension and others. Others are ignored.");
      }
      return root;
    }

    throw new NotFoundException("VCS root locator '" + vcsRootLocator + "' is not supported.");
  }

  @NotNull
  public VcsRootInstance getVcsRootInstance(@Nullable final String vcsRootLocator) {
    if (StringUtil.isEmpty(vcsRootLocator)) {
      throw new BadRequestException("Empty VCS root instance locator is not supported.");
    }

    final Locator locator = new Locator(vcsRootLocator);
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's root id
      final Long parsedId = locator.getSingleValueAsLong();
      if (parsedId == null) {
        throw new BadRequestException("Expecting VCS root instance id, found empty value.");
      }
      VcsRootInstance root = myVcsManager.findRootInstanceById(parsedId);
      if (root == null) {
        throw new NotFoundException("No root instance can be found by id '" + parsedId + "'.");
      }
      return root;
    }

    Long rootId = locator.getSingleDimensionValueAsLong("id");
    if (rootId == null) {
      throw new BadRequestException("No 'id' dimension found in locator '" + vcsRootLocator + "'.");
    }
    VcsRootInstance root = myVcsManager.findRootInstanceById(rootId);
    if (root == null) {
      throw new NotFoundException("No root instance can be found by id '" + rootId + "'.");
    }
    return root;
  }

  @NotNull
  public SVcsModification getChange(final String changeLocator) {
    if (StringUtil.isEmpty(changeLocator)) {
      throw new BadRequestException("Empty change locator is not supported.");
    }

    final Locator locator = new Locator(changeLocator);
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's id
      @SuppressWarnings("ConstantConditions") SVcsModification modification = myVcsManager.findModificationById(locator.getSingleValueAsLong(), false);
      if (modification == null) {
        throw new NotFoundException("No change can be found by id '" + changeLocator + "'.");
      }
      return modification;
    }

    Long id = locator.getSingleDimensionValueAsLong("id");
    Boolean isPersonal = locator.getSingleDimensionValueAsBoolean("personal", false);
    if (isPersonal == null){
      throw new BadRequestException("Only true/false values are supported for 'personal' dimension. Was: '" +
                                    locator.getSingleDimensionValue("personal") + "'");
    }

    if (id != null) {
      SVcsModification modification = myVcsManager.findModificationById(id, isPersonal);
      if (modification == null) {
        throw new NotFoundException("No change can be found by id '" + locator.getSingleDimensionValue("id") + "' (searching " +
                                    (isPersonal ? "personal" : "non-personal") + " changes).");
      }
      return modification;
    }
    throw new NotFoundException("VCS root locator '" + changeLocator + "' is not supported.");
  }

  @NotNull
  public SBuildAgent getAgent(@Nullable final String locatorString) {
    if (StringUtil.isEmpty(locatorString)) {
      throw new BadRequestException("Empty agent locator is not supported.");
    }

    final Locator locator = new Locator(locatorString);
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's name
      final SBuildAgent agent = findAgentByName(locator.getSingleValue());
      if (agent == null) {
        throw new NotFoundException("No agent can be found by name '" + locator.getSingleValue() + "'.");
      }
      return agent;
    }

    Long id = locator.getSingleDimensionValueAsLong("id");

    if (id != null) {
      final SBuildAgent agent = myAgentManager.findAgentById(id.intValue(), true);
      if (agent == null) {
        throw new NotFoundException("No agent can be found by id '" + locator.getSingleDimensionValue("id") + "'.");
      }
      return agent;
    }
    throw new NotFoundException("Agent locator '" + locatorString + "' is not supported.");
  }

  @NotNull
  public static SArtifactDependency getArtifactDep(final BuildTypeSettings buildType, final String artifactDepLocator) {
      if (StringUtil.isEmpty(artifactDepLocator)) {
        throw new BadRequestException("Empty artifact dependency locator is not supported.");
      }

      final Locator locator = new Locator(artifactDepLocator);

      if (locator.isSingleValue()) {
        // no dimensions found, assume it's an order number
        final Long order = locator.getSingleValueAsLong();
        if (order == null) {
          throw new NotFoundException("No artifact dependency found by locator '" + artifactDepLocator +
                                      ". Locator should be order number of the dependency in the build configuration.");
        }
        try {
          return buildType.getArtifactDependencies().get(order.intValue());
        } catch (IndexOutOfBoundsException e) {
          throw new NotFoundException(
            "No artifact dependency found by locator '" + artifactDepLocator + "'. There is no dependency with order " + order + ".");
        }
      }

    throw new BadRequestException("No artifact dependency found by locator '" + artifactDepLocator +
                                  "'. Locator should be order number of the dependency in the build configuration.");
  }

  public static Dependency getSnapshotDep(final BuildTypeSettings buildType, final String snapshotDepLocator) {
    if (StringUtil.isEmpty(snapshotDepLocator)) {
      throw new BadRequestException("Empty snapshot dependency locator is not supported.");
    }

    final Locator locator = new Locator(snapshotDepLocator);

    if (locator.isSingleValue()) {
      // no dimensions found, assume it's source build type id
      final String sourceBuildTypeId = locator.getSingleValue();
      //todo (TeamCity) seems like no way to get snapshot dependency by source build type
      final Dependency foundDependency = getSnapshotDepOrNull(buildType, sourceBuildTypeId);
      if (foundDependency != null) {
        return foundDependency;
      } else {
        throw new NotFoundException("No snapshot dependency found by locator '" + snapshotDepLocator +
                                    "'. There is no dependency with source build type id " + sourceBuildTypeId + ".");
      }
    }

    throw new BadRequestException(
      "No snapshot dependency found by locator '" + snapshotDepLocator + "'. Locator should be existing dependency source build type id.");
  }

  public static Dependency getSnapshotDepOrNull(final BuildTypeSettings buildType, final String sourceBuildTypeId){
    for (Dependency dependency : buildType.getDependencies()) {
      if (dependency.getDependOnId().equals(sourceBuildTypeId)) {
        return dependency;
      }
    }
    return null;
  }
  

  public static BuildTriggerDescriptor getTrigger(final BuildTypeSettings buildType, final String triggerLocator) {
    if (StringUtil.isEmpty(triggerLocator)) {
      throw new BadRequestException("Empty trigger locator is not supported.");
    }

    final Locator locator = new Locator(triggerLocator);

    if (locator.isSingleValue()) {
      // no dimensions found, assume it's trigger id
      final String triggerId = locator.getSingleValue();
      if (StringUtil.isEmpty(triggerId)){
        throw new BadRequestException("Trigger id cannot be empty.");
      }
      @SuppressWarnings("ConstantConditions") final BuildTriggerDescriptor foundTrigger = buildType.findTriggerById(triggerId);
      if (foundTrigger == null){
        throw new NotFoundException("No trigger found by id '" + triggerLocator +"' in build type.");
      }
      return foundTrigger;
    }
    throw new BadRequestException(
      "No trigger can be found by locator '" + triggerLocator + "'. Locator should be trigger id.");
  }


  public static Requirement getAgentRequirement(final BuildTypeSettings buildType, final String agentRequirementLocator) {
    if (StringUtil.isEmpty(agentRequirementLocator)) {
      throw new BadRequestException("Empty agent requirement locator is not supported.");
    }

    final Locator locator = new Locator(agentRequirementLocator);

    if (locator.isSingleValue()) {
      // no dimensions found, assume it's requirement parameter name
      final String parameterName = locator.getSingleValue();
      if (StringUtil.isEmpty(parameterName)){
        throw new BadRequestException("Agent requirement property name cannot be empty.");
      }
      Requirement result = getAgentRequirementOrNull(buildType, parameterName);
      if (result == null){
        throw new NotFoundException("No agent requirement for build parameter '" + parameterName +"' is found in the build type.");
      }
      return result;
    }
    throw new BadRequestException(
      "No agent requirement can be found by locator '" + agentRequirementLocator + "'. Locator should be property name.");
  }

  public static Requirement getAgentRequirementOrNull(final BuildTypeSettings buildType, final String parameterName) {
    final List<Requirement> requirements = buildType.getRequirements();
    for (Requirement requirement : requirements) {
      if (parameterName.equals(requirement.getPropertyName())) {
        return requirement;
      }
    }
    return null;
  }


  @Nullable
  public SBuildAgent findAgentByName(final String agentName) {
    return myAgentManager.findAgentByName(agentName, true);
  }

  @NotNull
  public String getBuildUrl(SBuild build) {
    return myWebLinks.getViewResultsUrl(build);
  }

  @NotNull
  public String getBuildTypeUrl(SBuildType buildType) {
    return myWebLinks.getConfigurationHomePageUrl(buildType);
  }

  @NotNull
  public String getProjectUrl(final SProject project) {
    return myWebLinks.getProjectPageUrl(project.getExternalId());
  }

  @NotNull
  public Collection<SBuildAgent> getAllAgents() {
    return getAllAgents(new AgentsSearchFields(true, true));
  }

  public Collection<SBuildAgent> getAllAgents(final AgentsSearchFields agentsSearchFields) {
    final List<SBuildAgent> result = myAgentManager.getRegisteredAgents(agentsSearchFields.isIncludeUnauthorized());
    if (agentsSearchFields.isIncludeDisconnected()) {
      result.addAll(myAgentManager.getUnregisteredAgents());
    }
    return result;
  }

  @NotNull
  public List<SVcsModification> getModifications(ChangesFilter changesFilter) {
    return changesFilter.getMatchingChanges(myVcsManager.getVcsHistory());
  }

  @Nullable
  public SProject getProjectIfNotNull(@Nullable final String projectLocator) {
    return projectLocator == null ? null : getProject(projectLocator);
  }

  @Nullable
  public SBuildType getBuildTypeIfNotNull(@Nullable final String buildTypeLocator) {
    return buildTypeLocator == null ? null : getBuildType(null, buildTypeLocator);
  }

  @Nullable
  public SUser getUserIfNotNull(@Nullable final String userLocator) {
    return userLocator == null ? null : getUser(userLocator);
  }

  @Nullable
  public SBuild getBuildIfNotNull(@Nullable final SBuildType buildType, @Nullable final String buildLocator) {
    return buildLocator == null ? null : getBuild(buildType, buildLocator);
  }

  @Nullable
  public SVcsRoot getVcsRootIfNotNull(@Nullable final String vcsRootLocator) {
    return vcsRootLocator == null ? null : getVcsRoot(vcsRootLocator);
  }

  @Nullable
  public VcsRootInstance getVcsRootInstanceIfNotNull(@Nullable final String vcsRootLocator) {
    return vcsRootLocator == null ? null : getVcsRootInstance(vcsRootLocator);
  }

  @Nullable
  public SVcsModification getChangeIfNotNull(@Nullable final String ChangeLocator) {
    return ChangeLocator == null ? null : getChange(ChangeLocator);
  }

  @Nullable
  public RangeLimit getRangeLimit(@Nullable final SBuildType buildType, @Nullable final String buildLocator, @Nullable final Date date) {
    //todo: need buildType here?
    if (buildLocator == null && date == null) {
      return null;
    }
    if (buildLocator != null) {
      if (date != null) {
        throw new BadRequestException("Both build and date are specified for a build rage limit");
      }
      return new RangeLimit(getBuild(buildType, buildLocator));
    }
    return new RangeLimit(date);
  }

  @Nullable
  public static Date parseDate(@Nullable final String dateString) {
    if (dateString == null) {
      return null;
    }
    try {
      return new SimpleDateFormat(Constants.TIME_FORMAT).parse(dateString);
    } catch (ParseException e) {
      throw new BadRequestException("Could not parse date from value '" + dateString + "'. Supported format example : " + Util.formatTime(new Date()) + " :", e);
    }
  }

  public void deleteBuild(final SBuild build) {
    myBuildHistory.removeEntry(build.getBuildId());
  }

  @NotNull
  public ServerPluginInfo getPluginInfo() {
    return myPluginInfo;
  }

  @Nullable
  public Date getServerStartTime() {
    return myServerListener.getServerStartTime();
  }

  public RoleEntry getGroupRoleEntry(final SUserGroup group, final String roleId, final String scopeValue) {
    if (roleId == null) {
      throw new BadRequestException("Expected roleId is not specified");
    }
    final RoleScope roleScope = getScope(scopeValue);
    final Collection<RoleEntry> roles = group.getRoles();
    for (RoleEntry roleEntry : roles) {
      if (roleScope.equals(roleEntry.getScope()) && roleId.equals(roleEntry.getRole().getId())) {
        return roleEntry;
      }
    }
    throw new NotFoundException("Group " + group + " does not have role with id: '" + roleId + "' and scope '" + scopeValue + "'");
  }

  public RoleEntry getUserRoleEntry(final SUser user, final String roleId, final String scopeValue) {
    if (roleId == null) {
      throw new BadRequestException("Expected roleId is not specified");
    }
    final RoleScope roleScope = getScope(scopeValue);
    final Collection<RoleEntry> roles = user.getRoles();
    for (RoleEntry roleEntry : roles) {
      if (roleScope.equals(roleEntry.getScope()) && roleId.equals(roleEntry.getRole().getId())) {
        return roleEntry;
      }
    }
    throw new NotFoundException("User " + user + " does not have role with id: '" + roleId + "' and scope '" + scopeValue + "'");
  }

  public String getHelpLink(@NotNull final String page, @Nullable final String anchor) {
    return myWebLinks.getHelp(page, anchor);
  }

  public void checkGlobalPermission(final Permission permission) throws AuthorizationFailedException{
    final AuthorityHolder authorityHolder = mySecurityContext.getAuthorityHolder();
    if (!authorityHolder.isPermissionGrantedForAnyProject(permission)) {
      throw new AuthorizationFailedException(
        "User " + authorityHolder.getAssociatedUser() + " does not have global permission " + permission);
    }
  }

  public void checkProjectPermission(final Permission permission, final String projectId) throws AuthorizationFailedException{
    final AuthorityHolder authorityHolder = mySecurityContext.getAuthorityHolder();
    if (!authorityHolder.isPermissionGrantedForProject(projectId, permission)) { //todo: (TeamCity) open API: is it internal or external project id?
      throw new AuthorizationFailedException("User " + authorityHolder.getAssociatedUser() + " does not have permission " + permission +
                                             "in project with internal id: '" + projectId + "'");
    }
  }

  @NotNull
  public VcsManager getVcsManager() {
    return myVcsManager;
  }

  @NotNull
  public SourceVersionProvider getSourceVersionProvider() {
    return mySourceVersionProvider;
  }

  public Collection<ServerPluginInfo> getPlugins() {
    final Collection<PluginInfo> detectedPlugins = myPluginManager.getDetectedPlugins();
    Collection<ServerPluginInfo> result = new ArrayList<ServerPluginInfo>(detectedPlugins.size());
    for (PluginInfo plugin : detectedPlugins) {
      result.add((ServerPluginInfo)plugin);
    }
    return result;
  }

  @NotNull
  public BuildDataStorage getBuildDataStorage() {
    return myBuildDataStorage;
  }

  @NotNull
  public ValueProviderRegistry getValueProviderRegistry() {
    return myValueProviderRegistry;
  }

  @Nullable
  public SUser getCurrentUser() {
    final User associatedUser = mySecurityContext.getAuthorityHolder().getAssociatedUser();
    if (associatedUser == null){
      return null;
    }
    return myUserModel.findUserAccount(null, associatedUser.getUsername());
  }
}
