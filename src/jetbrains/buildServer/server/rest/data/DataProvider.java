/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.groups.UserGroupManager;
import jetbrains.buildServer.plugins.PluginManager;
import jetbrains.buildServer.plugins.bean.PluginInfo;
import jetbrains.buildServer.plugins.bean.ServerPluginInfo;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Constants;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.*;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsManager;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 28.03.2009
 */
public class DataProvider {
  private static final Logger LOG = Logger.getInstance(DataProvider.class.getName());

  private final SBuildServer myServer;
  private final BuildHistory myBuildHistory;
  private final UserModel myUserModel;
  private final RolesManager myRolesManager;
  private final UserGroupManager myGroupManager;
  private final VcsManager myVcsManager;
  private final BuildAgentManager myAgentManager;
  private final WebLinks myWebLinks;
  private ServerPluginInfo myPluginInfo;
  private ServerListener myServerListener;
  private SecurityContext mySecurityContext;
  private SourceVersionProvider mySourceVersionProvider;
  private PluginManager myPluginManager;
  private RunningBuildsManager myRunningBuildsManager;

  public DataProvider(SBuildServer myServer,
                      BuildHistory myBuildHistory,
                      UserModel userModel,
                      final RolesManager rolesManager,
                      final UserGroupManager groupManager,
                      final VcsManager vcsManager,
                      final BuildAgentManager agentManager,
                      final WebLinks webLinks,
                      final ServerPluginInfo pluginInfo,
                      final ServerListener serverListener,
                      final SecurityContext securityContext,
                      final SourceVersionProvider sourceVersionProvider,
                      final PluginManager pluginManager,
                      final RunningBuildsManager runningBuildsManager
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
  }

  @Nullable
  public String getFieldValue(final SBuildType buildType, final String field) {
    if ("id".equals(field)) {
      return buildType.getBuildTypeId();
    } else if ("description".equals(field)) {
      return buildType.getDescription();
    } else if ("name".equals(field)) {
      return buildType.getName();
    }
    throw new NotFoundException("Field '" + field + "' is not supported.");
  }

  @Nullable
  public String getFieldValue(final SProject project, final String field) {
    if ("id".equals(field)) {
      return project.getProjectId();
    } else if ("description".equals(field)) {
      return project.getDescription();
    } else if ("name".equals(field)) {
      return project.getName();
    }
    throw new NotFoundException("Field '" + field + "' is not supported.");
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
    }
    throw new NotFoundException("Field '" + field + "' is not supported.");
  }

  @Nullable
  public String getServerFieldValue(@Nullable final String field) {
    if ("version".equals(field)) {
      return myServer.getFullServerVersion();
    } else if ("build".equals(field)) {
      return myServer.getBuildNumber();
    } else if ("majorVersion".equals(field)) {
      return Byte.toString(myServer.getServerMajorVersion());
    } else if ("minorVersion".equals(field)) {
      return Byte.toString(myServer.getServerMinorVersion());
    }
    throw new NotFoundException("Field '" + field + "' is not supported.");
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

        SBuild build = myServer.findBuildInstanceById(locator.getSingleValueAsLong());
        if (build == null) {
          throw new BadRequestException("Cannot find build by id '" + locator.getSingleValue() + "'.");
        }
        return build;
      }
      // no dimensions found and build type is specified, assume it's build number
      SBuild build = myServer.findBuildInstanceByBuildNumber(buildType.getBuildTypeId(), buildLocator);
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
        throw new NotFoundException("Build number is specified without build configuraiton. Cannot find build by build number only.");
      }
    }

    final BuildsFilter buildsFilter = getBuildsFilterByLocator(buildType, locator);
    buildsFilter.setCount(1);

    final List<SBuild> filteredBuilds = getBuilds(buildsFilter);
    if (filteredBuilds.size() == 0){
      throw new NotFoundException("No build found by filter: " + buildsFilter.toString() + ".");
    }

    if (filteredBuilds.size() == 1){
      return filteredBuilds.get(0);
    }

    //todo: check for unknown dimension names

    throw new NotFoundException("Build locator '" + buildLocator + "' is not supported (" + filteredBuilds.size() + " builds found)");
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
    final Long count = locator.getSingleDimensionValueAsLong("count");
    return new BuildsFilter(actualBuildType,
                            locator.getSingleDimensionValue("status"),
                            userLocator != null ? getUser(userLocator) : null,
                            locator.getSingleDimensionValueAsBoolean("personal"),
                            locator.getSingleDimensionValueAsBoolean("canceled"),
                            locator.getSingleDimensionValueAsBoolean("running", false),
                            locator.getSingleDimensionValueAsBoolean("pinned"),
                            tagsString == null ? null : Arrays.asList(tagsString.split(",")),
                            //todo: support agent locator here
                            locator.getSingleDimensionValue("agentName"),
                            getRangeLimit(actualBuildType, locator.getSingleDimensionValue("sinceBuild"),
                                          parseDate(locator.getSingleDimensionValue("sinceDate"))),
                            locator.getSingleDimensionValueAsLong("start"),
                            count == null?null:count.intValue());
  }

  @NotNull
  public SBuildType getBuildType(@Nullable final SProject project, @Nullable final String buildTypeLocator) {
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
        return findBuildTypeById(buildTypeLocator);
      }
    }

    String id = locator.getSingleDimensionValue("id");
    if (!StringUtil.isEmpty(id)) {
      assert id != null;
      SBuildType buildType = findBuildTypeById(id);
      if (project != null && !buildType.getProject().equals(project)) {
        throw new NotFoundException("Build type with id '" + id + "' does not belong to project " + project + ".");
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
  private SBuildType findBuildTypeById(@NotNull final String id) {
    SBuildType buildType = myServer.getProjectManager().findBuildTypeById(id);
    if (buildType == null) {
      throw new NotFoundException("No build type is found by id '" + id + "'.");
    }
    return buildType;
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
      // no dimensions found, assume it's a name
      SProject project = myServer.getProjectManager().findProjectByName(projectLocator);
      if (project == null) {
        throw new NotFoundException(
          "No project found by locator '" + projectLocator + "'. Project cannot be found by name '" + projectLocator + "'.");
      }
      return project;
    }

    String id = locator.getSingleDimensionValue("id");
    if (id != null) {
      SProject project = myServer.getProjectManager().findProjectById(id);
      if (project == null) {
        throw new NotFoundException("No project found by locator '" + projectLocator + ". Project cannot be found by id '" + id + "'.");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Project locator '" + projectLocator + "' has 'id' dimension and others. Others are ignored.");
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

  /**
   * @param project project to search build type in. Can be 'null' to search in all the build types on the server.
   * @param name    name of the build type to search for.
   * @return build type with the name 'name'. If 'project' is not null, the search is performed only within 'project'.
   * @throws jetbrains.buildServer.server.rest.errors.BadRequestException
   *          if several build types with the same name are found
   */
  @NotNull
  public SBuildType findBuildTypeByName(@Nullable final SProject project, @NotNull final String name) {
    if (project != null) {
      final SBuildType buildType = project.findBuildTypeByName(name);
      if (buildType == null) {
        throw new NotFoundException("No build type is found by name '" + name + "' in project " + project.getName() + ".");
      }
      return buildType;
    }
    List<SBuildType> allBuildTypes = myServer.getProjectManager().getAllBuildTypes();
    SBuildType foundBuildType = null;
    for (SBuildType buildType : allBuildTypes) {
      if (name.equalsIgnoreCase(buildType.getName())) {
        if (foundBuildType == null) {
          foundBuildType = buildType;
        } else {
          //second match found
          throw new BadRequestException("Several matching build types found for name '" + name + "'.");
        }
      }
    }
    if (foundBuildType == null) {
      throw new NotFoundException("No build type is found by name '" + name + "'.");
    }
    return foundBuildType;
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
    result.addAll(buildsFilter.getMatchingRunningBuilds(myRunningBuildsManager));
    final Integer originalCount = buildsFilter.getCount();
    if (originalCount == null || result.size() < originalCount) {
      if (originalCount != null){
        buildsFilter.setCount(originalCount - result.size());
      }
      result.addAll(buildsFilter.getMatchingFinishedBuilds(myBuildHistory));
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
        throw new NotFoundException("No user can be found by username '" + userLocator + "'.");
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
    return "p:" + scope.getProjectId();
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
      SVcsRoot root = myVcsManager.findRootById(locator.getSingleValueAsLong());
      if (root == null) {
        throw new NotFoundException("No root can be found by id '" + vcsRootLocator + "'.");
      }
      return root;
    }

    Long id = locator.getSingleDimensionValueAsLong("id");
    if (id != null) {

      Long version = locator.getSingleDimensionValueAsLong("ver");
      if (version != null) {
        SVcsRoot root = myVcsManager.findRootByIdAndVersion(id, version);
        if (root == null) {
          throw new NotFoundException("No root can be found by id '" + locator.getSingleDimensionValue("id") + "' and version '" + version + "'.");
        }
        if (locator.getDimensionsCount() > 2) {
          LOG.info("VCS root locator '" + vcsRootLocator + "' has 'id' and 'ver' dimensions and others. Others are ignored.");
        }
        return root;
      }

      SVcsRoot root = myVcsManager.findRootById(id);
      if (locator.getDimensionsCount() > 1) {
        LOG.info("VCS root locator '" + vcsRootLocator + "' has 'id' dimension and others. Others are ignored.");
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
  public SVcsModification getChange(final String changeLocator) {
    if (StringUtil.isEmpty(changeLocator)) {
      throw new BadRequestException("Empty change locator is not supported.");
    }

    final Locator locator = new Locator(changeLocator);
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's id
      SVcsModification modification = myVcsManager.findModificationById(locator.getSingleValueAsLong(), false);
      if (modification == null) {
        throw new NotFoundException("No change can be found by id '" + changeLocator + "'.");
      }
      return modification;
    }

    Long id = locator.getSingleDimensionValueAsLong("id");
    boolean isPersonal = locator.getSingleDimensionValueAsBoolean("personal", false);

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
    return myWebLinks.getProjectPageUrl(project.getProjectId());
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
      throw new BadRequestException("Could not parse date from value '" + dateString + "'", e);
    }
  }

  public void deleteBuild(final SBuild build) {
    myBuildHistory.removeEntry(build.getBuildId());
  }

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
    if (!authorityHolder.isPermissionGrantedForProject(projectId, permission)) {
      throw new AuthorizationFailedException("User " + authorityHolder.getAssociatedUser() + " does not have permission " + permission +
                                             "in project with id: '" + projectId + "'");
    }
  }

  public VcsManager getVcsManager() {
    return myVcsManager;
  }

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
}
