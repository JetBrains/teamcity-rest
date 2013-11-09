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
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 07.11.13
 */
@XmlRootElement(name = "agentPool-ref")
@XmlType(name = "agentPool-ref")
@SuppressWarnings("PublicField")
public class AgentPoolRef {
  @XmlAttribute(required = false) public Integer id;
  @XmlAttribute(required = true) public String name;
  @XmlAttribute(required = false) public String href;

  public AgentPoolRef() {
  }

  public AgentPoolRef(@NotNull final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    id = agentPool.getAgentPoolId();
    name = agentPool.getName();
    href = apiUrlBuilder.getHref(agentPool);
  }
}
