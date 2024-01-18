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

package jetbrains.buildServer.server.rest.model.change;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.vcs.VcsRootInstanceEx;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 09/06/2016
 */
@XmlRootElement(name = "vcsStatus")
@XmlType(name = "vcsStatus")
@SuppressWarnings("PublicField")
@ModelDescription(
    value = "Represents links to the last or previous VCS root check.",
    externalArticleLink = "https://www.jetbrains.com/help/teamcity/configuring-vcs-roots.html",
    externalArticleName = "VCS Root"
)
public class VcsStatus {
  @XmlElement
  public VcsCheckStatus current;

  @XmlElement
  public VcsCheckStatus previous;

  public VcsStatus() {
  }

  public VcsStatus(@NotNull final VcsRootInstanceEx vcsRoot, final @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    current = ValueWithDefault.decideDefault(fields.isIncluded("current", true),
                                             new VcsCheckStatus(vcsRoot.getStatus(), vcsRoot.getLastRequestor(), fields.getNestedField("current", Fields.LONG, Fields.LONG)));
    previous = ValueWithDefault.decideDefault(fields.isIncluded("previous", false, false),
                                             new VcsCheckStatus(vcsRoot.getPreviousStatus(), null, fields.getNestedField("previous", Fields.LONG, Fields.LONG)));
  }
}