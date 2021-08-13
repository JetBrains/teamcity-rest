/*
 * Copyright 2000-2021 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "userAvatars")
@XmlType(name = "userAvatars")
@ModelDescription("Represents a group of links to the user's avatars")
public class UserAvatars {

  @XmlAttribute
  public String urlToSize20;

  @XmlAttribute
  public String urlToSize32;

  @XmlAttribute
  public String urlToSize40;

  @XmlAttribute
  public String urlToSize64;

  public UserAvatars setUrlToSize32(@Nullable String urlToSize32) {
    this.urlToSize32 = urlToSize32;
    return this;
  }

  public UserAvatars setUrlToSize20(@Nullable String urlToSize20) {
    this.urlToSize20 = urlToSize20;
    return this;
  }

  public UserAvatars setUrlToSize40(@Nullable String urlToSize40) {
    this.urlToSize40 = urlToSize40;
    return this;
  }

  public UserAvatars setUrlToSize64(@Nullable String urlToSize64) {
    this.urlToSize64 = urlToSize64;
    return this;
  }
}
