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

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.controllers.project.VersionedSettingsBean;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "versionedSettingsStatus")
@ModelDescription(
  value = "Represents a Versioned Settings Status.",
  externalArticleLink = "https://www.jetbrains.com/help/teamcity/storing-project-settings-in-version-control.html",
  externalArticleName = "Storing Project Settings in Version Control"
)
public class VersionedSettingsStatus {

  private StatusType myType;

  private String myTimestamp;

  private String myMessage;

  private Collection<String> myMissingContextParameters;

  private List<VersionedSettingsError> myErrors;

  private Boolean myDslOutdated;


  @SuppressWarnings("unused")
  public VersionedSettingsStatus() {
  }

  public VersionedSettingsStatus(@NotNull VersionedSettingsBean.VersionedSettingsStatusBean versionedSettingsStatus,
                                 @NotNull Fields fields) {
    myType = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("type"),
                                                     versionedSettingsStatus.isWarn() ? StatusType.warn : StatusType.info);
    myTimestamp = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("timestamp"), versionedSettingsStatus.getTimestamp().toString());
    myMessage = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("message"),
                                                        versionedSettingsStatus.getDescription() +
                                                        (versionedSettingsStatus.isDslOutdated() ? ". DSL scripts should be updated." : ""));
    List<String> beanMissingContextParams = versionedSettingsStatus.getRequiredContextParameters();
    myMissingContextParameters = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("missingContextParameters"),
                                                                         beanMissingContextParams.isEmpty() ? null : beanMissingContextParams);
    List<VersionedSettingsBean.VersionedSettingsErrorInfo> configErrors = versionedSettingsStatus.getConfigErrors();
    myErrors = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("versionedSettingsError"),
      configErrors.isEmpty() ? null : configErrors.stream()
                                                  .map(error -> new VersionedSettingsError(error, fields.getNestedField("versionedSettingsError")))
                                                  .collect(Collectors.toList()));
    myDslOutdated = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("dslOutdated"), versionedSettingsStatus.isDslOutdated() ? true : null);
  }

  @XmlAttribute(name = "type")
  public StatusType getType() {
    return myType;
  }

  @XmlAttribute(name = "timestamp")
  public String getTimestamp() {
    return myTimestamp;
  }

  @XmlAttribute(name = "message")
  public String getMessage() {
    return myMessage;
  }

  @XmlAttribute(name = "dslOutdated")
  public Boolean getDslOutdated() {
    return myDslOutdated;
  }

  @XmlAttribute(name = "missingContextParameters")
  public Collection<String> getMissingContextParameters() {
    return myMissingContextParameters;
  }

  @XmlElement(name = VersionedSettingsError.TYPE)
  public List<VersionedSettingsError> getErrors() {
    return myErrors;
  }


  public enum StatusType implements DefaultValueAware {
    info,
    warn;

    @Override
    public boolean isDefault() {
      return equals(StatusType.info);
    }
  }

}
