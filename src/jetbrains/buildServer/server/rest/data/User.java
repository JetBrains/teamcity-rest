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

package jetbrains.buildServer.server.rest.data;

import java.text.SimpleDateFormat;
import java.util.Date;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * User: Yegor Yarko
 * Date: 12.04.2009
 */
@XmlRootElement(name = "user")
public class User {
  @XmlAttribute
  public Long id;
  @XmlAttribute
  public String name;
  @XmlAttribute
  public String username;
  @XmlAttribute
  public String lastLogin;
  @XmlAttribute
  public String email;
  @XmlElement(name = "roles")
  public RoleAssignments roleAssignments;
  @XmlElement(name = "groups")
  public Groups groups;

  public User() {
  }

  public User(jetbrains.buildServer.users.SUser user) {
    id = user.getId();
    name = user.getName();
    username = user.getUsername();
    Date lastLoginTimestamp = user.getLastLoginTimestamp();
    if (lastLoginTimestamp != null) {
      lastLogin = (new SimpleDateFormat("yyyyMMdd'T'HHmmssZ")).format(lastLoginTimestamp);
    }
    email = user.getEmail();
    roleAssignments = new RoleAssignments(user.getRoles());
    groups = new Groups(user.getUserGroups());
  }
}

