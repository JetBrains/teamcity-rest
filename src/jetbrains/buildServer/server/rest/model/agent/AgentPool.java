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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.server.rest.data.AgentFinder;
import jetbrains.buildServer.server.rest.data.AgentPoolFinder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.model.project.Projects;
import jetbrains.buildServer.server.rest.request.AgentRequest;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.agentPools.*;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 07.11.13
 */
@XmlRootElement(name = "agentPool")
@XmlType(name = "agentPool")
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_DESCRIPTION, value = "Represents an agent pool instance." + 
"\nRelated Help article: [Agent Pool](https://www.jetbrains.com/help/teamcity/agent-pool.html)"))
@SuppressWarnings("PublicField")
public class AgentPool {
  @XmlAttribute public Integer id;
  @XmlAttribute public String name;
  @XmlAttribute public String href;
  @XmlAttribute public Integer maxAgents;
  @XmlElement public Project ownerProject;
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

    final AgentPoolFinder agentPoolFinder = beanContext.getSingletonService(AgentPoolFinder.class);

    ownerProject = !agentPool.isProjectPool() ? null : ValueWithDefault.decideDefault(fields.isIncluded("ownerProject", false, false), //do not add by default as this is being introduced in bugfix release. Can include by default since any major release
                  () -> {
                    AgentPoolFinder.PontentiallyInaccessibleProject project = agentPoolFinder.getPoolOwnerProject(agentPool);
                    if (project == null) { //not existing project
                      return null;
                    }
                    if (project.getProject() == null) { //inaccessible project
                      return new Project(null, project.getInternalProjectId(), fields.getNestedField("ownerProject"), beanContext);
                    }
                    return new Project(project.getProject(), fields.getNestedField("ownerProject"), beanContext);
                  });

    projects = ValueWithDefault.decideDefault(fields.isIncluded("projects", false), new ValueWithDefault.Value<Projects>() {
      @Nullable
      public Projects get() {
        return new Projects(agentPoolFinder.getPoolProjects(agentPool), null, fields.getNestedField("projects", Fields.NONE, Fields.LONG), beanContext);
      }
    });
    //todo: support agent types
    agents = ValueWithDefault.decideDefault(fields.isIncluded("agents", false), new ValueWithDefault.Value<Agents>() {
      @Nullable
      public Agents get() {
        Fields nestedFields = fields.getNestedField("agents", Fields.NONE, Fields.LONG);
        String locator = Locator.merge(nestedFields.getLocator(), AgentFinder.getLocator(agentPool));
        return new Agents(locator, new PagerData(AgentRequest.getItemsHref(locator)), nestedFields, beanContext);
      }
    });

    final AuthorityHolder authorityHolder = beanContext.getSingletonService(PermissionChecker.class).getCurrent();

    if(AuthUtil.hasPermissionToViewAgentDetailsInPool(authorityHolder, agentPool) || AuthUtil.hasPermissionToManageAgentPoolsWithProjects(authorityHolder, agentPool.getProjectIds())){
      href = ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().getHref(agentPool));
      maxAgents = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("maxAgents", false), getMaxAgents(agentPool));
    }
  }

  @NotNull
  public jetbrains.buildServer.serverSide.agentPools.AgentPool getAgentPoolFromPosted(@NotNull final AgentPoolFinder agentPoolFinder) {
    Locator resultLocator = Locator.createEmptyLocator();
    boolean otherDimensionsSet = false;
    if (id != null) {
      otherDimensionsSet = true;
      resultLocator.setDimension(AgentPoolFinder.ID.name, String.valueOf(id));
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
    return agentPoolFinder.getItem(resultLocator.getStringRepresentation());
  }

  public static String getFieldValue(@NotNull final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool, @NotNull final String fieldName) {
    if ("name".equals(fieldName)) {
      return agentPool.getName();
    } else if ("maxAgents".equals(fieldName)) {
      return getStringValue(getMaxAgents(agentPool));
    }
    throw new NotFoundException("Field '" + fieldName + "' is not supported. Supported are: name, maxAgents.");
  }

  @Nullable
  private static String getStringValue(@Nullable final Object o) {
    return o == null ? null : String.valueOf(o);
  }

  @Nullable
  private static Integer getMaxAgents(final @NotNull jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool) {
    int maxAgents = agentPool.getMaxAgents();
    return maxAgents == AgentPoolLimits.DEFAULT.getMaxAgents() ? null : agentPool.getMaxAgents();
  }

  public static void setFieldValue(@NotNull final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool,
                                   @NotNull final String fieldName, @Nullable final String newValue, @NotNull final BeanContext beanContext) {
    String newName = agentPool.getName();
    int minAgents = agentPool.getMinAgents();
    int maxAgents = agentPool.getMaxAgents();
    boolean modificationsFound = false;

    if ("name".equals(fieldName)) {
      if (StringUtil.isEmpty(newValue)) {
        throw new BadRequestException("Agent pool name cannot be empty");
      } else {
        newName = newValue;
      }
      modificationsFound = true;
    } else if ("maxAgents".equals(fieldName)) {
      if (StringUtil.isEmpty(newValue)) {
        maxAgents = AgentPoolLimits.DEFAULT.getMaxAgents();
      } else {
        maxAgents = Integer.valueOf(newValue);
      }
      modificationsFound = true;
    }
    if (!modificationsFound) {
      throw new BadRequestException("Setting field '" + fieldName + "' is not supported. Supported are: name, maxAgents.");
    }
    try {
      beanContext.getSingletonService(AgentPoolManager.class).updateAgentPool(agentPool.getAgentPoolId(), newName, new AgentPoolLimitsImpl(minAgents, maxAgents));
    } catch (NoSuchAgentPoolException e) {
      throw new BadRequestException(e.getMessage());
    } catch (AgentPoolCannotBeRenamedException e) {
      throw new BadRequestException(e.getMessage());
    }
  }
}
