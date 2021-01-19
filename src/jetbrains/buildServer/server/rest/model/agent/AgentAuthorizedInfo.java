/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.agent;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.SBuildAgent;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 03/12/2015
 */
@XmlType(name = "authorizedInfo")
@XmlRootElement(name = "authorizedInfo")
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_DESCRIPTION, value = "Represents agent authorization data." + 
"\n\nRelated Help article: [Build Agent](https://www.jetbrains.com/help/teamcity/build-agent.html)"))
public class AgentAuthorizedInfo extends BooleanStatus {
  public AgentAuthorizedInfo() {
  }

  public AgentAuthorizedInfo(@NotNull final SBuildAgent agent, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    super(agent.isAuthorized(), () -> agent.getAuthorizeComment(), fields, beanContext);
  }
}
