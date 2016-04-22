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
import com.intellij.util.Function;
import java.util.*;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.parameters.impl.MapParametersProviderImpl;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.*;
import jetbrains.buildServer.users.PropertyKey;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.users.impl.UserImpl;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.13
 */
public class UserFinder extends AbstractFinder<SUser> {
  private static final Logger LOG = Logger.getInstance(UserFinder.class.getName());
  public static final String REST_CHECK_ADDITIONAL_PERMISSIONS_ON_USERS_AND_GROUPS = "rest.request.checkAdditionalPermissionsForUsersAndGroups";

  public static final String USERNAME = "username";
  public static final String GROUP = "group";
  public static final String AFFECTED_GROUP = "affectedGroup";
  public static final String PROPERTY = "property";
  public static final String EMAIL = "email";
  public static final String NAME = "name";
  public static final String HAS_PASSWORD = "hasPassword";
  public static final String PASSWORD = "password";
  public static final String LAST_LOGIN_TIME = "lastLogin";
  public static final String ROLE = "role";

  @NotNull private final UserGroupFinder myGroupFinder;

  @NotNull private final UserModel myUserModel;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final TimeCondition myTimeCondition;
  @NotNull private final RolesManager myRolesManager;
  @NotNull private final PermissionChecker myPermissionChecker;
  @NotNull private final SecurityContext mySecurityContext;

  public UserFinder(@NotNull final UserModel userModel,
                    @NotNull final UserGroupFinder groupFinder,
                    @NotNull final ProjectFinder projectFinder,
                    @NotNull final TimeCondition timeCondition,
                    @NotNull final RolesManager rolesManager,
                    @NotNull final PermissionChecker permissionChecker,
                    @NotNull final SecurityContext securityContext) {
    super(DIMENSION_ID, USERNAME, EMAIL, NAME, GROUP, AFFECTED_GROUP, PROPERTY, ROLE, LAST_LOGIN_TIME, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    myUserModel = userModel;
    myGroupFinder = groupFinder;
    myProjectFinder = projectFinder;
    myTimeCondition = timeCondition;
    myRolesManager = rolesManager;
    myPermissionChecker = permissionChecker;
    mySecurityContext = securityContext;
  }

  @NotNull
  @Override
  public Locator createLocator(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    final Locator result = super.createLocator(locatorText, locatorDefaults);
    result.addHiddenDimensions(HAS_PASSWORD, PASSWORD);
    return result;
  }

  @NotNull
  public String getItemLocator(@NotNull final SUser user) {
    return UserFinder.getLocator(user);
  }

  @NotNull
  public static String getLocator(@NotNull final User user) {
    return Locator.getStringLocator(DIMENSION_ID, String.valueOf(user.getId()));
  }

  @NotNull
  public SUser getItem(@Nullable final String locatorText, boolean checkViewPermission) {
    if (checkViewPermission) {
      return ensureViewUserPermissionEnforced(super.getItem(locatorText));
    }
    return super.getItem(locatorText);
  }

  @Nullable
  @Override
  protected SUser findSingleItem(@NotNull final Locator locator) {
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's username
      @SuppressWarnings("ConstantConditions") @NotNull String singleValue = locator.getSingleValue();
      SUser user = myUserModel.findUserAccount(null, singleValue);
      if (user == null) {
        if (!"current".equals(singleValue)) {
          throw new NotFoundException("No user can be found by username '" + singleValue + "'.");
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

    Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      SUser user = myUserModel.findUserById(id);
      if (user == null) {
        throw new NotFoundException("No user can be found by id '" + id + "'.");
      }
      return user;
    }

    String username = locator.getSingleDimensionValue(USERNAME);
    if (username != null) {
      SUser user = myUserModel.findUserAccount(null, username);
      if (user == null) {
        throw new NotFoundException("No user can be found by username '" + username + "'.");
      }
      return user;
    }

    return null;
  }

  @NotNull
  @Override
  protected ItemFilter<SUser> getFilter(@NotNull final Locator locator) {
    final LocatorBasedFilterBuilder<SUser> result = new LocatorBasedFilterBuilder<SUser>(locator);

    result.addLongFilter(DIMENSION_ID, new LocatorBasedFilterBuilder.ValueChecker<SUser, Long>() {
      @Override
      public boolean isIncluded(@NotNull final Long value, @NotNull final SUser item) {
        return value.equals(item.getId());
      }
    });

    result.addStringFilter(USERNAME, new LocatorBasedFilterBuilder.ValueChecker<SUser, String>() {
      @Override
      public boolean isIncluded(@NotNull final String value, @NotNull final SUser item) {
        return value.equalsIgnoreCase(item.getUsername());
      }
    });

    result.addSingleValueFilter(GROUP, new LocatorBasedFilterBuilder.ValueFromString<SUserGroup>() {
      @Override
      public SUserGroup get(@NotNull final String valueText) {
        return myGroupFinder.getGroup(valueText);
      }
    }, new LocatorBasedFilterBuilder.ValueChecker<SUser, SUserGroup>() {
      @Override
      public boolean isIncluded(@NotNull final SUserGroup value, @NotNull final SUser item) {
        return value.containsUserDirectly(item);
      }
    });

    result.addSingleValueFilter(AFFECTED_GROUP, new LocatorBasedFilterBuilder.ValueFromString<SUserGroup>() {
      @Override
      public SUserGroup get(@NotNull final String valueText) {
        return myGroupFinder.getGroup(valueText);
      }
    }, new LocatorBasedFilterBuilder.ValueChecker<SUser, SUserGroup>() {
      @Override
      public boolean isIncluded(@NotNull final SUserGroup value, @NotNull final SUser item) {
        return item.getAllUserGroups().contains(value);
      }
    });

    result.addParameterConditionFilter(EMAIL, new LocatorBasedFilterBuilder.NullableValue<String, SUser>() {
      @Override
      public String get(@NotNull final SUser source) {
        return source.getEmail();
      }
    });

    result.addParameterConditionFilter(NAME, new LocatorBasedFilterBuilder.NullableValue<String, SUser>() {
      @Override
      public String get(@NotNull final SUser source) {
        return source.getName();
      }
    });

    result.addBooleanMatchFilter(HAS_PASSWORD, new LocatorBasedFilterBuilder.NotNullValue<Boolean, SUser>() {
      @NotNull
      @Override
      public Boolean get(@NotNull final SUser source) {
        return ((UserImpl)source).hasPassword();
      }
    });

    result.addSingleValueFilter(PASSWORD, new LocatorBasedFilterBuilder.ValueFromString<String>() {
      @Override
      public String get(@NotNull final String valueText) {
        if (!myPermissionChecker.isPermissionGranted(Permission.CHANGE_SERVER_SETTINGS, null)) {
          throw new AuthorizationFailedException("Only system admin can query users for passwords");
        }
        try {
          Thread.sleep(5 * 1000); //inapt attempt to prevent brute-forcing
        } catch (InterruptedException e) {
          //ignore
        }
        return valueText;
      }
    }, new LocatorBasedFilterBuilder.ValueChecker<SUser, String>() {
      @Override
      public boolean isIncluded(@NotNull final String value, @NotNull final SUser item) {
        return myUserModel.findUserAccount(item.getRealm(), item.getUsername(), value) != null;
      }
    });

    result.addParametersConditionFilter(PROPERTY, new LocatorBasedFilterBuilder.NotNullValue<ParametersProvider, SUser>() {
      @NotNull
      @Override
      public ParametersProvider get(@NotNull final SUser item) {
        return getUserPropertiesProvider(item);
      }
    });

    result.addFilter(ROLE, new LocatorBasedFilterBuilder.ValueFromString<FilterConditionChecker<SUser>>() {
      @Override
      public FilterConditionChecker<SUser> get(@NotNull final String valueText) {
        Locator roleLocator = new Locator(valueText, "item", "method", Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
        List<String> roleAssignmentsLocatorTexts;
        String method = null;
        if (roleLocator.isSingleValue()) {
          roleAssignmentsLocatorTexts = Collections.singletonList(roleLocator.getSingleValue());
        } else {
          roleAssignmentsLocatorTexts = roleLocator.getDimensionValue("item");
          method = roleLocator.getSingleDimensionValue("method");
        }
        if (method == null && roleAssignmentsLocatorTexts.isEmpty()) {
          roleAssignmentsLocatorTexts = Collections.singletonList(valueText);
        } else {
          roleLocator.checkLocatorFullyProcessed();
        }

        if (method == null) method = "effective";

        RoleEntryDatas roleDatas = new RoleEntryDatas(roleAssignmentsLocatorTexts, myRolesManager, myProjectFinder, myPermissionChecker);
        if ("effective".equals(method)) {
          return new FilterConditionChecker<SUser>() {
            public boolean isIncluded(@NotNull final SUser item) {
              return roleDatas.containsAllRolesEffectively(item);
            }
          };
        } else if ("byPermission".equals(method)) {
          return new FilterConditionChecker<SUser>() {
            public boolean isIncluded(@NotNull final SUser item) {
              return roleDatas.containsAllRolesByPermissions(item);
            }
          };
        } else {
          //at some point can add locator dimensions to search direct roles or selectively considering projects, roles, groups nesting
          throw new BadRequestException("Unknown '" + "method" + "' role dimension value '" + method + "'. Supported are: " + "effective" + ", " + "byPermission");
        }
      }
    });

    //todo: add PERMISSION

    result.addFilter(LAST_LOGIN_TIME, new LocatorBasedFilterBuilder.ValueFromString<FilterConditionChecker<SUser>>() {
      @Override
      public FilterConditionChecker<SUser> get(@NotNull final String valueText) {
        return myTimeCondition.processTimeCondition(valueText, new TimeCondition.ValueExtractor<SUser, Date>() {
          @Nullable
          @Override
          public Date get(@NotNull final SUser sUser) {
            return sUser.getLastLoginTimestamp();
          }
        }, null).getFilter();
      }
    });

    return result.getFilter();
  }

  @NotNull
  private ParametersProvider getUserPropertiesProvider(@NotNull final SUser item) {
    Map<PropertyKey, String> properties = item.getProperties();
    HashMap<String, String> result = new HashMap<>();
    for (Map.Entry<PropertyKey, String> entry : properties.entrySet()) {
      result.put(entry.getKey().getKey(), entry.getValue());
    }
    return new MapParametersProviderImpl(result);
  }

  @NotNull
  @Override
  protected ItemHolder<SUser> getPrefilteredItems(@NotNull final Locator locator) {
    checkViewAllUsersPermissionEnforced();

    final String group = locator.getSingleDimensionValue(GROUP);
    if (group != null) {
      return getItemHolder(convert(myGroupFinder.getGroup(group).getDirectUsers()));
    }

    final String affectedGroup = locator.getSingleDimensionValue(AFFECTED_GROUP);
    if (affectedGroup != null) {
      return getItemHolder(convert(myGroupFinder.getGroup(affectedGroup).getAllUsers()));
    }

    return getItemHolder(myUserModel.getAllUsers().getUsers());
  }

  @Nullable
  public SUser getCurrentUser() {
    //also related API: SessionUser.getUser(request)
    final User associatedUser = mySecurityContext.getAuthorityHolder().getAssociatedUser();
    if (associatedUser == null) {
      return null;
    }
    if (SUser.class.isAssignableFrom(associatedUser.getClass())) {
      return (SUser)associatedUser;
    }
    return myUserModel.findUserAccount(null, associatedUser.getUsername());
  }

  public void checkViewUserPermission(final @NotNull SUser user) throws AuthorizationFailedException {
    final jetbrains.buildServer.users.User currentUser = getCurrentUser();
    if (currentUser != null && currentUser.getId() == user.getId()) {
      return;
    }
    checkViewAllUsersPermission();
  }

  //related to http://youtrack.jetbrains.net/issue/TW-20071 and other cases
  @Nullable
  @Contract("!null -> !null; null -> null")
  public SUser ensureViewUserPermissionEnforced(final @Nullable SUser user) throws AuthorizationFailedException {
    if (user != null && TeamCityProperties.getBooleanOrTrue(REST_CHECK_ADDITIONAL_PERMISSIONS_ON_USERS_AND_GROUPS)) {
      checkViewUserPermission(user);
    }
    return user;
  }

  public void checkViewAllUsersPermission() throws AuthorizationFailedException {
    myPermissionChecker.checkGlobalPermissionAnyOf(new Permission[]{Permission.VIEW_USER_PROFILE, Permission.CHANGE_USER});
  }

  public void checkViewAllUsersPermissionEnforced() throws AuthorizationFailedException {
    if (TeamCityProperties.getBooleanOrTrue(REST_CHECK_ADDITIONAL_PERMISSIONS_ON_USERS_AND_GROUPS)) {
      checkViewAllUsersPermission();
    }
  }

  @NotNull
  public static List<SUser> convert(Collection<User> users) {
    ArrayList<SUser> result = new ArrayList<>(users.size());
    for (User user : users) {
      if (SUser.class.isAssignableFrom(user.getClass())) {
        result.add((SUser)user);
      } else {
        LOG.info("Got User which is not SUser (skipping): " + user.describe(true));
      }
    }
    return result;
  }

  private static class RoleEntryDatas {
    private final Set<Role> anyScopeRoles = new HashSet<>();
    private final Set<RoleScope> anyRoleScopes = new HashSet<>();
    private final Map<Role, Set<RoleScope>> roleScopes = new HashMap<>();
    @NotNull private final RolesManager myRolesManager;
    @NotNull private final ProjectFinder myProjectFinder;
    @NotNull private final PermissionChecker myPermissionChecker;


    public RoleEntryDatas(@NotNull final List<String> roleLocators,
                          @NotNull final RolesManager rolesManager,
                          @NotNull final ProjectFinder projectFinder,
                          @NotNull final PermissionChecker permissionChecker) {
      myRolesManager = rolesManager;
      myProjectFinder = projectFinder;
      myPermissionChecker = permissionChecker;
      for (String roleLocator : roleLocators) {
        Locator locator = new Locator(roleLocator, "scope", "role");
        String scope = locator.getSingleDimensionValue("scope");
        String role = locator.getSingleDimensionValue("role");
        if (scope == null && role == null) {
          throw new BadRequestException("Invalid role locator '" + roleLocator + ": either 'scope' or 'role' dimension should be present");
        }
        locator.checkLocatorFullyProcessed();
        add(getScope(scope), getRole(role));
      }
    }

    /**
     * Considers roles, projects and user groups nesting
     */
    boolean containsAllRolesEffectively(@NotNull final RolesHolder mainHolder) {
      Set<Role> anyScopeRoles = new HashSet<>(this.anyScopeRoles);
      Set<RoleScope> anyRoleScopes = new HashSet<>(this.anyRoleScopes);
      Map<Role, Set<RoleScope>> roleScopes = new HashMap<>(this.roleScopes.size());
      for (Map.Entry<Role, Set<RoleScope>> entry : this.roleScopes.entrySet()) {
        roleScopes.put(entry.getKey(), new HashSet<>(entry.getValue()));
      }

      for (RolesHolder holder : CollectionsUtil.join(Collections.singletonList(mainHolder), mainHolder.getAllParentHolders())) {
        for (RoleEntry mainRole : holder.getRoles()) {
          anyRoleScopes.remove(mainRole.getScope());
          for (Role role : CollectionsUtil.join(Collections.singletonList(mainRole.getRole()), Arrays.asList(mainRole.getRole().getIncludedRoles()))) {
            Set<RoleScope> scopes = roleScopes.get(role);
            if (scopes != null) {
              if (mainRole.getScope().isGlobal()) {
                roleScopes.remove(role);
              } else {
                scopes.remove(mainRole.getScope());
                BuildProject project = myProjectFinder.findProjectByInternalId(mainRole.getScope().getProjectId());
                if (project != null) {
                  for (BuildProject subProject : project.getProjects()) {
                    scopes.remove(RoleScope.projectScope(subProject.getProjectId()));
                  }
                }
              }
              if (scopes.isEmpty()) roleScopes.remove(role);
            }
            anyScopeRoles.remove(role);
          }
          if (anyScopeRoles.isEmpty() && anyRoleScopes.isEmpty() && roleScopes.isEmpty()) return true;
        }
      }
      return false;
    }

    private Permissions globalPermissions = null;
    private Map<String, Permissions> projectsPermissions = null;

    /**
     * does matching by effective permisisons
     */
    boolean containsAllRolesByPermissions(@NotNull final AuthorityHolder mainHolder) {
      if (globalPermissions == null) {
        initPermissions();
      }
      Permissions holderGlobalPermissions = mainHolder.getGlobalPermissions();
      Map<String, Permissions> holderProjectsPermissions = mainHolder.getProjectsPermissions();
      if (!holderGlobalPermissions.containsAll(globalPermissions)) return false;
      for (Map.Entry<String, Permissions> requiredPermissions : projectsPermissions.entrySet()) {
        Permissions actualPermissions = holderProjectsPermissions.get(requiredPermissions.getKey());
        if (actualPermissions == null) {
          return holderGlobalPermissions.containsAll(requiredPermissions.getValue());
        }
        if (!getCombined(actualPermissions, holderGlobalPermissions).containsAll(requiredPermissions.getValue())) return false;
      }
      return true;
    }

    private void initPermissions() {
      if (!anyScopeRoles.isEmpty() || !anyRoleScopes.isEmpty() || roleScopes.isEmpty()) {
        throw new BadRequestException("When matching roles by permissions, both scope and role should be specified.");
      }
      globalPermissions = new Permissions();
      projectsPermissions = new HashMap<>();
      for (Map.Entry<Role, Set<RoleScope>> entry : roleScopes.entrySet()) {
        for (RoleScope scope : entry.getValue()) {
          if (scope.isGlobal()) {
            globalPermissions = getCombined(entry.getKey().getPermissions(), this.globalPermissions);
          } else {
            projectsPermissions.put(scope.getProjectId(), getCombined(entry.getKey().getPermissions(), projectsPermissions.get(scope.getProjectId())));
          }
        }
      }
    }

    private static Permissions getCombined(@NotNull final Permissions permissions1, @Nullable final Permissions permissions2) {
      BitSet mask = new BitSet();
      mask.or(permissions1.getMask());
      if (permissions2 != null) mask.or(permissions2.getMask());
      return new Permissions(mask);
    }

    private void add(@Nullable final RoleScope scope, @Nullable final Role role) {
      if (scope == null && role == null) {
        throw new BadRequestException("Either 'scope' or 'role' should be defined");
      }
      if (scope == null) {
        anyScopeRoles.add(role);
      } else if (role == null) {
        anyRoleScopes.add(scope);
      } else {
        Set<RoleScope> scopes = roleScopes.get(role);
        if (scopes == null) {
          scopes = new HashSet<>();
          roleScopes.put(role, scopes);
        }
        scopes.add(scope);
      }
    }

    // See also jetbrains.buildServer.server.rest.model.user.RoleAssignment.getScope()
    @Nullable
    private RoleScope getScope(@Nullable String scopeLocator) {
      if (scopeLocator == null) return null;
      Locator locator = new Locator(scopeLocator, "project", "p", Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);

      if (locator.isSingleValue()) {
        if ("global".equalsIgnoreCase(locator.getSingleValue()) || "g".equalsIgnoreCase(locator.getSingleValue())) { //g is for compatibility
          return RoleScope.globalScope();
        }
        throw new BadRequestException("Invalid scope specification '" + scopeLocator + "'. Should be 'global' or project:<projectLocator>");
      }

      String projectLocator = locator.getSingleDimensionValue("project");
      if (projectLocator == null) {
        projectLocator = locator.getSingleDimensionValue("p"); //compatibility
      }
      if (projectLocator == null) {
        throw new BadRequestException("Invalid scope locator '" + scopeLocator + "'. Should be 'global' or project:<projectLocator>");
      }
      locator.checkLocatorFullyProcessed();

      SProject project = myProjectFinder.getItem(projectLocator);
      return RoleScope.projectScope(project.getProjectId());
    }

    @Nullable
    private Role getRole(@Nullable String roleLocator) {
      if (roleLocator == null) return null;
      Locator locator = new Locator(roleLocator, "id", Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);

      String roleId = null;
      if (locator.isSingleValue()) {
        roleId = locator.getSingleValue();
      } else {
        String id = locator.getSingleDimensionValue("id");
        if (id != null) {
          roleId = id;
        }
      }
      if (roleId == null) {
        throw new BadRequestException(addRoleIdsIfApplicable("Invalid role locator '" + roleLocator + "'. Should contain id of one of the roles defined on the server."));
      }
      locator.checkLocatorFullyProcessed();

      Role role = myRolesManager.findRoleById(roleId);
      if (role == null) {
        role = myRolesManager.findRoleById(roleId.toUpperCase()); //support for any case
        if (role == null) {
          throw new NotFoundException(addRoleIdsIfApplicable("Cannot find role by id '" + roleId + "'."));
        }
      }
      return role;
    }

    private String addRoleIdsIfApplicable(final String initialMessage) {
      if (myPermissionChecker.isPermissionGranted(Permission.VIEW_USER_PROFILE, null)) {
        return initialMessage + " Available role ids: " + StringUtil.join(myRolesManager.getAvailableRoles(), new Function<Role, String>() {
          @Override
          public String fun(final Role role) {
            return role.getId();
          }
        }, ", ") + ".";
      }
      return initialMessage;
    }
  }
}
