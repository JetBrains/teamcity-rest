/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 01.08.2009
 */
@XmlRootElement(name = "agent")
public class Agent {
  private SBuildAgent myAgent;
  private ApiUrlBuilder myApiUrlBuilder;

  public Agent() {
  }

  public Agent(final SBuildAgent agent, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    myAgent = agent;
    myApiUrlBuilder = apiUrlBuilder;
  }

  @XmlAttribute
  public String getHref() {
    return myApiUrlBuilder.getHref(myAgent);
  }

  @XmlAttribute
  public Integer getId() {
    return myAgent.getId();
  }

  @XmlAttribute
  public String getName() {
    return myAgent.getName();
  }

  @XmlAttribute
  public boolean isConnected() {
    return myAgent.isRegistered();
  }

  @XmlAttribute
  public boolean isEnabled() {
    return myAgent.isEnabled();
  }

  @XmlAttribute
  public boolean isAuthorized() {
    return myAgent.isAuthorized();
  }

  @XmlAttribute
  public boolean isUptodate() {
    return !myAgent.isOutdated() && !myAgent.isPluginsOutdated();
  }

  @XmlAttribute
  public String getIp() {
    return myAgent.getHostAddress();
  }

  @XmlElement
  public Properties getProperties() {
    //TODO: review, if it should return all parameters on agent, use #getDefinedParameters()
    return new Properties(myAgent.getAvailableParameters());
  }

  public static String getFieldValue(@NotNull final SBuildAgent agent, @Nullable final String name) {
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Field name cannot be empty");
    }
    if ("id".equals(name)) {
      return String.valueOf(agent.getId());
    } else if ("name".equals(name)) {
      return agent.getName();
    } else if ("connected".equals(name)) {
      return String.valueOf(agent.isRegistered());
    } else if ("enabled".equals(name)) {
      return String.valueOf(agent.isEnabled());
    } else if ("authorized".equals(name)) {
      return String.valueOf(agent.isAuthorized());
    } else if ("ip".equals(name)) {
      return agent.getHostAddress();
    }
    throw new BadRequestException("Unknown field '" + name + "'. Supported fields are: id, name, connected, enabled, authorized, ip");
  }

  public static void setFieldValue(@NotNull final SBuildAgent agent, @Nullable final String name, @NotNull final String value, @NotNull final DataProvider dataProvider) {
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Field name cannot be empty");
    }
    if ("enabled".equals(name)) {
      agent.setEnabled(Boolean.valueOf(value), dataProvider.getCurrentUser(), TeamCityProperties.getProperty("rest.defaultActionComment"));
      //todo (TeamCity) why not use current user by default?
      return;
    } else if ("authorized".equals(name)) {
      agent.setAuthorized(Boolean.valueOf(value), dataProvider.getCurrentUser(), TeamCityProperties.getProperty("rest.defaultActionComment"));
      //todo (TeamCity) why not use current user by default?
      return;
    }
    throw new BadRequestException("Changing field '" + name + "' is not supported. Supported fields are: enabled, authorized");
  }
}
