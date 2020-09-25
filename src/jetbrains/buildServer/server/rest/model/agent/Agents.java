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

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.server.rest.data.AgentFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.SBuildAgent;
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
 *         Date: 16.08.2009
 */
@XmlRootElement(name = "agents")
@XmlType(name = "agents-ref")
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_BASE_TYPE, value = ObjectType.PAGINATED))
@SuppressWarnings("PublicField")
public class Agents {
  public static final String COUNT = "count";
  @XmlAttribute
  public Integer count;

  public static final String AGENT = "agent";
  @XmlElement(name = AGENT)
  public List<Agent> agents;

  @XmlAttribute(required = false) @Nullable public String nextHref;
  @XmlAttribute(required = false) @Nullable public String prevHref;
  @XmlAttribute(required = false) @Nullable public String href;

  public Agents() {
  }

  public Agents(@Nullable final Collection<SBuildAgent> agentObjects, @Nullable final PagerData pagerData, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    init(agentObjects, pagerData, fields, beanContext);
  }

  public Agents(@Nullable final String agentsLocatorText, @Nullable final PagerData pagerData, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    AgentFinder finder = beanContext.getSingletonService(AgentFinder.class);
    List<SBuildAgent> result = null;
    if (fields.isIncluded(AGENT, false, true) || fields.isIncluded(COUNT, false, true)){ //todo: is decision on count OK?
      result = finder.getItems(agentsLocatorText).myEntries; //todo: make it effective
    }
    init(result, pagerData, fields, beanContext);
  }

  private void init(final @Nullable Collection<SBuildAgent> agentObjects,
                    final @Nullable PagerData pagerData,
                    final @NotNull Fields fields,
                    final @NotNull BeanContext beanContext) {
    if (agentObjects != null) {
      agents = ValueWithDefault.decideDefault(fields.isIncluded(AGENT, false, true), new ValueWithDefault.Value<List<Agent>>() {
        @Nullable
        public List<Agent> get() {
          final ArrayList<Agent> items = new ArrayList<Agent>(agentObjects.size());
          for (SBuildAgent item : agentObjects) {
            items.add(new Agent(item, fields.getNestedField(AGENT), beanContext));
          }
          return items;
        }
      });

      count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded(COUNT), agentObjects.size());
    }

    if (pagerData != null) {
      href = ValueWithDefault.decideDefault(fields.isIncluded("href", true), beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getHref()));
      nextHref = ValueWithDefault
        .decideDefault(fields.isIncluded("nextHref"), pagerData.getNextHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null);
      prevHref = ValueWithDefault
        .decideDefault(fields.isIncluded("prevHref"), pagerData.getPrevHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null);

    }
  }
}
