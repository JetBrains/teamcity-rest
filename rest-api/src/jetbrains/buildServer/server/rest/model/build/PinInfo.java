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

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.agent.BooleanStatus;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.SBuild;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 * Date: 21/09/2018
 */
@XmlType(name = "pinInfo")
@XmlRootElement(name = "pinInfo")
@ModelDescription(
    value = "Represents the pinned status of this build.",
    externalArticleLink = "https://www.jetbrains.com/help/teamcity/pinned-build.html",
    externalArticleName = "Pinning Build"
)
public class PinInfo extends BooleanStatus {
  public PinInfo() {
  }

  public PinInfo(@NotNull SBuild build, final Fields fields, final BeanContext beanContext) {
    super(build != null && build.isPinned(), () -> Build.getPinComment(build), fields, beanContext);
  }
}