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

package jetbrains.buildServer.server.rest.model.user;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataUpdater;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.group.Groups;
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
public class User {
  private SUser myUser;
  private ApiUrlBuilder myApiUrlBuilder;

  public User() {
  }

  public User(jetbrains.buildServer.users.SUser user, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    this.myUser = user;
    myApiUrlBuilder = apiUrlBuilder;
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
    Date lastLoginTimestamp = myUser.getLastLoginTimestamp();
    if (lastLoginTimestamp != null) {
      return Util.formatTime(lastLoginTimestamp);
    }
    return null;
  }

  @XmlAttribute
  public String getEmail() {
    return myUser.getEmail();
  }

  @XmlElement(name = "roles")
  public RoleAssignments getRoleAssignments() {
    return new RoleAssignments(myUser.getRoles(), myUser, myApiUrlBuilder);
  }

  @XmlElement(name = "groups")
  public Groups getGroups() {
    return new Groups(myUser.getUserGroups(), myApiUrlBuilder);
  }

  @XmlAttribute
  public String getRealm() {
    return myUser.getRealm();
  }

  @XmlElement(name = "properties")
  public Properties getProperties() {
    return new Properties(getUserProperties(myUser));
  }

  public static Map<String, String> getUserProperties(final SUser user) {
    Map<String, String> convertedProperties = new HashMap<String, String>();
    for (Map.Entry<PropertyKey, String> prop : user.getProperties().entrySet()) {
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

  public static void setFieldValue(@NotNull final SUser user, @Nullable final String name, @NotNull final String value) {
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Field name cannot be empty");
    }
    if ("username".equals(name)) {
      DataUpdater.updateUserCoreFields(user, value, null, null, null);
      return;
    } else if ("name".equals(name)) {
      DataUpdater.updateUserCoreFields(user, null, value, null, null);
      return;
    } else if ("email".equals(name)) {
      DataUpdater.updateUserCoreFields(user, null, null, value, null);
      return;
    } else if ("password".equals(name)) {
      DataUpdater.updateUserCoreFields(user, null, null, null, value);
      return;
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

