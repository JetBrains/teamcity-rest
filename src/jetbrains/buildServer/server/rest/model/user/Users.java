/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.UserFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "users")
@XmlType(name = "users")
public class Users {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "user")
  public List<User> users;
  private ApiUrlBuilder myApiUrlBuilder;

  public Users() {
  }

  public Users(Collection<SUser> userObjects, @NotNull final Fields fields, @NotNull final BeanContext context) {
    if (fields.isIncluded("user", false, true)) {
      users = new ArrayList<User>(userObjects.size());
      final Fields nestedFields = fields.getNestedField("user");
      for (jetbrains.buildServer.users.SUser user : userObjects) {
        users.add(new User(user, nestedFields, context));
      }
    }
    count = userObjects == null ? null : ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), userObjects.size());
  }

  @NotNull
  public Collection<SUser> getFromPosted(@NotNull final UserFinder userFinder) {
    if (users == null){
      return Collections.emptyList();
    }
    return users.stream().map(user -> user.getFromPosted(userFinder)).collect(Collectors.toList());
  }
}