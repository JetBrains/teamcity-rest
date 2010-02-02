/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.serverSide.SBuildAgent;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 16.08.2009
 */
@XmlRootElement(name = "agents")
public class Agents {
  @XmlElement(name = "agent")
  public List<AgentRef> agents;

  public Agents() {
  }

  public Agents(Collection<SBuildAgent> agentObjects, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    agents = new ArrayList<AgentRef>(agentObjects.size());
    for (SBuildAgent agent : agentObjects) {
      agents.add(new AgentRef(agent, apiUrlBuilder));
    }
  }
}
