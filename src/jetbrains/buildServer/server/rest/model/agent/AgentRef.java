/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 01.08.2009
 */
@XmlRootElement(name = "agent-ref")
@XmlType(name = "agent-ref")
@SuppressWarnings("PublicField")
public class AgentRef {
  @XmlAttribute(required = false) public Integer id;
  @XmlAttribute(required = true) public String name;
  @XmlAttribute(required = false) public String href;

  /**
   * This is used only when posting a link to an agent.
   */
  @XmlAttribute public String locator;


  public AgentRef() {
  }

  public AgentRef(@NotNull final SBuildAgent agent, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    id = agent.getId();
    name = agent.getName();
    href = apiUrlBuilder.getHref(agent);
  }

  public AgentRef(final String agentName) {
    name = agentName;
  }

  @NotNull
  public SBuildAgent getAgentFromPosted(final DataProvider dataProvider) {
    String locatorText = "";
    if (id != null) locatorText += (!locatorText.isEmpty() ? "," : "") + "id:" + id;
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
    return dataProvider.getAgent(locatorText);
  }
}
