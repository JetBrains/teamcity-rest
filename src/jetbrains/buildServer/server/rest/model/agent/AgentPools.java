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

import jetbrains.buildServer.server.rest.data.AgentPoolFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Yegor.Yarko
 * Date: 07.11.13
 */
@XmlRootElement(name = "agentPools")
@XmlType(name = "agentPools")
@ModelBaseType(ObjectType.PAGINATED)
@SuppressWarnings("PublicField")
public class AgentPools {
  @XmlAttribute
  public Integer count;

  @XmlAttribute
  @Nullable
  public String href;

  @XmlAttribute(required = false)
  @Nullable
  public String nextHref;

  @XmlAttribute(required = false)
  @Nullable
  public String prevHref;

  @XmlElement(name = "agentPool")
  public List<AgentPool> items;

  public AgentPools() {
  }

  public AgentPools(Collection<jetbrains.buildServer.serverSide.agentPools.AgentPool> items, final PagerData pagerData, final Fields fields, final BeanContext beanContext) {
    if (items != null && fields.isIncluded("agentPool", false, true)) {
      ArrayList<AgentPool> list = new ArrayList<AgentPool>(items.size());
      for (jetbrains.buildServer.serverSide.agentPools.AgentPool item : items) {
        list.add(new AgentPool(item, fields.getNestedField("agentPool"), beanContext));
      }
      this.items = ValueWithDefault.decideDefault(fields.isIncluded("agentPool"), list);
    } else {
      this.items = null;
    }

    if (pagerData != null) {
      href = ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getHref()));
      nextHref = ValueWithDefault
          .decideDefault(fields.isIncluded("nextHref"), pagerData.getNextHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null);
      prevHref = ValueWithDefault
          .decideDefault(fields.isIncluded("prevHref"), pagerData.getPrevHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null);
    }
    count = items == null ? null : ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), items.size());
  }

  @NotNull
  public List<jetbrains.buildServer.serverSide.agentPools.AgentPool> getPoolsFromPosted(@NotNull final AgentPoolFinder agentPoolFinder) {
    if (items == null) {
      throw new BadRequestException("List of agent pools should be supplied");
    }
    final ArrayList<jetbrains.buildServer.serverSide.agentPools.AgentPool> result = new ArrayList<jetbrains.buildServer.serverSide.agentPools.AgentPool>(items.size());
    for (AgentPool agentPool : items) {
      result.add(agentPool.getAgentPoolFromPosted(agentPoolFinder));
    }
    return result;
  }
}
