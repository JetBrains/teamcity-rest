/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.data.DataUpdater;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.finder.impl.UserFinder;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.group.Groups;
import jetbrains.buildServer.server.rest.request.UserRequest;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.TwoFactorPasswordManager;
import jetbrains.buildServer.serverSide.impl.auth.ServerAuthUtil;
import jetbrains.buildServer.users.*;
import jetbrains.buildServer.users.impl.UserImpl;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 12.04.2009
 */
@XmlRootElement(name = "user")
@XmlType(name = "user", propOrder = {"username", "name", "id", "email", "lastLogin", "password", "hasPassword", "realm" /*obsolete*/, "href",
  "properties", "roles", "groups",
  "locator"/*only when triggering*/, "avatars",
  "enabled2FA"
})
@ModelDescription(
    value = "Represents a user.",
    externalArticleLink = "https://www.jetbrains.com/help/teamcity/user-account.html",
    externalArticleName = "User Account"
)
public class User {
  @Nullable
  public final SUser myUser;
  @NotNull
  private final Long myUserId;
  private Fields myFields;
  private BeanContext myContext;
  private boolean myCanViewDetails;
  private UserAvatarsManager myUserAvatarsManager;
  private TwoFactorPasswordManager myTwoFactorPasswordManager;

  public User() {
    myUser = null;
    myUserId = 0L;
    myCanViewDetails = false;
  }

  public User(long userId, @NotNull final Fields fields, @NotNull final BeanContext context) {
    myUserId = userId;
    myUser = context.getSingletonService(UserModel.class).findUserById(userId);
    myFields = fields;
    myContext = context;
    myUserAvatarsManager = context.getSingletonService(UserAvatarsManager.class);
    myTwoFactorPasswordManager = context.getSingletonService(TwoFactorPasswordManager.class);
    initCanViewDetails();
  }

  public User(@NotNull jetbrains.buildServer.users.User user, @NotNull final Fields fields, @NotNull final BeanContext context) {
    myUser = (SUser)user;
    myUserId = myUser.getId();
    myFields = fields;
    myContext = context;
    myUserAvatarsManager = context.getSingletonService(UserAvatarsManager.class);
    myTwoFactorPasswordManager = context.getSingletonService(TwoFactorPasswordManager.class);
    initCanViewDetails();
  }


  private void initCanViewDetails() {
    try {
      checkCanViewUserDetails(myUser, myContext.getServiceLocator());
      myCanViewDetails = true;
    } catch (AuthorizationFailedException | NotFoundException e) {
      myCanViewDetails = false;
    }
  }

  private static void checkCanViewUserDetails(@Nullable final SUser user, @NotNull final ServiceLocator context) {
    context.getSingletonService(UserFinder.class).checkViewUserPermission(user);   //until http://youtrack.jetbrains.net/issue/TW-20071 is fixed
    if (TeamCityProperties.getBoolean("rest.beans.user.checkPermissions.limitViewUserProfileToListableUsersOnly")) { // related to TW-51644
      // see AdminEditUserController for related code
      if (user != null) {
        if (ServerAuthUtil.canViewUser(context.getSingletonService(PermissionChecker.class).getCurrent(), user)) {
          return;
        }
        throw new AuthorizationFailedException("No permission to view full detail of user with id \"" + user.getId() + "\"");
      }
      context.getSingletonService(PermissionChecker.class).checkGlobalPermissionAnyOf(new Permission[]{Permission.VIEW_ALL_USERS, Permission.CHANGE_USER});
    }
  }

  private void checkCanViewUserDetails() {
    if (!myCanViewDetails) {
      throw new AuthorizationFailedException("No permission to view full detail of user with id \"" + myUserId + "\"");
    }
  }

  @XmlAttribute
  public Long getId() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("id"), myUserId);
  }

  @XmlAttribute
  public String getName() {
    return myUser == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("name"), () -> {
      return StringUtil.isEmpty(myUser.getName()) ? null : myUser.getName();
    });
  }

  @XmlAttribute
  public String getUsername() {
    return myUser == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("username"), () -> {
      return myUser.getUsername();
    });
  }

  @XmlAttribute
  public String getLastLogin() {
    return myUser == null ? null : ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("lastLogin", false), () -> {
      checkCanViewUserDetails();
      Date lastLoginTimestamp = myUser.getLastLoginTimestamp();
      if (lastLoginTimestamp != null) {
        return Util.formatTime(lastLoginTimestamp);
      }
      return null;
    });
  }

  @XmlAttribute
  public String getHref() {
    return myUser == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("href"), myContext.getApiUrlBuilder().getHref(myUser));
  }

  @XmlAttribute
  public String getEmail() {
    return myUser == null ? null : ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("email", false), () -> {
      checkCanViewUserDetails();
      return StringUtil.isEmpty(myUser.getEmail()) ? null : myUser.getEmail();
    });
  }

  @XmlElement(name = "avatars")
  public UserAvatars getAvatars() {
    return myUser == null ? null : ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("avatars", false), () -> {
      final UserAvatarsManager.Avatars avatars = myUserAvatarsManager.getAvatars(myUser);
      if (!avatars.exists()) {
        return null;
      }

      final Fields avatarsField = myFields.getNestedField("avatars");
      if (avatarsField.getFieldsSpec().isEmpty()) {
        return new UserAvatars()
          .setUrlToSize20(avatars.getUrlToSize(20))
          .setUrlToSize28(avatars.getUrlToSize(28))
          .setUrlToSize32(avatars.getUrlToSize(32))
          .setUrlToSize40(avatars.getUrlToSize(40))
          .setUrlToSize56(avatars.getUrlToSize(56))
          .setUrlToSize64(avatars.getUrlToSize(64))
          .setUrlToSize80(avatars.getUrlToSize(80));
      } else {
        final UserAvatars userAvatars = new UserAvatars();

        final Boolean urlToSize20 = avatarsField.isIncluded("urlToSize20");
        if (urlToSize20 != null && urlToSize20) userAvatars.setUrlToSize20(avatars.getUrlToSize(20));

        final Boolean urlToSize28 = avatarsField.isIncluded("urlToSize28");
        if (urlToSize28 != null && urlToSize28) userAvatars.setUrlToSize28(avatars.getUrlToSize(28));

        final Boolean urlToSize32 = avatarsField.isIncluded("urlToSize32");
        if (urlToSize32 != null && urlToSize32) userAvatars.setUrlToSize32(avatars.getUrlToSize(32));

        final Boolean urlToSize40 = avatarsField.isIncluded("urlToSize40");
        if (urlToSize40 != null && urlToSize40) userAvatars.setUrlToSize40(avatars.getUrlToSize(40));

        final Boolean urlToSize56 = avatarsField.isIncluded("urlToSize56");
        if (urlToSize56 != null && urlToSize56) userAvatars.setUrlToSize56(avatars.getUrlToSize(56));

        final Boolean urlToSize64 = avatarsField.isIncluded("urlToSize64");
        if (urlToSize64 != null && urlToSize64) userAvatars.setUrlToSize64(avatars.getUrlToSize(64));

        final Boolean urlToSize80 = avatarsField.isIncluded("urlToSize80");
        if (urlToSize80 != null && urlToSize80) userAvatars.setUrlToSize80(avatars.getUrlToSize(80));

        return userAvatars;
      }
    });
  }

  @XmlAttribute(name = "enabled2FA")
  public Boolean getEnabled2FA() {
    return myUser == null ? null : ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("enabled2FA", false), () -> {
      checkCanViewUserDetails();
      return myTwoFactorPasswordManager.hasEnabled2FA(myUser);
    });
  }

  @XmlAttribute
  public Boolean getHasPassword() {
    return myUser == null ? null : ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("hasPassword", false, false), () -> {
      checkCanViewUserDetails();
      return ((UserImpl)myUser).hasPassword();
    });
  }

  @XmlElement(name = "roles")
  public RoleAssignments getRoles() {
    return myUser == null ? null : ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("roles", false), () -> {
      checkCanViewUserDetails();
      return new RoleAssignments(myUser.getRoles(), myUser, myContext);
    });
  }

  @XmlElement(name = "groups")
  public Groups getGroups() {
    return myUser == null ? null : ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("groups", false), () -> {
      checkCanViewUserDetails();
      return new Groups(myUser.getUserGroups(), myFields.getNestedField("groups", Fields.NONE, Fields.LONG), myContext);
    });
  }

  @XmlAttribute
  public String getRealm() {
    return myUser == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("realm", false), myUser.getRealm());
  }

  @XmlElement(name = "properties")
  public Properties getProperties() {
    return myUser == null ? null : ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("properties", false), () -> {
      checkCanViewUserDetails();
      return new Properties(getProperties(myUser), UserRequest.getPropertiesHref(myUser),myFields.getNestedField("properties", Fields.NONE, Fields.LONG), myContext);
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

  public static void deleteField(@NotNull final SUser user, @Nullable final String name) {
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Field name cannot be empty");
    }
    if ("name".equals(name)) {
      user.updateUserAccount(user.getUsername(), null, user.getEmail());
      return;
    } else if ("email".equals(name)) {
      user.updateUserAccount(user.getUsername(), user.getName(), null);
      return;
    } else if ("password".equals(name)) {
      user.setPassword(null);
      return;
    }
    throw new BadRequestException("Setting field '" + name + "' to 'null' is not supported. Supported fields are: name, email, password");
  }

  /**
   * Is only used for posting
   */
  @XmlAttribute
  public String getPassword() {
    return null;
  }

  /**
   * Is only used for posting
   */
  @XmlAttribute
  public String getLocator() {
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
  private String submittedLocator;

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

  public void setLocator(final String locator) {
    submittedLocator = locator;
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
  public SUser getFromPosted(final UserFinder userFinder) {
    if (submittedLocator != null) {
      if (id != null) {
        throw new BadRequestException("Both 'locator' and '" + "id" + "' attributes are specified. Only one should be present.");
      }
      if (username != null) {
        throw new BadRequestException("Both 'locator' and '" + "username" + "' attributes are specified. Only one should be present.");
      }
      return userFinder.getItem(submittedLocator, true);
    }

    if (id != null){
      return userFinder.getItem(UserFinder.getLocatorById(id));
    }
    if (username != null){
      return userFinder.getItem(UserFinder.getLocatorByUsername(username));
    }

    throw new BadRequestException("Submitted user should have 'id', 'username' or 'locator' attributes");
  }
}
