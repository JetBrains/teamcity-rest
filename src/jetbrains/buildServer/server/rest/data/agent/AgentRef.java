/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.agent;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.serverSide.SBuildAgent;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 01.08.2009
 */
@XmlRootElement(name = "agent")
public class AgentRef {
  private SBuildAgent myAgent;
  private ApiUrlBuilder myApiUrlBuilder;
  private String myAgentName;

  public AgentRef() {
  }

  public AgentRef(final SBuildAgent agent, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    myAgent = agent;
    myApiUrlBuilder = apiUrlBuilder;
  }

  public AgentRef(final String agentName) {
    myAgentName = agentName;
  }

  @XmlAttribute(required = false)
  public Integer getId() {
    return myAgent == null ? null : myAgent.getId();
  }

  @XmlAttribute(required = true)
  public String getName() {
    if (myAgent != null) {
      return myAgent.getName();
    }
    if (myAgentName == null) {
      throw new IllegalStateException("Either agent or agentName must be specified.");
    }
    return myAgentName;
  }

  @XmlAttribute(required = false)
  public String getHref() {
    return myAgent == null || myApiUrlBuilder == null ? null : myApiUrlBuilder.getHref(myAgent);
  }
}
