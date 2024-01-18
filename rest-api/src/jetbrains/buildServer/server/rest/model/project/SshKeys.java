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

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.user.PermissionAssignment;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;

/**
 * @author Vladimir Shefer
 * @date 21.11.2022
 */
@XmlRootElement(name = "sshKeys")
@XmlType(name = "sshKeys")
@ModelDescription(
  value = "Represents ssh key list.",
  externalArticleLink = "https://www.jetbrains.com/help/teamcity/ssh-keys-management.html#Uploading+SSH+Key+to+TeamCity+Server",
  externalArticleName = "Uploading SSH Key to TeamCity Server"
)
public class SshKeys {
  private List<SshKey> mySshKeys;

  @XmlElement(name = "sshKey")
  public List<SshKey> getSshKeys() {
    return mySshKeys;
  }

  public void setSshKeys(List<SshKey> sshKeys) {
    mySshKeys = sshKeys;
  }
}