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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.SBuildAgent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.08.2009
 */
@XmlRootElement(name = "agents")
@XmlType(name = "agents-ref")
@SuppressWarnings("PublicField")
public class Agents {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "agent")
  public List<AgentRef> agents;

  @XmlAttribute(required = false) @Nullable public String nextHref;
  @XmlAttribute(required = false) @Nullable public String prevHref;
  @XmlAttribute(name = "href") public String href;

  public Agents() {
  }

  public Agents(@NotNull Collection<SBuildAgent> agentObjects, @Nullable final PagerData pagerData, @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    href = pagerData == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("href", true), beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getHref()));
    if (fields.isIncluded("agent", false, true)) {
      final ArrayList<AgentRef> agentList = new ArrayList<AgentRef>(agentObjects.size());
      for (SBuildAgent agent : agentObjects) {
        agentList.add(new AgentRef(agent, beanContext.getApiUrlBuilder()));
      }
      agents = ValueWithDefault.decideDefault(fields.isIncluded("agent"), agentList);
    } else {
      agents = null;
    }

    count = ValueWithDefault.decideDefault(fields.isIncluded("count"), agentObjects.size());

    if (pagerData != null) {
      nextHref = ValueWithDefault
        .decideDefault(fields.isIncluded("nextHref"), pagerData.getNextHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null);
      prevHref = ValueWithDefault
        .decideDefault(fields.isIncluded("prevHref"), pagerData.getPrevHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null);

    }
  }
}
