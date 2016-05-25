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

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.controllers.agent.AgentDetailsFormFactory;
import jetbrains.buildServer.server.rest.data.AgentFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.AgentCompatibility;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "compatibilities")
@XmlType(name = "compatibilities")
@SuppressWarnings("PublicField")
public class Compatibilities {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "compatibility")
  public List<Compatibility> compatibilities;

  public Compatibilities() {
  }

  public Compatibilities(@Nullable final List<Compatibility.AgentCompatibilityData> compatibilitiesP,
                         @Nullable final SBuildAgent contextAgent, @Nullable final SBuildType contextBuildType,
                         @NotNull final Fields fields, final @NotNull BeanContext beanContext) {
    compatibilities =
      compatibilitiesP == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("compatibility", true, true), new ValueWithDefault.Value<List<Compatibility>>() {
        @Nullable
        public List<Compatibility> get() {
          return CollectionsUtil.convertCollection(compatibilitiesP, new Converter<Compatibility, Compatibility.AgentCompatibilityData>() {
            public Compatibility createFrom(@NotNull final Compatibility.AgentCompatibilityData source) {
              return new Compatibility(source, contextAgent, contextBuildType, fields.getNestedField("compatibility"), beanContext);
            }
          });
        }
      });

    count = compatibilitiesP == null ? null : ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), compatibilitiesP.size());
  }

  @NotNull
  public static CompatibilityLists getCompatiblityLists(final @NotNull SBuildAgent agent, final @Nullable List<SBuildType> buildTypes, final @NotNull BeanContext beanContext) {
    final CompatibilityLists result = new CompatibilityLists();

    final AgentDetailsFormFactory factory = beanContext.getServiceLocator().getSingletonService(AgentDetailsFormFactory.class);
    for (AgentCompatibility compatibility : factory.createAgentDetailsForm(agent).getActiveCompatibilities().getCompatibilities()) {
      processCompatibility(result, compatibility, agent, buildTypes);
    }
    return result;
  }

  private static void processCompatibility(final @NotNull CompatibilityLists result,
                                           final @NotNull AgentCompatibility compatibility,
                                           final @NotNull SBuildAgent agent,
                                           final @Nullable List<SBuildType> buildTypes) {
    if (buildTypes != null && !buildTypes.contains(compatibility.getBuildType())) return;
    if (!compatibility.isActive()) return;
    if (compatibility.isCompatible()) {
      result.compatibleBuildTypes.add(new Compatibility.AgentCompatibilityData(compatibility, agent));
    } else {
      result.incompatibleBuildTypes.add(new Compatibility.AgentCompatibilityData(compatibility, agent));
    }
  }

  @NotNull
  public static CompatibilityLists getCompatiblityLists(@Nullable List<SBuildAgent> agents, final @Nullable List<SBuildType> buildTypes,
                                                        final @NotNull BeanContext beanContext) {
    final CompatibilityLists result = new CompatibilityLists();
    if (agents == null) agents = beanContext.getSingletonService(AgentFinder.class).getItems(null).myEntries;
    for (SBuildAgent agent : agents) {
      final CompatibilityLists agentResult = getCompatiblityLists(agent, buildTypes, beanContext);
      result.compatibleBuildTypes.addAll(agentResult.compatibleBuildTypes);
      result.incompatibleBuildTypes.addAll(agentResult.incompatibleBuildTypes);
    }
    return result;
  }

  public static class CompatibilityLists {
    public List<Compatibility.AgentCompatibilityData> compatibleBuildTypes = new ArrayList<Compatibility.AgentCompatibilityData>();
    public List<Compatibility.AgentCompatibilityData> incompatibleBuildTypes = new ArrayList<Compatibility.AgentCompatibilityData>();

    public List<BuildTypeOrTemplate> getCompatibleBuildTypes() {
      return CollectionsUtil.convertCollection(compatibleBuildTypes, new Converter<BuildTypeOrTemplate, Compatibility.AgentCompatibilityData>() {
        public BuildTypeOrTemplate createFrom(@NotNull final Compatibility.AgentCompatibilityData source) {
          return new BuildTypeOrTemplate(source.getBuildType());
        }
      });
    }

    public List<Compatibility.AgentCompatibilityData> getAll() {
      final ArrayList<Compatibility.AgentCompatibilityData> result = new ArrayList<Compatibility.AgentCompatibilityData>(compatibleBuildTypes);
      result.addAll(incompatibleBuildTypes);
      return result;
    }
  }
}
