/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.versionedSettings;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import org.jetbrains.annotations.NotNull;


@XmlRootElement(name = "versionedSettingsTokens")
@ModelDescription(
  value = "Represents a Versioned Settings Tokens.",
  externalArticleLink = "https://www.jetbrains.com/help/teamcity/storing-project-settings-in-version-control.html#Managing+Tokens",
  externalArticleName = "Managing Tokens"
)
@ModelBaseType(ObjectType.LIST)
public class VersionedSettingsTokens {

  private List<VersionedSettingsToken> myTokens;


  @SuppressWarnings("unused")
  public VersionedSettingsTokens() {
  }

  public VersionedSettingsTokens(@NotNull List<VersionedSettingsToken> tokens) {
    myTokens = tokens;
  }

  @XmlElement(name = VersionedSettingsToken.TYPE)
  public List<VersionedSettingsToken> getTokens() {
    return myTokens;
  }
}
