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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.serverSide.versionedSettings.SecureValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@XmlRootElement(name = VersionedSettingsToken.TYPE)
@ModelDescription(
  value = "Represents a Versioned Settings Token.",
  externalArticleLink = "https://www.jetbrains.com/help/teamcity/storing-project-settings-in-version-control.html#Managing+Tokens",
  externalArticleName = "Managing Tokens"
)
public class VersionedSettingsToken {

  static final String TYPE = "versionedSettingsToken";

  private String myName;

  private String myValue;

  private String myDescription;

  @SuppressWarnings("unused")
  public VersionedSettingsToken() {
  }

  public VersionedSettingsToken(@NotNull String name,
                                @Nullable SecureValue secureValue) {
    myName = name;
    if (secureValue != null) {
      myValue = secureValue.getSecureValue();
      myDescription = secureValue.getDescription();
    }
  }

  public VersionedSettingsToken removeValue() {
    myValue = null;
    return this;
  }

  @XmlAttribute(name = "name")
  public String getName() {
    return myName;
  }

  @XmlAttribute(name = "value")
  public String getValue() {
    return myValue;
  }

  @XmlAttribute(name = "description")
  public String getDescription() {
    return myDescription;
  }

  public void setName(String name) {
    myName = name;
  }

  public void setValue(String value) {
    myValue = value;
  }

}
