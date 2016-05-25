/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.project.Projects;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 07.11.13
 */
@XmlRootElement(name = "agentPool")
@XmlType(name = "agentPool")
@SuppressWarnings("PublicField")
public class AgentPool {
  @XmlAttribute public Integer id;
  @XmlAttribute public String name;
  @XmlAttribute public String href;
  @XmlElement public Projects projects;
  @XmlElement public Agents agents;
  /**
   * This is used only when posting a link to a project
   */
  @XmlAttribute public String locator;

  public AgentPool() {
  }

  public AgentPool(@NotNull final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool, final @NotNull Fields fields, @NotNull final BeanContext beanContext) {

    id = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("id"), agentPool.getAgentPoolId());
    name = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("name"), agentPool.getName());
    href = ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().getHref(agentPool));

    final AgentPoolsFinder agentPoolsFinder = beanContext.getSingletonService(AgentPoolsFinder.class);

    projects = ValueWithDefault.decideDefault(fields.isIncluded("projects", false), new ValueWithDefault.Value<Projects>() {
      @Nullable
      public Projects get() {
        return new Projects(agentPoolsFinder.getPoolProjects(agentPool), null, fields.getNestedField("projects", Fields.NONE, Fields.LONG), beanContext);
      }
    });
    //todo: support agent types
    agents = ValueWithDefault.decideDefault(fields.isIncluded("agents", false), new ValueWithDefault.Value<Agents>() {
      @Nullable
      public Agents get() {
        return new Agents(agentPoolsFinder.getPoolAgents(agentPool), null, fields.getNestedField("agents", Fields.NONE, Fields.LONG), beanContext);
      }
    });
  }

  @NotNull
  public jetbrains.buildServer.serverSide.agentPools.AgentPool getAgentPoolFromPosted(@NotNull final AgentPoolsFinder agentPoolsFinder) {
    Locator resultLocator = Locator.createEmptyLocator();
    boolean otherDimensionsSet = false;
    if (id != null) {
      otherDimensionsSet = true;
      resultLocator.setDimension(AgentPoolsFinder.DIMENSION_ID, String.valueOf(id));
    }
    /*
    //todo: implement this in finder!
    if (name != null) {
      otherDimensionsSet = true;
      resultLocator.setDimension("name", name);
    }
    */
    if (locator != null) {
      if (otherDimensionsSet) {
        throw new BadRequestException("Either 'locator' or other attributes should be specified.");
      }
      resultLocator = new Locator(locator);
    }
    return agentPoolsFinder.getAgentPool(resultLocator.getStringRepresentation());
  }
}
