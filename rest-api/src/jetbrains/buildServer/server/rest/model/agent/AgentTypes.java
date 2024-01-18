/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "agentTypes")
@XmlType(name = "agentTypes")
@ModelBaseType(ObjectType.PAGINATED)
public class AgentTypes {
  private String myHref;
  private String myPrevHref;
  private String myNextHref;
  private Integer myCount;
  private List<AgentType> myAgentTypes;

  public AgentTypes() { }

  public AgentTypes(@NotNull List<SAgentType> data,
                    @NotNull Fields fields,
                    @Nullable PagerData pagerData,
                    @NotNull BeanContext beanContext) {
    myAgentTypes = ValueWithDefault.decideDefault(
      fields.isIncluded("agentType", false, true),
      () -> resolveAgentTypes(data, fields.getNestedField("agentType"), beanContext)
    );

    myCount = ValueWithDefault.decideDefault(
      fields.isIncluded("count", true, true),
      () -> data.size()
    );

    if(pagerData != null) {
      myHref = ValueWithDefault.decideDefault(
        fields.isIncluded("href", true, false),
        () -> beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getHref())
      );

      myNextHref = ValueWithDefault.decideDefault(
        fields.isIncluded("nextHref", false, false),
        () -> beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref())
      );

      myPrevHref = ValueWithDefault.decideDefault(
        fields.isIncluded("nextHref", false, false),
        () -> beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref())
      );
    }
  }

  @NotNull
  private List<AgentType> resolveAgentTypes(@NotNull List<SAgentType> data, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    return data.stream()
               .map(at -> new AgentType(at, fields, beanContext.getServiceLocator(), beanContext.getApiUrlBuilder()))
               .collect(Collectors.toList());
  }

  @XmlAttribute(name = "count")
  public Integer getCount() {
    return myCount;
  }

  @XmlAttribute(name = "href")
  public String getHref() {
    return myHref;
  }

  @XmlAttribute(name = "prevHref")
  public String getPrevHref() {
    return myPrevHref;
  }

  @XmlAttribute(name = "nextHref")
  public String getNextHref() {
    return myNextHref;
  }

  @XmlElement(name = "agentType")
  public List<AgentType> getAgentTypes() {
    return myAgentTypes;
  }
}
