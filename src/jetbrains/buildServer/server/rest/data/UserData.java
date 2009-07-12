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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Yegor.Yarko
 *         Date: 12.07.2009
 */
@XmlRootElement(name = "user")
public class UserData {

  @XmlAttribute
  public String name;

  @XmlAttribute
  public String username;

  @XmlAttribute
  public String email;

  @XmlAttribute
  public String password;

  @XmlElement(name = "roles")
  public RoleAssignments roles;

  @XmlElement(name = "groups")
  public Groups groups;

  @XmlAttribute
  public String realm;

  @XmlElement(name = "properties")
  public Properties properties;

  public UserData() {
  }
}
