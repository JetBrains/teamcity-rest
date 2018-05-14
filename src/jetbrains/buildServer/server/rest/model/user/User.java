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

package jetbrains.buildServer.server.rest.model.user;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataUpdater;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.UserFinder;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.group.Groups;
import jetbrains.buildServer.server.rest.request.UserRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.impl.auth.ServerAuthUtil;
import jetbrains.buildServer.users.PropertyHolder;
import jetbrains.buildServer.users.PropertyKey;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 12.04.2009
 */
@XmlRootElement(name = "user")
@XmlType(name = "user", propOrder = {"username", "name", "id", "email", "lastLogin", "password", "href",
  "properties", "roles", "groups"})
public class User {
  private SUser myUser;
  private Fields myFields;
  private BeanContext myContext;

  public User() {
  }

  public User(@NotNull jetbrains.buildServer.users.User user, @NotNull final Fields fields, @NotNull final BeanContext context) {
    this.myUser = (SUser)user;
    myFields = fields;
    myContext = context;
  }

  private static void checkCanViewUserDetails(@Nullable final SUser user, @NotNull final ServiceLocator context) {
    context.getSingletonService(UserFinder.class).checkViewUserPermission(user);   //until http://youtrack.jetbrains.net/issue/TW-20071 is fixed
    if (TeamCityProperties.getBoolean("rest.beans.user.checkPermissions.limitViewUserProfileToListableUsersOnly")) { // related to TW-51644
      // see AdminEditUserController for related code
      if (user != null) {
        if (ServerAuthUtil.canViewUser(context.getSingletonService(SecurityContext.class).getAuthorityHolder(), user)) {
          return;
        }
        throw new AuthorizationFailedException("No permission to view full detail of user with id \"" + user.getId() + "\"");
      }
      context.getSingletonService(PermissionChecker.class).checkGlobalPermissionAnyOf(new Permission[]{Permission.VIEW_ALL_USERS, Permission.CHANGE_USER});
    }
  }

  @XmlAttribute
  public Long getId() {
    return  ValueWithDefault.decideDefault(myFields.isIncluded("id"), myUser.getId());
  }

  @XmlAttribute
  public String getName() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("name"), StringUtil.isEmpty(myUser.getName()) ? null : myUser.getName());
  }

  @XmlAttribute
  public String getUsername() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("username"), myUser.getUsername());
  }

  @XmlAttribute
  public String getLastLogin() {
    return myUser == null ? null : ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("lastLogin", false), new ValueWithDefault.Value<String>() {
      public String get() {
        checkCanViewUserDetails(myUser, myContext.getServiceLocator());
        Date lastLoginTimestamp = myUser.getLastLoginTimestamp();
        if (lastLoginTimestamp != null) {
          return Util.formatTime(lastLoginTimestamp);
        }
        return null;
      }
    });
  }

  @XmlAttribute
  public String getHref() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("href"), myContext.getContextService(ApiUrlBuilder.class).getHref(myUser));
  }

  @XmlAttribute
  public String getEmail() {
    return ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("email", false), new ValueWithDefault.Value<String>() {
      public String get() {
        checkCanViewUserDetails(myUser, myContext.getServiceLocator());
        return StringUtil.isEmpty(myUser.getEmail()) ? null : myUser.getEmail();
      }
    });
  }

  @XmlElement(name = "roles")
  public RoleAssignments getRoles() {
    return ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("roles", false), new ValueWithDefault.Value<RoleAssignments>() {
      public RoleAssignments get() {
        checkCanViewUserDetails(myUser, myContext.getServiceLocator());
        return new RoleAssignments(myUser.getRoles(), myUser, myContext);
      }
    });
  }

  @XmlElement(name = "groups")
  public Groups getGroups() {
    return ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("groups", false), new ValueWithDefault.Value<Groups>() {
      public Groups get() {
        checkCanViewUserDetails(myUser, myContext.getServiceLocator());
        return new Groups(myUser.getUserGroups(), myFields.getNestedField("groups", Fields.NONE, Fields.LONG), myContext);
      }
    });
  }

  @XmlAttribute
  public String getRealm() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("realm", false), myUser.getRealm());
  }

  @XmlElement(name = "properties")
  public Properties getProperties() {
    return ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("properties", false), new ValueWithDefault.Value<Properties>() {
      public Properties get() {
        checkCanViewUserDetails(myUser, myContext.getServiceLocator());
        return new Properties(getProperties(myUser), UserRequest.getPropertiesHref(myUser),myFields.getNestedField("properties", Fields.NONE, Fields.LONG));
      }
    });
  }

  public static Map<String, String> getProperties(final PropertyHolder holder) {
    Map<String, String> convertedProperties = new HashMap<String, String>();
    for (Map.Entry<PropertyKey, String> prop : holder.getProperties().entrySet()) {
      convertedProperties.put(prop.getKey().getKey(), prop.getValue());
    }
    return convertedProperties;
  }

  public static String getFieldValue(@NotNull final SUser user, @Nullable final String name, @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Field name cannot be empty");
    }
    if ("id".equals(name)) {
      return String.valueOf(user.getId());
    } else if ("name".equals(name)) {
      return user.getName();
    } else if ("username".equals(name)) {
      return user.getUsername();
    } else if ("email".equals(name)) {
      checkCanViewUserDetails(user, serviceLocator);
      return user.getEmail();
    }
    throw new BadRequestException("Unknown field '" + name + "'. Supported fields are: id, name, username, email");
  }

  @Nullable
  public static String setFieldValue(@NotNull final SUser user, @Nullable final String name, @NotNull final String value, @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Field name cannot be empty");
    }
    if ("username".equals(name)) {
      DataUpdater.updateUserCoreFields(user, value, null, null, null);
      return getFieldValue(user, name, serviceLocator);
    } else if ("name".equals(name)) {
      DataUpdater.updateUserCoreFields(user, null, value, null, null);
      return getFieldValue(user, name, serviceLocator);
    } else if ("email".equals(name)) {
      DataUpdater.updateUserCoreFields(user, null, null, value, null);
      return getFieldValue(user, name, serviceLocator);
    } else if ("password".equals(name)) {
      DataUpdater.updateUserCoreFields(user, null, null, null, value);
      return null; //do not report password back
    }
    throw new BadRequestException("Changing field '" + name + "' is not supported. Supported fields are: username, name, email, password");
  }

  /**
   * Is only used for posting
   */
  @XmlAttribute
  public String getPassword() {
    return null;
  }

  // These are necessary for allowing to submit the same class
  private Long id;
  private String name;
  private String username;
  private String email;
  private String password;
  private RoleAssignments roles;
  private Groups groups;
  private Properties properties;

  public void setId(final Long id) {
    this.id = id;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public void setEmail(final String email) {
    this.email = email;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public void setRoles(final RoleAssignments roles) {
    this.roles = roles;
  }

  public void setGroups(final Groups groups) {
    this.groups = groups;
  }

  public void setProperties(final Properties properties) {
    this.properties = properties;
  }

  public String getSubmittedName() {
    return name;
  }

  public String getSubmittedUsername() {
    return username;
  }

  public String getSubmittedEmail() {
    return email;
  }

  public String getSubmittedPassword() {
    return password;
  }

  public RoleAssignments getSubmittedRoles() {
    return roles;
  }

  public Groups getSubmittedGroups() {
    return groups;
  }

  public Properties getSubmittedProperties() {
    return properties;
  }

  @NotNull
  public jetbrains.buildServer.users.SUser getFromPosted(final UserFinder userFinder) {
    if (id != null){
      return userFinder.getUser(Locator.getStringLocator(UserFinder.ID, String.valueOf(id)));
    }
    if (username != null){
      return userFinder.getUser(Locator.getStringLocator(UserFinder.USERNAME, username));
    }

    throw new BadRequestException("Submitted user should have wither 'id; or 'username' attributes");
  }
}

