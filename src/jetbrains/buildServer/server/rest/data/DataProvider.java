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
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.groups.UserGroupManager;
import jetbrains.buildServer.plugins.PluginManager;
import jetbrains.buildServer.plugins.bean.PluginInfo;
import jetbrains.buildServer.plugins.bean.ServerPluginInfo;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Constants;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.user.RoleAssignment;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.auth.*;
import jetbrains.buildServer.serverSide.db.DBFunctionsProvider;
import jetbrains.buildServer.serverSide.impl.VcsModificationChecker;
import jetbrains.buildServer.serverSide.statistics.ValueProviderRegistry;
import jetbrains.buildServer.serverSide.statistics.build.BuildDataStorage;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.VcsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * User: Yegor Yarko
 * Date: 28.03.2009
 */
public class DataProvider {
  private static final Logger LOG = Logger.getInstance(DataProvider.class.getName());

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
  @NotNull private final VcsModificationChecker myVcsModificationChecker;
  @NotNull private final BeanFactory myBeanFactory;
  @NotNull private final ConfigurableApplicationContext myApplicationContext;
  @NotNull private final DBFunctionsProvider myDbFunctionsProvider;

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
                      @NotNull final BuildPromotionManager promotionManager,
                      @NotNull final VcsModificationChecker vcsModificationChecker,
                      @NotNull final BeanFactory beanFactory,
                      @NotNull ConfigurableApplicationContext applicationContext,
                      @NotNull DBFunctionsProvider dbFunctionsProvider
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
    myVcsModificationChecker = vcsModificationChecker;
    myBeanFactory = beanFactory;
    myApplicationContext = applicationContext;
    myDbFunctionsProvider = dbFunctionsProvider;
  }

  @NotNull
  public SBuildServer getServer() {
    return myServer;
  }

  @NotNull
  public SProject getProjectByInternalId(@NotNull String projectInternalId){
    final SProject project = myServer.getProjectManager().findProjectById(projectInternalId);
    if (project == null){
      throw new NotFoundException("Could not find project by internal id '" + projectInternalId + "'.");
    }
    return project;
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

  public Collection<User> getAllUsers() {
    final Collection<SUser> serverUsers = myUserModel.getAllUsers().getUsers();
    final Collection<User> result = new ArrayList<User>(serverUsers.size());
    for (SUser group : serverUsers) {
      result.add(group);
    }
    return result;
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
    if (build.isFinished()){
      myBuildHistory.removeEntry((SFinishedBuild)build); //todo:  (TeamCity) open API: should also add entry into audit and check permisisons (see also deleteBuild callers)
    }else{
      throw new BadRequestException("Deleting not finished builds is not supported. Cancel the build and only then delete it.");
    }
  }

  @NotNull
  public ServerPluginInfo getPluginInfo() {
    return myPluginInfo;
  }

  @Nullable
  public Date getServerStartTime() {
    return myServerListener.getServerStartTime();
  }

  public static RoleEntry getGroupRoleEntry(final SUserGroup group, final String roleId, final String scopeValue, final BeanContext context) {
    if (roleId == null) {
      throw new BadRequestException("Expected roleId is not specified");
    }
    final RoleScope roleScope = RoleAssignment.getScope(scopeValue, context);
    final Collection<RoleEntry> roles = group.getRoles();
    for (RoleEntry roleEntry : roles) {
      if (roleScope.equals(roleEntry.getScope()) && roleId.equals(roleEntry.getRole().getId())) {
        return roleEntry;
      }
    }
    throw new NotFoundException("Group " + group + " does not have role with id: '" + roleId + "' and scope '" + scopeValue + "'");
  }

  public static RoleEntry getUserRoleEntry(final SUser user, final String roleId, final String scopeValue, final BeanContext context) {
    if (roleId == null) {
      throw new BadRequestException("Expected roleId is not specified");
    }
    final RoleScope roleScope = RoleAssignment.getScope(scopeValue, context);
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

  public void checkGlobalPermissionAnyOf(final Permission[] permissions) throws AuthorizationFailedException{
    final AuthorityHolder authorityHolder = mySecurityContext.getAuthorityHolder();
    for (Permission permission : permissions) {
      if (authorityHolder.isPermissionGrantedForAnyProject(permission)) {
        return;
      }
    }

    throw new AuthorizationFailedException(
      "User " + authorityHolder.getAssociatedUser() + " does not have any of the permissions granted globally: " + Arrays
        .toString(permissions));
  }

  public void checkProjectPermission(@NotNull final Permission permission, @Nullable final String projectId) throws AuthorizationFailedException{
    final AuthorityHolder authorityHolder = mySecurityContext.getAuthorityHolder();
    if (projectId == null){
      if (authorityHolder.isPermissionGrantedGlobally(permission)){
        return;
      }
      throw new AuthorizationFailedException("Not existing project with internal id: '" + projectId + "'. Treating as no permission.");
    }
    if (!authorityHolder.isPermissionGrantedForProject(projectId, permission)) {
      throw new AuthorizationFailedException("User " + authorityHolder.getAssociatedUser() + " does not have permission " + permission +
                                             " in project with internal id: '" + projectId + "'");
    }
  }

  // workaround for http://youtrack.jetbrains.com/issue/TW-28306
  public boolean checkCanView(final SVcsModification change) {
    final AuthorityHolder authorityHolder = mySecurityContext.getAuthorityHolder();
    if (authorityHolder.isPermissionGrantedGlobally(Permission.VIEW_PROJECT)){
      return true;
    }
    return AuthUtil.hasReadAccessTo(authorityHolder, change);
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

  @NotNull
  public UserModel getUserModel() {
    return myUserModel;
  }

  @NotNull
  public BuildPromotionManager getPromotionManager() {
    return myPromotionManager;
  }

  @NotNull
  public BuildHistory getBuildHistory() {
    return myBuildHistory;
  }

  @NotNull
  public RunningBuildsManager getRunningBuildsManager() {
    return myRunningBuildsManager;
  }

  @NotNull
  public VcsModificationChecker getVcsModificationChecker() {
    return myVcsModificationChecker;
  }

  @NotNull
  public BeanFactory getBeanFactory() {
    return myBeanFactory;
  }

  // Workaround for http://youtrack.jetbrains.com/issue/TW-25260
  public <T> T getBean(Class<T> type){
    if (type.equals(DBFunctionsProvider.class)) return (T)myDbFunctionsProvider;
    return myApplicationContext.getBean(type);
  }
}
