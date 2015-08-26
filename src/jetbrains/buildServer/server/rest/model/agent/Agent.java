/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.data.AgentFinder;
import jetbrains.buildServer.server.rest.data.AgentPoolsFinder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.impl.agent.DeadAgent;
import jetbrains.buildServer.serverSide.impl.agent.PollingRemoteAgentConnection;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 01.08.2009
 */
@XmlRootElement(name = "agent")
@SuppressWarnings("PublicField")
public class Agent {

  public static final int UNKNOWN_AGENT_ID = -1;
  @XmlAttribute public Integer id;
  @XmlAttribute public String name;
  @XmlAttribute public Integer typeId;
  @XmlAttribute public Boolean connected;
  @XmlAttribute public Boolean enabled;
  @XmlAttribute public Boolean authorized;
  @XmlAttribute public Boolean uptodate;
  @XmlAttribute public String ip;
  @XmlAttribute public String protocol;
  @XmlAttribute public String href;
  @XmlElement public Properties properties;
  @XmlElement public AgentPool pool;
  @XmlElement public BuildTypes compatibleBuildTypes;
  @XmlElement public Compatibilities incompatibleBuildTypes;

  /**
   * This is used only when posting a link to an agent.
   */
  @XmlAttribute public String locator;

  public Agent() {
  }

  public Agent(@NotNull final SBuildAgent agent, @NotNull final AgentPoolsFinder agentPoolsFinder, final @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    final int agentId = agent.getId();
    final boolean unknownAgent = agentId == UNKNOWN_AGENT_ID;
    id = unknownAgent ? null : ValueWithDefault.decideIncludeByDefault(fields.isIncluded("id"), agentId);
    name = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("name"), agent.getName());
    typeId = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("typeId"), agent.getAgentTypeId());
    href = unknownAgent ? null : ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().getHref(agent));
    connected = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("connected", false), agent.isRegistered());
    enabled = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("enabled", false), agent.isEnabled());
    authorized = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("authorized", false), agent.isAuthorized());
    uptodate = unknownAgent ? null : ValueWithDefault.decideIncludeByDefault(fields.isIncluded("uptodate", false), !agent.isOutdated() && !agent.isPluginsOutdated());
    ip = ValueWithDefault.decideDefaultIgnoringAccessDenied(fields.isIncluded("ip", false), new ValueWithDefault.Value<String>() {
      @Nullable
      public String get() {
        final String hostAddress = agent.getHostAddress();
        return DeadAgent.NA.equals(hostAddress) ? null : hostAddress;
      }
    });
    protocol = ValueWithDefault.decideDefaultIgnoringAccessDenied(fields.isIncluded("protocol", false, false), new ValueWithDefault.Value<String>() {  //hide by default for now
      @Nullable
      public String get() {
        return getAgentProtocol(agent);
      }
    });
    //TODO: review, if it should return all parameters on agent, use #getDefinedParameters()
    properties = ValueWithDefault.decideDefaultIgnoringAccessDenied(fields.isIncluded("properties", false), new ValueWithDefault.Value<Properties>() {
      @Nullable
      public Properties get() {
        return new Properties(agent.getAvailableParameters(), null, fields.getNestedField("properties", Fields.NONE, Fields.LONG));
      }
    });
    pool = ValueWithDefault.decideDefault(fields.isIncluded("pool", false), new ValueWithDefault.Value<AgentPool>() {
      @Nullable
      public AgentPool get() {
        final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = agentPoolsFinder.getAgentPool(agent);
        return agentPool == null ? null : new AgentPool(agentPool, fields.getNestedField("pool"), beanContext);
      }
    });

    final Compatibilities.CompatibilityLists[] compatibilityResults = new Compatibilities.CompatibilityLists[1];
    compatibleBuildTypes =
      ValueWithDefault.decideDefault(fields.isIncluded("compatibleBuildTypes", false, false), new ValueWithDefault.Value<BuildTypes>() {
        @Nullable
        public BuildTypes get() {
          if (compatibilityResults[0] == null) compatibilityResults[0] = Compatibilities.getCompatiblityLists(agent, null, beanContext);
          return new BuildTypes(compatibilityResults[0].getCompatibleBuildTypes(), null, fields.getNestedField("compatibleBuildTypes"), beanContext);
        }
      });
    incompatibleBuildTypes = ValueWithDefault.decideDefault(fields.isIncluded("incompatibleBuildTypes", false, false), new ValueWithDefault.Value<Compatibilities>() {
      @Nullable
      public Compatibilities get() {
        if (compatibilityResults[0] == null) compatibilityResults[0] = Compatibilities.getCompatiblityLists(agent, null, beanContext);
        return new Compatibilities(compatibilityResults[0].incompatibleBuildTypes, agent, null, fields.getNestedField("incompatibleBuildTypes"), beanContext);
      }
    });
  }

  @NotNull
  private static String getAgentProtocol(final @NotNull SBuildAgent agent) {
    final String protocolType = agent.getCommunicationProtocolType();
    if (PollingRemoteAgentConnection.TYPE.equals(protocolType)) return "unidirectional";
    // would be better to check, but that is in another module: if (XmlRpcRemoteAgentConnection.DESCRIPTION.equals(communicationProtocolDescription)) return "bidirectional";
    return protocolType;
  }

  public static String getFieldValue(@NotNull final SBuildAgent agent, @Nullable final String name) {
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Field name cannot be empty");
    }
    if ("id".equals(name)) {
      return String.valueOf(agent.getId());
    } else if ("typeId".equals(name)) {
      return String.valueOf(agent.getAgentTypeId());
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
    } else if ("protocol".equals(name)) {
      return getAgentProtocol(agent);
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

  @NotNull
  public SBuildAgent getAgentFromPosted(@NotNull final AgentFinder agentFinder) {
    String locatorText = "";
    if (id != null) locatorText += (!locatorText.isEmpty() ? "," : "") + AgentFinder.DIMENSION_ID + ":" + id;
    if (locatorText.isEmpty()) {
      locatorText = locator;
    } else {
      if (locator != null) {
        throw new BadRequestException("Both 'locator' and 'id' attributes are specified. Only one should be present.");
      }
    }
    if (StringUtil.isEmpty(locatorText)){
      throw new BadRequestException("No agent specified. Either 'id' or 'locator' attribute should be present.");
    }
    return agentFinder.getItem(locatorText);
  }
}
