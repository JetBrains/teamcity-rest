/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.change;

import java.util.Collection;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.user.Users;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "commiter")
@XmlType(name = "commiter", propOrder = {
  "vcsUsername",
  "users"
})
@ModelDescription(
  value = "Represents a commiter to a VCS."
)
public class Commiter {
  private String myVCSUsername;
  private Collection<SUser> myNonCheckedUsers;
  private Fields myFields;
  private BeanContext myBeanContext;

  public Commiter() { }

  public Commiter(@NotNull Fields fields, @Nullable String VCSUsername, @NotNull Collection<SUser> users, @NotNull BeanContext context) {
    myVCSUsername = VCSUsername;
    myFields = fields;
    myNonCheckedUsers = users;
    myBeanContext = context;
  }

  @XmlAttribute(name = "vcsUsername")
  public String getVcsUsername() {
    if(myFields.isIncluded("vcsUsername", true, true)) {
      return myVCSUsername;
    }
    return null;
  }

  @XmlElement(name="users")
  public Users getUsers() {
    if(myFields.isIncluded("users", false, true)) {
      return new Users(myNonCheckedUsers, myFields.getNestedField("users"), myBeanContext);
    }
    return null;
  }
}