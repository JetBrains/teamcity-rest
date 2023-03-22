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
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = VersionedSettingsContextParameter.TYPE)
@ModelDescription(
  value = "Represents a Versioned Settings Context Parameter.",
  externalArticleLink = "https://www.jetbrains.com/help/teamcity/storing-project-settings-in-version-control.html",
  externalArticleName = "Storing Project Settings in Version Control"
)
public class VersionedSettingsContextParameter {

  static final String TYPE = "versionedSettingsContextParameter";

  private String myName;

  private String myValue;


  @SuppressWarnings("unused")
  public VersionedSettingsContextParameter() {
  }

  public VersionedSettingsContextParameter(@NotNull String name, @NotNull String value) {
    myName = name;
    myValue = value;
  }

  @XmlAttribute(name = "name")
  public String getName() {
    return myName;
  }

  @XmlAttribute(name = "value")
  public String getValue() {
    return myValue;
  }

}
