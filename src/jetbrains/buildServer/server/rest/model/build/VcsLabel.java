/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.build;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "vcsLabel")
@XmlType(name = "vcsLabel", propOrder = {
  "text",
  "failureReason",
  "status",
  "buildId"
})
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_DESCRIPTION, value = "Represents a VCS-side label of this build's sources." +
"\n\nRelated Help article: [Labeling Sources](https://www.jetbrains.com/help/teamcity/vcs-labeling.html)"))
public class VcsLabel {
  @NotNull
  private final jetbrains.buildServer.serverSide.vcs.VcsLabel myRealLabel;
  @NotNull
  private final Fields myFields;

  public VcsLabel() {
    myRealLabel = null;
    myFields = null;
  }

  @XmlAttribute
  public String getText() {
    boolean isIncluded = myFields.isIncluded("text", true, true);
    return ValueWithDefault.decideDefault(isIncluded, myRealLabel.getLabelText());
  }

  @XmlAttribute
  public String getFailureReason() {
    boolean isIncluded = myFields.isIncluded("failureReason", false, false);
    return ValueWithDefault.decideDefault(isIncluded, myRealLabel.getFailureReason());
  }

  @XmlAttribute
  public String getStatus() {
    boolean isIncluded = myFields.isIncluded("status", false, true);
    return ValueWithDefault.decideDefault(isIncluded, myRealLabel.getStatus().toString());
  }

  @XmlAttribute
  public Long getBuildId() {
    boolean isIncluded = myFields.isIncluded("buildId", false, true);
    return ValueWithDefault.decideDefault(isIncluded, myRealLabel.getBuild().getBuildId());
  }

  public VcsLabel(@NotNull jetbrains.buildServer.serverSide.vcs.VcsLabel realLabel, @NotNull Fields fields) {
    myRealLabel = realLabel;
    myFields = fields;
  }
}
