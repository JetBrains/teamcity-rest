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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "users")
public class Users {
  @XmlElement(name = "user")
  public List<UserRef> users;
  private ApiUrlBuilder myApiUrlBuilder;

  public Users() {
  }

  public Users(Collection<jetbrains.buildServer.users.User> userObjects, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    myApiUrlBuilder = apiUrlBuilder;
    users = new ArrayList<UserRef>(userObjects.size());
    for (jetbrains.buildServer.users.User user : userObjects) {
      users.add(new UserRef(user, myApiUrlBuilder));
    }
  }
}