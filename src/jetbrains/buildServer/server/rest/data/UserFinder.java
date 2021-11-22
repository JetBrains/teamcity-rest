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
import com.intellij.util.Function;
import java.util.*;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.parameters.impl.MapParametersProviderImpl;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.CommonLocatorDimensionsList;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorDimensionDataType;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.*;
import jetbrains.buildServer.serverSide.impl.auth.ServerAuthUtil;
import jetbrains.buildServer.users.PropertyKey;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.users.impl.UserImpl;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.server.rest.data.FinderDataBinding.getItemHolder;
import static jetbrains.buildServer.server.rest.data.TypedFinderBuilder.Dimension;

/**
 * @author Yegor.Yarko
 * Date: 23.03.13
 */
@LocatorResource(value = LocatorName.USER,
  extraDimensions = {
    CommonLocatorDimensionsList.PROPERTY,
    CommonLocatorDimensionsList.CURRENT,
    AbstractFinder.DIMENSION_ITEM
  },
  baseEntity = "User",
  examples = {
    "`name:John Smith` — find user with name `John Smith`.",
    "`group:<groupLocator>` — find all users in user group found by `groupLocator`."
  }
)
public class UserFinder extends DelegatingFinder<SUser> {
  private static final Logger LOG = Logger.getInstance(jetbrains.buildServer.serverSide.impl.audit.finders.UserFinder.class.getName());
  public static final String REST_CHECK_ADDITIONAL_PERMISSIONS_ON_USERS_AND_GROUPS = "rest.request.checkAdditionalPermissionsForUsersAndGroups";

  @LocatorDimension("id") private static final Dimension<Long> ID = new Dimension<>("id");
  @LocatorDimension("username") private static final Dimension<String> USERNAME = new Dimension<>("username");
  @LocatorDimension(value = "group", format = LocatorName.USER_GROUP, notes = "User group (direct parent) locator.")
  private static final Dimension<SUserGroup> GROUP = new Dimension<>("group");
  @LocatorDimension(value = "affectedGroup", format = LocatorName.USER_GROUP, notes = "User group (direct or indirect parent) locator.")
  private static final Dimension<SUserGroup> AFFECTED_GROUP = new Dimension<>("affectedGroup");
  private static final Dimension<ParameterCondition> PROPERTY = new Dimension<>("property");
  @LocatorDimension("email") private static final Dimension<ValueCondition> EMAIL = new Dimension<>("email");
  @LocatorDimension("name") private static final Dimension<ValueCondition> NAME = new Dimension<>("name");
  private static final Dimension<Boolean> HAS_PASSWORD = new Dimension<>("hasPassword");
  private static final Dimension<String> PASSWORD = new Dimension<>("password");
  @LocatorDimension(value = "lastLogin", dataType = LocatorDimensionDataType.TIMESTAMP, format = "yyyyMMddTHHmmss+ZZZZ") private static final Dimension<TimeCondition.ParsedTimeCondition>
    LAST_LOGIN_TIME = new Dimension<>("lastLogin");
  @LocatorDimension(value = "role", format = LocatorName.ROLE, notes = "Role locator.")
  private static final Dimension<RoleEntryDatas> ROLE = new Dimension<>("role");
  private static final Dimension<ItemFilter<SUser>> PERMISSION = new Dimension<>("permission");
  //todo: add filtering by changes (authors), builds (triggering), audit events, etc?
  @NotNull private final UserModel myUserModel;
  @NotNull private final UserGroupFinder myGroupFinder;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final TimeCondition myTimeCondition;
  @NotNull private final RolesManager myRolesManager;
  @NotNull private final PermissionChecker myPermissionChecker;
  @NotNull private final SecurityContext mySecurityContext;
  @NotNull private final ServiceLocator myServiceLocator;

  public UserFinder(@NotNull final UserModel userModel,
                    @NotNull final UserGroupFinder groupFinder,
                    @NotNull final ProjectFinder projectFinder,
                    @NotNull final TimeCondition timeCondition,
                    @NotNull final RolesManager rolesManager,
                    @NotNull final PermissionChecker permissionChecker,
                    @NotNull final SecurityContext securityContext,
                    @NotNull final ServiceLocator serviceLocator) {
    myUserModel = userModel;
    myGroupFinder = groupFinder;
    myProjectFinder = projectFinder;
    myTimeCondition = timeCondition;
    myRolesManager = rolesManager;
    myPermissionChecker = permissionChecker;
    mySecurityContext = securityContext;
    myServiceLocator = serviceLocator;
    setDelegate(new UserFinderBuilder().build());
  }

  public void checkViewAllUsersPermissionEnforced() throws AuthorizationFailedException {
    if (TeamCityProperties.getBooleanOrTrue(REST_CHECK_ADDITIONAL_PERMISSIONS_ON_USERS_AND_GROUPS)) {
      checkViewAllUsersPermission();
    }
  }

  public void checkViewAllUsersPermission() throws AuthorizationFailedException {
    myPermissionChecker.checkGlobalPermissionAnyOf(new Permission[]{Permission.VIEW_USER_PROFILE, Permission.CHANGE_USER});
  }

  public void checkViewUserPermission(final @Nullable SUser user) throws AuthorizationFailedException {
    if (user == null) return;

    final jetbrains.buildServer.users.User currentUser = getCurrentUser();
      if (currentUser != null && currentUser.getId() == user.getId()) {
        return;
    }

      if (!ServerAuthUtil.canViewUser(mySecurityContext.getAuthorityHolder(), user)) {
        throw new AuthorizationFailedException("No permission to view user");
      }
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

  @NotNull
  private static ParametersProvider getUserPropertiesProvider(@NotNull final SUser item) {
    Map<PropertyKey, String> properties = item.getProperties();
    HashMap<String, String> result = new HashMap<>();
    for (Map.Entry<PropertyKey, String> entry : properties.entrySet()) {
      result.put(entry.getKey().getKey(), entry.getValue());
    }
    return new MapParametersProviderImpl(result);
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

  public static String getLocatorById(@NotNull final Long id) {
    return Locator.getStringLocator(ID.name, String.valueOf(id));
  }

  public static String getLocatorByUsername(@NotNull final String username) {
    return Locator.getStringLocator(USERNAME.name, username);
  }

  public static String getLocatorByGroup(@NotNull final SUserGroup userGroup) {
    return Locator.getStringLocator(GROUP.name, UserGroupFinder.getLocator(userGroup));
  }

  @NotNull
  public static String getLocator(@NotNull final User user) {
    return getLocatorById(user.getId());
  }

  @NotNull
  public SUser getItem(@Nullable final String locatorText, boolean checkViewPermission) {
    if (checkViewPermission) {
      SUser user = null;
      try {
        user = super.getItem(locatorText);
        return user;
      } finally {
        //should check in case of NotFoundException as well. this will mask original exception in cae of no permissions, but that is exactly what is necessary
        if (TeamCityProperties.getBooleanOrTrue(REST_CHECK_ADDITIONAL_PERMISSIONS_ON_USERS_AND_GROUPS)) {
          //related to http://youtrack.jetbrains.net/issue/TW-20071 and other cases
          checkViewUserPermission(user);
        }
      }
    }
    return super.getItem(locatorText);
  }

  static class RoleEntryDatas {
    private final Set<Role> anyScopeRoles = new HashSet<>();
    private final Set<RoleScope> anyRoleScopes = new HashSet<>();
    private final Map<Role, Set<RoleScope>> roleScopes = new HashMap<>();
    @NotNull private final RolesManager myRolesManager;
    @NotNull private final ProjectFinder myProjectFinder;
    @NotNull private final PermissionChecker myPermissionChecker;
    @NotNull private final String myMethod;

    public RoleEntryDatas(@NotNull final String roleLocatorText,
                          @NotNull final RolesManager rolesManager,
                          @NotNull final ProjectFinder projectFinder,
                          @NotNull final PermissionChecker permissionChecker) {
      Locator roleLocator = new Locator(roleLocatorText, "item", "method", Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);  //consider using generic "item" support from FinderImpl
      List<String> roleAssignmentsLocatorTexts;
      String method = null;
      if (roleLocator.isSingleValue()) {
        roleAssignmentsLocatorTexts = Collections.singletonList(roleLocator.getSingleValue());
      } else {
        roleAssignmentsLocatorTexts = roleLocator.getDimensionValue("item");
        method = roleLocator.getSingleDimensionValue("method");
      }
      if (method == null && roleAssignmentsLocatorTexts.isEmpty()) {
        roleAssignmentsLocatorTexts = Collections.singletonList(roleLocatorText);
      } else {
        roleLocator.checkLocatorFullyProcessed();
      }

      if (method == null) method = "effective";

      if (!"effective".equals(method) && !"byPermission".equals(method)) {
        //at some point can add locator dimensions to search direct roles or selectively considering projects, roles, groups nesting
        throw new BadRequestException("Unknown '" + "method" + "' role dimension value '" + method + "'. Supported are: " + "effective" + ", " + "byPermission");
      }

      //parsed, not initialize

      myMethod = method;
      myRolesManager = rolesManager;
      myProjectFinder = projectFinder;
      myPermissionChecker = permissionChecker;
      for (String value : roleAssignmentsLocatorTexts) {
        Locator locator = new Locator(value, "scope", "role");
        String scope = locator.getSingleDimensionValue("scope");
        String role = locator.getSingleDimensionValue("role");
        if (scope == null && role == null) {
          throw new LocatorProcessException("Invalid role locator '" + roleLocator + ": either 'scope' or 'role' dimension should be present");
        }
        locator.checkLocatorFullyProcessed();
        add(getScope(scope), getRole(role));
      }
    }

    boolean matches(@NotNull final SUser item) {
      if ("effective".equals(myMethod)) {
        return containsAllRolesEffectively(item);
      }
      //else if ("byPermission".equals(myMethod))
      return containsAllRolesByPermissions(item);
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
     * does matching by effective permissions
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

  private class UserFinderBuilder extends TypedFinderBuilder<SUser> {
    UserFinderBuilder() {
      name("UserFinder");
      singleDimension(dimension -> {
        //"current" is a reserved value for single dimension - current user.
        // before 2018.1 the precedence was different: if there was a user with username "current" it was served. However, the behavior was poorly predictable that way.
        if ("current".equals(dimension)) {
          // support for predefined "current" keyword to get current user
          final SUser currentUser = getCurrentUser();
          if (currentUser == null) {
            throw new NotFoundException("No current user.");
          } else {
            return Collections.singletonList(currentUser);
          }
        }
        // no dimensions found and it's not reserved current" -> assume it's username
        SUser user = myUserModel.findUserAccount(null, dimension);
        if (user == null) {
          throw new NotFoundException("No user can be found by username '" + dimension + "'.");
        }
        return Collections.singletonList(user);
      });

      dimensionLong(ID).description("internal user id")
                       .filter((value, item) -> value.equals(item.getId()))
                       .toItems(dimension -> {
                         SUser user = myUserModel.findUserById(dimension);
                         checkViewUserPermission(user);
                         if (user == null) {
                           throw new NotFoundException("No user can be found by id '" + dimension + "'.");
                         }
                         return Collections.singletonList(user);
                       });

      dimensionString(USERNAME).description("user's username")
                               .filter((value, item) -> value.equalsIgnoreCase(item.getUsername()))
                               .toItems(dimension -> {
                                 SUser user = myUserModel.findUserAccount(null, dimension);
                                 checkViewUserPermission(user);
                                 if (user == null) {
                                   throw new NotFoundException("No user can be found by username '" + dimension + "'.");
                                 }
                                 return Collections.singletonList(user);
                               });

      final Type<SUserGroup> myGroupMapper = type(dimensionValue -> myGroupFinder.getGroup(dimensionValue)).description("user groups locator");

      dimension(GROUP, myGroupMapper).description("user group including the user directly")
                                     .filter((value, item) -> value.containsUserDirectly(item))
                                     .toItems(dimension -> {
                                       checkViewAllUsersPermissionEnforced();
                                       return convert(dimension.getDirectUsers());
                                     });

      dimension(AFFECTED_GROUP, myGroupMapper).description("user group including the user considering groups hierarchy")
                                              .filter((value, item) -> item.getAllUserGroups().contains(value))
                                              .toItems(dimension -> {
                                                checkViewAllUsersPermissionEnforced();
                                                return convert(dimension.getAllUsers());
                                              });

      dimensionParameterCondition(PROPERTY).description("user's property").valueForDefaultFilter(item -> getUserPropertiesProvider(item));
      dimensionValueCondition(EMAIL).description("user's email").valueForDefaultFilter(item -> item.getEmail());
      dimensionValueCondition(NAME).description("user's display name").valueForDefaultFilter(item -> item.getName());
      dimensionBoolean(HAS_PASSWORD).description("user has not empty password").hidden().valueForDefaultFilter(item -> ((UserImpl)item).hasPassword());
      dimension(PASSWORD, type(dimensionValue -> {
        if (!myPermissionChecker.isPermissionGranted(Permission.CHANGE_SERVER_SETTINGS, null)) {
          throw new AuthorizationFailedException("Only system admin can query users for passwords");
        }
        try {
          Thread.sleep(TeamCityProperties.getInteger("rest.request.users.passwordCheckDelay.ms", 5 * 1000)); //inapt attempt to prevent brute-forcing
        } catch (InterruptedException e) {
          //ignore
        }
        return dimensionValue;
      }).description("text")).description("user's password")
                             .hidden()
                             .filter((value, item) -> myUserModel.findUserAccount(item.getRealm(), item.getUsername(), value) != null);

      dimensionTimeCondition(LAST_LOGIN_TIME, myTimeCondition).description("user's last login time").valueForDefaultFilter(item -> item.getLastLoginTimestamp());


      dimension(ROLE, type(dimensionValue -> new RoleEntryDatas(dimensionValue, myRolesManager, myProjectFinder, myPermissionChecker)).description("role locator"))
        .description("user's role")
        .filter((roleEntryDatas, item) -> roleEntryDatas.matches(item));

      PermissionCheck permissionCheck = new PermissionCheck();
      dimension(PERMISSION, type(dimensionValue -> permissionCheck.matches(dimensionValue)).description("permission check locator"))
        .description("user's permission (experimental)").hidden()
        .filter((type, item) -> type.isIncluded(item));

      multipleConvertToItemHolder(DimensionCondition.ALWAYS, dimensions -> {
        checkViewAllUsersPermissionEnforced();
        return getItemHolder(myUserModel.getAllUsers().getUsers());
      });

      locatorProvider(user -> getLocator(user));
      containerSetProvider(() -> new HashSet<SUser>());
    }
  }


  private class PermissionCheck {
    private final Dimension<List<SProject>> PROJECT = new Dimension<>("project");
    private final Dimension<Permission> PERMISSION = new Dimension<>("permission");

    private final Finder<SUser> myFinder;

    PermissionCheck() {
      TypedFinderBuilder<SUser> builder = new TypedFinderBuilder<SUser>();
      builder.dimensionProjects(PROJECT, myServiceLocator)
             .description("project to check permission in (matching when permission is present at least in one of the matched projects), when omitted checking globally");
      builder.dimensionEnum(PERMISSION, Permission.class).description("permission to check, should be present");
      builder.filter(locator -> locator.lookupSingleDimensionValue(PERMISSION.name) != null && locator.lookupDimensionValue(PROJECT.name).size() <= 1,
                     dimensions -> new UserPermissionFilter(dimensions));
      myFinder = builder.build();
    }

    ItemFilter<SUser> matches(@NotNull String permissionCheckLocator) {
      return myFinder.getFilter(permissionCheckLocator);
    }

    private class UserPermissionFilter implements ItemFilter<SUser> {
      private final Permission myPermission;
      private final List<SProject> myProjects;

      UserPermissionFilter(final TypedFinderBuilder.DimensionObjects dimensions) {
        //noinspection ConstantConditions - is checked in a filter condition earlier
        myPermission = dimensions.get(PERMISSION).get(0);
        List<List<SProject>> projects = dimensions.get(PROJECT);
        myProjects = projects == null ? null : projects.get(0);
      }

      @Override
      public boolean shouldStop(@NotNull final SUser item) {
        return false;
      }

      @Override
      public boolean isIncluded(@NotNull final SUser item) {
        return PermissionChecker.anyOfUsersHavePermissionForAnyOfProjects(Collections.singletonList(item), myPermission, myProjects);
      }
    }
  }
}
