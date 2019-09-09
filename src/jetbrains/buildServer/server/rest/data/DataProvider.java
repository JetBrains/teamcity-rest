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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import jetbrains.buildServer.RootUrlHolder;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.maintenance.StartupContext;
import jetbrains.buildServer.metrics.MetricValue;
import jetbrains.buildServer.metrics.ServerMetricsReader;
import jetbrains.buildServer.plugins.PluginManager;
import jetbrains.buildServer.plugins.bean.PluginInfo;
import jetbrains.buildServer.plugins.bean.ServerPluginInfo;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Constants;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.user.RoleAssignment;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.agentPools.*;
import jetbrains.buildServer.serverSide.auth.*;
import jetbrains.buildServer.serverSide.db.DBFunctionsProvider;
import jetbrains.buildServer.serverSide.statistics.ValueProviderRegistry;
import jetbrains.buildServer.serverSide.statistics.build.BuildDataStorage;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.ChangesCheckingService;
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
  @NotNull private final VcsManager myVcsManager;
  @NotNull private final WebLinks myWebLinks;
  @NotNull private final ServerPluginInfo myPluginInfo;
  @NotNull private final ServerListener myServerListener;
  @NotNull private final PluginManager myPluginManager;
  @NotNull private final RunningBuildsManager myRunningBuildsManager;
  @NotNull private final ValueProviderRegistry myValueProviderRegistry;
  @NotNull private final BuildDataStorage myBuildDataStorage;
  @NotNull private final BuildPromotionManager myPromotionManager;
  @NotNull private final ChangesCheckingService myChangesCheckingService;
  @NotNull private final BeanFactory myBeanFactory;
  @NotNull private final ConfigurableApplicationContext myApplicationContext;
  @NotNull private final DBFunctionsProvider myDbFunctionsProvider;
  @NotNull private final StartupContext myStartupContext;
  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final PermissionChecker myPermissionChecker;
  @NotNull private final ServerMetricsReader myMetricsReader;

  public DataProvider(@NotNull final SBuildServer myServer,
                      @NotNull final BuildHistory myBuildHistory,
                      @NotNull final UserModel userModel,
                      @NotNull final RolesManager rolesManager,
                      @NotNull final VcsManager vcsManager,
                      @NotNull final WebLinks webLinks,
                      @NotNull final ServerPluginInfo pluginInfo,
                      @NotNull final ServerListener serverListener,
                      @NotNull final SecurityContext securityContext,
                      @NotNull final PluginManager pluginManager,
                      @NotNull final RunningBuildsManager runningBuildsManager,
                      @NotNull final ValueProviderRegistry valueProviderRegistry,
                      @NotNull final BuildDataStorage buildDataStorage,
                      @NotNull final BuildPromotionManager promotionManager,
                      @NotNull final ChangesCheckingService changesCheckingService,
                      @NotNull final BeanFactory beanFactory,
                      @NotNull final ConfigurableApplicationContext applicationContext,
                      @NotNull final DBFunctionsProvider dbFunctionsProvider,
                      @NotNull final StartupContext startupContext,
                      @NotNull final ServiceLocator serviceLocator,
                      @NotNull final RootUrlHolder rootUrlHolder,
                      @NotNull final PermissionChecker permissionChecker,
                      @NotNull final ServerMetricsReader metricsReader) {
    this.myServer = myServer;
    this.myBuildHistory = myBuildHistory;
    this.myUserModel = userModel;
    myRolesManager = rolesManager;
    myVcsManager = vcsManager;
    myWebLinks = webLinks;
    myPluginInfo = pluginInfo;
    myServerListener = serverListener;
    myPluginManager = pluginManager;
    myRunningBuildsManager = runningBuildsManager;
    myValueProviderRegistry = valueProviderRegistry;
    myBuildDataStorage = buildDataStorage;
    myPromotionManager = promotionManager;
    myChangesCheckingService = changesCheckingService;
    myBeanFactory = beanFactory;
    myApplicationContext = applicationContext;
    myDbFunctionsProvider = dbFunctionsProvider;
    myStartupContext = startupContext;
    myServiceLocator = serviceLocator;
    myPermissionChecker = permissionChecker;
    myMetricsReader = metricsReader;
  }

  public static String dumpQuoted(final Collection<String> strings) {
    final StringBuilder result = new StringBuilder();
    final Iterator<String> it = strings.iterator();
    while (it.hasNext()){
      result.append("\"").append(it.next()).append("\"");
      if (it.hasNext()) result.append(", ");
    }
    return result.toString();
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


  //todo: replace usages with TimeWithPrecision.parse
  @Nullable
  public static Date parseDate(@Nullable final String dateString) {
    if (dateString == null) {
      return null;
    }
    return getDate(dateString);
  }

  @NotNull
  public static Date getDate(@NotNull final String dateString) {
    try {
      return new SimpleDateFormat(Constants.TIME_FORMAT, Locale.ENGLISH).parse(dateString);
    } catch (ParseException e) {
      throw new BadRequestException("Could not parse date from value '" + dateString + "'. Supported format example: '" + Util.formatTime(new Date()) + "' : " + e.getMessage(), e);
    }
  }

  public static void deleteBuild(@NotNull final SBuild build, @NotNull final BuildHistory buildHistory) {
    if (build instanceof SFinishedBuild){
      buildHistory.removeEntry((SFinishedBuild)build); //todo:  (TeamCity) open API: should also add entry into audit and check permissions (see also deleteBuild callers)
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
    final RoleScope roleScope = RoleAssignment.getScope(scopeValue, context.getServiceLocator());
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
    final RoleScope roleScope = RoleAssignment.getScope(scopeValue, context.getServiceLocator());
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
    myPermissionChecker.checkGlobalPermission(permission);
  }

  @NotNull
  public VcsManager getVcsManager() {
    return myVcsManager;
  }

  public Collection<ServerPluginInfo> getPlugins() {
    final Collection<PluginInfo> detectedPlugins = myPluginManager.getDetectedPlugins();
    Collection<ServerPluginInfo> result = new ArrayList<ServerPluginInfo>(detectedPlugins.size());
    for (PluginInfo plugin : detectedPlugins) {
      result.add((ServerPluginInfo)plugin);
    }
    return result;
  }

  public List<MetricValue> getMetrics() {
    return myMetricsReader.queryBuilder().withExperimental(true).build();
  }

  @NotNull
  public BuildDataStorage getBuildDataStorage() {
    return myBuildDataStorage;
  }

  @NotNull
  public ValueProviderRegistry getValueProviderRegistry() {
    return myValueProviderRegistry;
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
  public ChangesCheckingService getChangesCheckingService() {
    return myChangesCheckingService;
  }

  @NotNull
  public BeanFactory getBeanFactory() {
    return myBeanFactory;
  }

  // Workaround for http://youtrack.jetbrains.com/issue/TW-25260
  public <T> T getBean(Class<T> type){
    if (type.equals(DBFunctionsProvider.class)) return (T)myDbFunctionsProvider;
    if (type.equals(StartupContext.class)) return (T)myStartupContext;
    return myApplicationContext.getBean(type);
  }

  public void addAgentToPool(@NotNull final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool, final int agentTypeId) {
    final AgentPoolManager agentPoolManager = myServiceLocator.getSingletonService(AgentPoolManager.class);
    final int agentPoolId = agentPool.getAgentPoolId();
    try {
      agentPoolManager.moveAgentTypesToPool(agentPoolId, Collections.singleton(agentTypeId)); //this moves the entire agent type to the pool, not only the agent, TW-40502
    } catch (NoSuchAgentPoolException e) {
      throw new IllegalStateException("Agent pool with id \'" + agentPoolId + "' is not found.");
    } catch (PoolQuotaExceededException e) {
      throw new IllegalStateException(e.getMessage());
    } catch (AgentTypeCannotBeMovedException e) {
      throw new IllegalStateException(e.getMessage());
    }
  }

  public void setProjectPools(final SProject project, final List<AgentPool> pools) {
    final AgentPoolManager poolManager = myServiceLocator.getSingletonService(AgentPoolManager.class);
      for (AgentPool agentPool : poolManager.getAllAgentPools()) {
        final int agentPoolId = agentPool.getAgentPoolId();
        try {
        if (pools.contains(agentPool)){
          poolManager.associateProjectsWithPool(agentPoolId, Collections.singleton(project.getProjectId()));
        }else{
          poolManager.dissociateProjectsFromPool(agentPoolId, Collections.singleton(project.getProjectId()));
        }
        } catch (NoSuchAgentPoolException e) {
          throw new NotFoundException("No agent pool is found by id '" + agentPoolId + "'.");
        }
      }
  }
}
