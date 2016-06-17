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

import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.server.rest.data.AgentPoolFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.AgentCompatibility;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("PublicField")
@XmlRootElement(name = "compatibility")
public class Compatibility {
  @XmlAttribute public Boolean compatible;
  @XmlElement public Agent agent;
  @XmlElement public BuildType buildType;
  @XmlElement public Requirements unmetRequirements;

  public Compatibility() {
  }

  public Compatibility(@NotNull final AgentCompatibilityData compatibility,
                       @Nullable final SBuildAgent contextAgent, @Nullable final SBuildType contextBuildType,
                       @NotNull final Fields fields, @NotNull final BeanContext context) {
    compatible = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("compatible", true, true), compatibility.getCompatibility().isCompatible());

    final boolean sameAgent = contextAgent != null && compatibility.getAgent().getId() == contextAgent.getId();
    agent = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("agent", !sameAgent, !sameAgent), new ValueWithDefault.Value<Agent>() {
      @Nullable
      public Agent get() {
        return new Agent(compatibility.getAgent(), context.getSingletonService(AgentPoolFinder.class), fields.getNestedField("agent", Fields.SHORT, Fields.SHORT), context);
      }
    });

    final boolean sameBuildType = contextBuildType != null && compatibility.getBuildType().getInternalId().equals(contextBuildType.getInternalId());
    buildType = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("buildType", !sameBuildType, !sameBuildType), new ValueWithDefault.Value<BuildType>() {
      @Nullable
      public BuildType get() {
        return new BuildType(new BuildTypeOrTemplate(compatibility.getBuildType()), fields.getNestedField("buildType", Fields.SHORT, Fields.SHORT), context);
      }
    });
    unmetRequirements = compatibility.getCompatibility().isCompatible() ? null : ValueWithDefault.decideIncludeByDefault(fields.isIncluded("unmetRequirements", true, true),
                                                                                                                   new ValueWithDefault.Value<Requirements>() {
      @Nullable
      public Requirements get() {
        return new Requirements(getDescription(compatibility.getCompatibility()), fields.getNestedField("unmetRequirements", Fields.LONG, Fields.LONG), context);
      }
    });
  }

  static String getDescription(@NotNull final AgentCompatibility compatibility) {
    if (compatibility.isCompatible()) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    if (compatibility.getIncompatibleRunner() != null) {
      sb.append("Incompatible runner: '").append(compatibility.getIncompatibleRunner().getDisplayName()).append("'\n");
    }
    if (!compatibility.getNonMatchedRequirements().isEmpty()) {
      sb.append("Unmet requirements:\n");
      for (Requirement r : compatibility.getNonMatchedRequirements()) {
        final RequirementType type = r.getType();
        sb.append("\tParameter '").append(r.getPropertyName()).append("' ").append(type.getDisplayName());
        if (!StringUtil.isEmpty(r.getPropertyValue())) {
          sb.append(" '").append(r.getPropertyValue()).append("'");
        }
        sb.append("; ");
      }
      sb.delete(sb.length() - "; ".length(), sb.length());
    }
    if (!compatibility.getMissedVcsPluginsOnAgent().isEmpty()) {
      final Map<String, String> missed = compatibility.getMissedVcsPluginsOnAgent();
      sb.append("Missing VCS plugins on agent:\n");
      for (String v : missed.values()) {
        sb.append("\t'").append(v).append("'\n");
      }
    }
    if (!compatibility.getInvalidRunParameters().isEmpty()) {
      final List<InvalidProperty> irp = compatibility.getInvalidRunParameters();
      sb.append("Missing or invalid build configuration parameters:\n");
      for (InvalidProperty ip : irp) {
        sb.append("\t'").append(ip.getPropertyName()).append("': ").append(ip.getInvalidReason()).append('\n');
      }
    }

    if (!compatibility.getUndefinedParameters().isEmpty()) {
      final Map<String, String> undefined = compatibility.getUndefinedParameters();
      sb.append("Implicit requirements:\n");
      for (Map.Entry<String, String> entry : undefined.entrySet()) {
        sb.append("\tParameter '").append(entry.getKey()).append("' defined in ").append(entry.getValue()).append('\n');
      }
    }
    return sb.toString();
  }

  public static class AgentCompatibilityData {
    @NotNull
    public final SBuildAgent myAgent;

    @NotNull
    private final AgentCompatibility myCompatibility;

    public AgentCompatibilityData(final @NotNull AgentCompatibility compatibility, final @NotNull SBuildAgent agent) {
      myAgent = agent;
      myCompatibility = compatibility;
    }

    @NotNull
    public SBuildAgent getAgent() {
      return myAgent;
    }

    @NotNull
    public SBuildType getBuildType() {
      return myCompatibility.getBuildType();
    }

    @NotNull
    public AgentCompatibility getCompatibility() {
      return myCompatibility;
    }
  }
}
