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
import jetbrains.buildServer.server.rest.UserRequest;
import org.jetbrains.annotations.NotNull;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
public class UserRef {
  @NotNull private jetbrains.buildServer.users.User myUser;

  public UserRef() {
  }

  public UserRef(@NotNull jetbrains.buildServer.users.User user) {
    myUser = user;
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
  public String getHref() {
    return UserRequest.getUserHref(myUser);
  }
}