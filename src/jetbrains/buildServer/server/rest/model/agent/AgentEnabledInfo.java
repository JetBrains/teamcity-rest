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

import java.util.Date;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.TimeCondition;
import jetbrains.buildServer.server.rest.data.TimeWithPrecision;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.SBuildAgent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 03/12/2015
 */
@XmlType(name = "enabledInfo")
@XmlRootElement(name = "enabledInfo")
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_DESCRIPTION, value = "Represents the current enablement status of the agent." +
"\n\nRelated Help article: [Build Agent](https://www.jetbrains.com/help/teamcity/build-agent.html)"))
public class AgentEnabledInfo extends BooleanStatus {
  @XmlAttribute(name = "statusSwitchTime")
  public String statusSwitchTime;

  public AgentEnabledInfo() {
  }

  public AgentEnabledInfo(@NotNull final SBuildAgent agent, final Fields fields, final BeanContext beanContext) {
    super(agent.isEnabled(), () -> agent.getStatusComment(), fields, beanContext);
    Boolean restoreEnabled = agent.getAgentStatusToRestore();
    if (restoreEnabled != null && (restoreEnabled ^ agent.isEnabled())) {
      statusSwitchTime = ValueWithDefault.decideDefault(fields.isIncluded("statusSwitchTime"), Util.formatTime(agent.getAgentStatusRestoringTimestamp()));
    }
  }

  @Nullable
  public Date getStatusSwitchTimeFromPosted(@NotNull final ServiceLocator serviceLocator) {
    if (statusSwitchTime == null) return null;
    return TimeWithPrecision.parse(statusSwitchTime, TimeCondition.getTimeService(serviceLocator)).getTime();
  }
}
