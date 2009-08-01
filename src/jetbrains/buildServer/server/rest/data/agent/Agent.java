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
import jetbrains.buildServer.server.rest.request.AgentRequest;
import jetbrains.buildServer.serverSide.SBuildAgent;

/**
 * @author Yegor.Yarko
 *         Date: 01.08.2009
 */
@XmlRootElement(name = "agent")
public class Agent {
  private SBuildAgent myAgent;

  public Agent() {
  }

  public Agent(final SBuildAgent agent) {
    myAgent = agent;
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
  public String getHref() {
    return AgentRequest.getAgentHref(myAgent);
  }
}
