/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "user-ref")
@XmlType(name = "user-ref")
public class UserRef {
  @NotNull private jetbrains.buildServer.users.User myUser;
  private ApiUrlBuilder myApiUrlBuilder;

  public UserRef() {
  }

  public UserRef(@NotNull jetbrains.buildServer.users.User user, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    myUser = user;
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
  public String getHref() {
    return myApiUrlBuilder.getHref(myUser);
  }
}