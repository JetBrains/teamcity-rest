/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.user;

import java.text.SimpleDateFormat;
import java.util.Date;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.data.Properties;
import jetbrains.buildServer.server.rest.data.group.Groups;
import jetbrains.buildServer.users.SUser;

/**
 * User: Yegor Yarko
 * Date: 12.04.2009
 */
@XmlRootElement(name = "user")
public class User {
  private SUser myUser;

  public User() {
  }

  public User(jetbrains.buildServer.users.SUser user) {
    this.myUser = user;
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
      return (new SimpleDateFormat("yyyyMMdd'T'HHmmssZ")).format(lastLoginTimestamp);
    }
    return null;
  }

  @XmlAttribute
  public String getEmail() {
    return myUser.getEmail();
  }

  @XmlElement(name = "roles")
  public RoleAssignments getRoleAssignments() {
    return new RoleAssignments(myUser.getRoles(), myUser);
  }

  @XmlElement(name = "groups")
  public Groups getGroups() {
    return new Groups(myUser.getUserGroups());
  }

  @XmlAttribute
  public String getRealm() {
    return myUser.getRealm();
  }

  @XmlElement(name = "properties")
  public Properties getProperties() {
    Properties result = new Properties();
    result.init(myUser.getProperties());
    return result;
  }
}

