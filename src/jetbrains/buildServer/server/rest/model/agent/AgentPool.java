/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.AgentPoolsFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.project.Projects;
import jetbrains.buildServer.server.rest.util.BeanContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 07.11.13
 */
@XmlRootElement(name = "agentPool")
@XmlType(name = "agentPool")
@SuppressWarnings("PublicField")
public class AgentPool {
  @XmlAttribute public String href;
  @XmlAttribute public Integer id;
  @XmlAttribute public String name;
  @XmlElement public Projects projects;
  @XmlElement public Agents agents;
  /**
   * This is used only when posting a link to a project
   */
  @XmlAttribute public String locator;

  public AgentPool() {
  }

  public AgentPool(@NotNull final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool, final @NotNull Fields fields, @NotNull final BeanContext beanContext) {

    href = beanContext.getApiUrlBuilder().getHref(agentPool);
    id = agentPool.getAgentPoolId();
    name = agentPool.getName();
    AgentPoolsFinder agentPoolsFinder = beanContext.getSingletonService(AgentPoolsFinder.class);
    projects = new Projects(agentPoolsFinder.getPoolProjects(agentPool), fields.getNestedField("projects", Fields.NONE, Fields.LONG), beanContext);
    //todo: support agent types
    agents = new Agents(agentPoolsFinder.getPoolAgents(agentPool), null, fields.getNestedField("agents", Fields.NONE, Fields.LONG), beanContext);
  }

  @NotNull
  public jetbrains.buildServer.serverSide.agentPools.AgentPool getAgentPoolFromPosted(@NotNull final AgentPoolsFinder agentPoolsFinder) {
    AgentPoolRef agentPoolRef = new AgentPoolRef();
    agentPoolRef.id = id;
    agentPoolRef.name = name;
    agentPoolRef.locator = locator;
    return agentPoolRef.getAgentPoolFromPosted(agentPoolsFinder);
  }
}
