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

package jetbrains.buildServer.server.rest.model.user;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataUpdater;
import jetbrains.buildServer.server.rest.data.UserFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.group.Groups;
import jetbrains.buildServer.server.rest.request.UserRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.users.PropertyHolder;
import jetbrains.buildServer.users.PropertyKey;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.UserModel;
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
  "locator"/*only when triggering*/})
public class User {
  @Nullable
  private SUser myUser;
  @NotNull
  private final Long myUserId;
  private Fields myFields;
  private BeanContext myContext;

  public User() {
    myUserId = 0L;
  }

  public User(long userId, @NotNull final Fields fields, @NotNull final BeanContext context) {
    myUserId = userId;
    myUser = context.getSingletonService(UserModel.class).findUserById(userId);
    myFields = fields;
    myContext = context;
  }

  public User(@NotNull jetbrains.buildServer.users.User user, @NotNull final Fields fields, @NotNull final BeanContext context) {
    myUser = (SUser)user;
    myUserId = myUser.getId();
    myFields = fields;
    myContext = context;
  }

  @XmlAttribute
  public Long getId() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("id"), myUserId);
  }

  @XmlAttribute
  public String getName() {
    return myUser == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("name"), StringUtil.isEmpty(myUser.getName()) ? null : myUser.getName());
  }

  @XmlAttribute
  public String getUsername() {
    return myUser == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("username"), myUser.getUsername());
  }

  @XmlAttribute
  public String getLastLogin() {
    return myUser == null ? null : ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("lastLogin", false), new ValueWithDefault.Value<String>() {
      public String get() {
        myContext.getSingletonService(UserFinder.class).checkViewUserPermission(myUser);
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
    return myUser == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("href"), myContext.getContextService(ApiUrlBuilder.class).getHref(myUser));
  }

  @XmlAttribute
  public String getEmail() {
    return myUser == null ? null : ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("email", false), new ValueWithDefault.Value<String>() {
      public String get() {
        return StringUtil.isEmpty(myUser.getEmail()) ? null : myUser.getEmail();
      }
    });
  }

  @XmlAttribute
  public Boolean getHasPassword() {
    return myUser == null ? null : ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("hasPassword", false, false), new ValueWithDefault.Value<Boolean>() {
      @Nullable
      @Override
      public Boolean get() {
        return ((UserImpl)myUser).hasPassword();
      }
    });
  }

  @XmlElement(name = "roles")
  public RoleAssignments getRoles() {
    return myUser == null ? null : ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("roles", false), new ValueWithDefault.Value<RoleAssignments>() {
      public RoleAssignments get() {
        myContext.getSingletonService(UserFinder.class).checkViewUserPermission(myUser); //until http://youtrack.jetbrains.net/issue/TW-20071 is fixed
        return new RoleAssignments(myUser.getRoles(), myUser, myContext);
      }
    });
  }

  @XmlElement(name = "groups")
  public Groups getGroups() {
    return myUser == null ? null : ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("groups", false), new ValueWithDefault.Value<Groups>() {
      public Groups get() {
        myContext.getSingletonService(UserFinder.class).checkViewUserPermission(myUser); //until http://youtrack.jetbrains.net/issue/TW-20071 is fixed
        return new Groups(myUser.getUserGroups(), myFields.getNestedField("groups", Fields.NONE, Fields.LONG), myContext);
      }
    });
  }

  @XmlAttribute
  public String getRealm() {
    return myUser == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("realm", false), myUser.getRealm());
  }

  @XmlElement(name = "properties")
  public Properties getProperties() {
    return myUser == null ? null : ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("properties", false), new ValueWithDefault.Value<Properties>() {
      public Properties get() {
        return new Properties(getProperties(myUser), UserRequest.getPropertiesHref(myUser),myFields.getNestedField("properties", Fields.NONE, Fields.LONG),
                              myContext.getServiceLocator());
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

  public static String getFieldValue(@NotNull final SUser user, @Nullable final String name) {
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
      return user.getEmail();
    }
    throw new BadRequestException("Unknown field '" + name + "'. Supported fields are: id, name, username, email");
  }

  @Nullable
  public static String setFieldValue(@NotNull final SUser user, @Nullable final String name, @NotNull final String value) {
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Field name cannot be empty");
    }
    if ("username".equals(name)) {
      DataUpdater.updateUserCoreFields(user, value, null, null, null);
      return getFieldValue(user, name);
    } else if ("name".equals(name)) {
      DataUpdater.updateUserCoreFields(user, null, value, null, null);
      return getFieldValue(user, name);
    } else if ("email".equals(name)) {
      DataUpdater.updateUserCoreFields(user, null, null, value, null);
      return getFieldValue(user, name);
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
      return userFinder.getItem(submittedLocator);
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
