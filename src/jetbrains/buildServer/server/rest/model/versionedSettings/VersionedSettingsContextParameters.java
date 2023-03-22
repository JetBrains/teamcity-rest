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


import java.util.*;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import org.jetbrains.annotations.NotNull;


@XmlRootElement(name = "versionedSettingsContextParameters")
@ModelDescription(
  value = "Represents a Versioned Settings Context Parameters.",
  externalArticleLink = "https://www.jetbrains.com/help/teamcity/storing-project-settings-in-version-control.html",
  externalArticleName = "Storing Project Settings in Version Control"
)
@ModelBaseType(ObjectType.LIST)
public class VersionedSettingsContextParameters {

  private Collection<VersionedSettingsContextParameter> myParameters;


  @SuppressWarnings("unused")
  public VersionedSettingsContextParameters() {
  }


  public VersionedSettingsContextParameters(@NotNull Map<String, String> parameters) {
    myParameters = parameters.entrySet().stream()
                             .map(entry -> new VersionedSettingsContextParameter(entry.getKey(), entry.getValue()))
                             .collect(Collectors.toSet());
  }


  public Map<String, String> toMap() {
    Map<String, String> result = new HashMap<>();
    myParameters.forEach(parameter -> result.put(parameter.getName(), parameter.getValue()));
    return result;
  }

  @XmlElement(name = VersionedSettingsContextParameter.TYPE)
  @NotNull
  public Collection<VersionedSettingsContextParameter> getParameters() {
    return myParameters;
  }

}
