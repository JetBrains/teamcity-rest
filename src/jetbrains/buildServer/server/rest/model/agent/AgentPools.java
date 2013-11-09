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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.AgentPoolsFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 07.11.13
 */
@XmlRootElement(name = "agentPools")
@XmlType(name = "agentPools")
@SuppressWarnings("PublicField")
public class AgentPools {
  @XmlAttribute
  public long count;

  @XmlElement(name = "agentPool")
  public List<AgentPoolRef> items;

  public AgentPools() {
  }

  public AgentPools(Collection<AgentPool> items, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    this.items = new ArrayList<AgentPoolRef>(items.size());
    for (AgentPool item : items) {
      this.items.add(new AgentPoolRef(item, apiUrlBuilder));
    }
    count = this.items.size();
  }

  @NotNull
  public List<AgentPool> getPoolsFromPosted(@NotNull final AgentPoolsFinder agentPoolsFinder) {
      if (items == null) {
        throw new BadRequestException("List of agent pools should be supplied");
      }
      final ArrayList<AgentPool> result = new ArrayList<AgentPool>(items.size());
      for (AgentPoolRef agentPoolRef : items) {
        result.add(agentPoolRef.getAgentPoolFromPosted(agentPoolsFinder));
      }
      return result;
    }
}
