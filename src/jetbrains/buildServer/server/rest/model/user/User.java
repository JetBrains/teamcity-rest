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
import jetbrains.buildServer.server.rest.data.UserFinder;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.group.Groups;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.impl.auth.ServerAuthUtil;
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
@XmlType(name="user", propOrder = {"username", "name", "id", "email", "lastLogin", "href",
"properties", "roles", "groups"})
public class User {
  private SUser myUser;
  private BeanContext myContext;

  public User() {
  }

  public User(SUser user, @NotNull final BeanContext context) {
    this.myUser = user;
    myContext = context;
  }

  private static boolean isCanViewUserDetails(@Nullable final SUser user, @NotNull final ServiceLocator context) {
    try {
      context.getSingletonService(UserFinder.class).checkViewUserPermission(user == null ? "" : "id:" + user.getId());   //until http://youtrack.jetbrains.net/issue/TW-20071 is fixed
      if (TeamCityProperties.getBoolean("rest.beans.user.checkPermissions.limitViewUserProfileToListableUsersOnly")) { // related to TW-51644
        // see AdminEditUserController for related code
        final AuthorityHolder currentUser = context.getSingletonService(SecurityContext.class).getAuthorityHolder();
        if (user != null) {
          return ServerAuthUtil.canViewUser(currentUser, user);
        }
        return currentUser.isPermissionGrantedGlobally(Permission.VIEW_ALL_USERS) ||
               currentUser.isPermissionGrantedGlobally(Permission.CHANGE_USER);
      }
    } catch (RuntimeException e) {
      return false;
    }
    return true;
  }

  @XmlAttribute
  public Long getId() {
    return myUser.getId();
  }

  @XmlAttribute
  public String getName() {
    return myUser.getName();
  }

  @XmlAttribute
  public String getUsername() {
    return myUser.getUsername();
  }

  @XmlAttribute
  public String getLastLogin() {
    if (!isCanViewUserDetails(myUser, myContext.getSingletonService(ServiceLocator.class))) {
      return null;
    }
    Date lastLoginTimestamp = myUser.getLastLoginTimestamp();
    if (lastLoginTimestamp != null) {
      return Util.formatTime(lastLoginTimestamp);
    }
    return null;
  }

  @XmlAttribute
  public String getHref() {
    return myContext.getContextService(ApiUrlBuilder.class).getHref(myUser);
  }

  @XmlAttribute
  public String getEmail() {
    if (!isCanViewUserDetails(myUser, myContext.getSingletonService(ServiceLocator.class))) {
      return null;
    }
    return myUser.getEmail();
  }

  @XmlElement(name = "roles")
  public RoleAssignments getRoles() {
    if (!isCanViewUserDetails(myUser, myContext.getSingletonService(ServiceLocator.class))) {
      return null;
    }
    return new RoleAssignments(myUser.getRoles(), myUser, myContext);
  }

  @XmlElement(name = "groups")
  public Groups getGroups() {
    if (!isCanViewUserDetails(myUser, myContext.getSingletonService(ServiceLocator.class))) {
      return null;
    }
    return new Groups(myUser.getUserGroups(), myContext.getContextService(ApiUrlBuilder.class));
  }

  @XmlAttribute
  public String getRealm() {
    return myUser.getRealm();
  }

  @XmlElement(name = "properties")
  public Properties getProperties() {
    if (!isCanViewUserDetails(myUser, myContext.getSingletonService(ServiceLocator.class))) {
      return null;
    }
    return new Properties(getUserProperties(myUser));
  }

  public static Map<String, String> getUserProperties(final SUser user) {
    Map<String, String> convertedProperties = new HashMap<String, String>();
    for (Map.Entry<PropertyKey, String> prop : user.getProperties().entrySet()) {
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
      if (!isCanViewUserDetails(user, serviceLocator)) {
        throw new AuthorizationFailedException("No permission to view full detail of user with id \"" + user.getId() + "\"");
      }
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

  // These are necessary for allowing to submit the same class
  private String name;
  private String username;
  private String email;
  private String password;
  private RoleAssignments roles;
  private Groups groups;
  private Properties properties;

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
}

