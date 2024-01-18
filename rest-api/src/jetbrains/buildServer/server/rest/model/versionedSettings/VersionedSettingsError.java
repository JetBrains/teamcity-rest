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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.controllers.project.VersionedSettingsBean;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = VersionedSettingsError.TYPE)
@ModelDescription(
  value = "Represents a Versioned Settings Error.",
  externalArticleLink = "https://www.jetbrains.com/help/teamcity/storing-project-settings-in-version-control.html",
  externalArticleName = "Storing Project Settings in Version Control"
)
public class VersionedSettingsError {

  static final String TYPE = "versionedSettingsError";

  private String myMessage;

  private String myType;

  private List<String> myStackTraceLines;

  private String myFile;

  @SuppressWarnings("unused")
  public VersionedSettingsError() {
  }

  public VersionedSettingsError(@NotNull VersionedSettingsBean.VersionedSettingsErrorInfo versionedSettingsErrorInfo,
                                @NotNull Fields fields) {
    myMessage = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("message"), versionedSettingsErrorInfo.getMessage());
    myType = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("type"), versionedSettingsErrorInfo.getErrorType());
    myStackTraceLines = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("stackTraceLines"),
                                                              versionedSettingsErrorInfo.isHasStackTrace() ? versionedSettingsErrorInfo.getStackTrace() : null);
    myFile = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("file"),
                                                   versionedSettingsErrorInfo.isFilePathPresent() ? versionedSettingsErrorInfo.getFilePath() : null);
  }

  @XmlAttribute(name = "message")
  public String getMessage() {
    return myMessage;
  }

  @XmlAttribute(name = "type")
  public String getType() {
    return myType;
  }

  @XmlAttribute(name = "stackTraceLines")
  public List<String> getStackTraceLines() {
    return myStackTraceLines;
  }

  @XmlAttribute(name = "file")
  public String getFile() {
    return myFile;
  }

}
