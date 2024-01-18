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

package jetbrains.buildServer.server.rest.model.project;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;

/**
 * @author Vladimir Shefer
 * @date 21.11.2022
 */
@XmlRootElement(name = "sshKey")
@XmlType(name = "sshKey")
@ModelDescription(
  value = "Represents ssh key.",
  externalArticleLink = "https://www.jetbrains.com/help/teamcity/ssh-keys-management.html#Uploading+SSH+Key+to+TeamCity+Server",
  externalArticleName = "Uploading SSH Key to TeamCity Server"
)
public class SshKey {

  private String myName;

  private Boolean myIsEncrypted;

  private String myPublicKey;

  @XmlAttribute
  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  @XmlAttribute
  public Boolean getEncrypted() {
    return myIsEncrypted;
  }

  public void setEncrypted(Boolean encrypted) {
    myIsEncrypted = encrypted;
  }

  @XmlAttribute
  public String getPublicKey() {
    return myPublicKey;
  }

  public void setPublicKey(String publicKey) {
    myPublicKey = publicKey;
  }
}