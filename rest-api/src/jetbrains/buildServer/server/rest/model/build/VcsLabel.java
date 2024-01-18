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

package jetbrains.buildServer.server.rest.model.build;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import io.swagger.annotations.ApiModelProperty;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.change.VcsRootInstance;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "vcsLabel")
@XmlType(name = "vcsLabel", propOrder = {
    "text",
    "failureReason",
    "status",
    "buildId",
    "vcsRootInstance"
})
@ModelDescription(
    value = "Represents a VCS-side label of this build's sources.",
    externalArticleLink = "https://www.jetbrains.com/help/teamcity/vcs-labeling.html",
    externalArticleName = "Labeling Sources"
)
public class VcsLabel {
  @NotNull
  private jetbrains.buildServer.serverSide.vcs.VcsLabel myRealLabel;
  @NotNull
  private Fields myFields;
  @NotNull
  private BeanContext myBeanContext;

  public VcsLabel() {
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
  @ApiModelProperty(allowableValues = "UNKNOWN, SUCCESSFUL_SET, IS_BEING_SET, FAILED, DISABLED_FOR_THE_ROOT, LABELING_NOT_SUPPORTED")
  public String getStatus() {
    boolean isIncluded = myFields.isIncluded("status", false, true);
    return ValueWithDefault.decideDefault(isIncluded, myRealLabel.getStatus().toString());
  }

  @XmlAttribute
  public Long getBuildId() {
    boolean isIncluded = myFields.isIncluded("buildId", false, true);
    return ValueWithDefault.decideDefault(isIncluded, myRealLabel.getBuild().getBuildId());
  }

  @XmlElement(name = "vcs-root-instance")
  public VcsRootInstance getVcsRootInstance() {
    boolean isIncluded = myFields.isIncluded("vcs-root-instance", false, false);
    return ValueWithDefault.decideDefault(isIncluded, new VcsRootInstance(myRealLabel.getRoot(), myFields.getNestedField("vcs-root-instance"), myBeanContext));
  }

  public VcsLabel(@NotNull jetbrains.buildServer.serverSide.vcs.VcsLabel realLabel, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    myRealLabel = realLabel;
    myFields = fields;
    myBeanContext = beanContext;
  }
}