/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.controllers.agent.OSKind;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildAgentEx;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 * Date: 20/07/2017
 */
@XmlRootElement(name = "environment")
@XmlType(name = "environment")
@ModelDescription("Represents the details of the agent's OS.")
@SuppressWarnings("PublicField")
public class Environment {
  @XmlAttribute
  public String osType;
  @XmlAttribute
  public String osName;

  public Environment() {
  }

  public Environment(@NotNull final SBuildAgent agent, @NotNull final Fields fields) {
    osType = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("osType"), () -> resolveOsType(agent));
    osName = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("osName"), agent::getOperatingSystemName);
  }

  public Environment(@NotNull SAgentType agentType, @NotNull Fields fields) {
    osType = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("osType"), () -> resolveOsType(agentType));
    osName = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("osName"), agentType::getOperatingSystemName);
  }

  @Nullable
  private static String resolveOsType(@NotNull SBuildAgent agent) {
    String osName = agent.getOperatingSystemName();
    if ("N/A".equalsIgnoreCase(osName) || "<unknown>".equalsIgnoreCase(osName) || "".equals(osName)) {
      osName = ((BuildAgentEx)agent).getAgentType().getOperatingSystemName();
    }

    return getOsName(osName);
  }

  @Nullable
  private static String resolveOsType(@NotNull SAgentType agent) {
    return getOsName(agent.getOperatingSystemName());
  }

  @Nullable
  private static String getOsName(@NotNull String osName) {
    OSKind os = OSKind.guessByName(osName);
    if (os == null) return null;
    switch (os) {
      case WINDOWS:
        return "Windows";
      case MAC:
        return "macOS";
      case LINUX:
        return "Linux";
      case SOLARIS:
        return "Solaris";
      case FREEBSD:
        return "FreeBSD";
      case OTHERUNIX:
        return "Unix";
      default:
        return null;
    }
  }
}
