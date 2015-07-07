/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.data.AgentPoolsFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.AgentCompatibility;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = Compatibility.COMPATIBILITY)
@SuppressWarnings({"PublicField", "PackageVisibleField"})
public class Compatibility {
  public static final String COMPATIBILITY = "compatibility";
  private boolean isCompatible;
  @XmlElement(name = "agent") Agent agent;
  @XmlElement(name = "buildType") BuildType buildType;
  @XmlAttribute(name = "reason") String reason;

  public Compatibility() {
  }

  public Compatibility(@Nullable final SBuildAgent agent, @NotNull final AgentCompatibility compatibility, @NotNull final Fields fields, @NotNull final BeanContext context) {
    this.agent = agent == null ? null : ValueWithDefault.decideIncludeByDefault(fields.isIncluded("agent", false, true), new ValueWithDefault.Value<Agent>() {
      @Nullable
      public Agent get() {
        return new Agent(agent, context.getSingletonService(AgentPoolsFinder.class), fields.getNestedField("agent"), context);
      }
    });

    this.buildType = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("buildType", true, true), new ValueWithDefault.Value<BuildType>() {
      @Nullable
      public BuildType get() {
        return new BuildType(new BuildTypeOrTemplate(compatibility.getBuildType()), fields.getNestedField("buildType"), context);
      }
    });
    this.isCompatible = compatibility.isCompatible();
    this.reason = isCompatible ? null : ValueWithDefault.decideIncludeByDefault(fields.isIncluded("reason", true, true), new ValueWithDefault.Value<String>() {
      @Nullable
      public String get() {
        return getIncompatibilityReason(compatibility);
      }
    });
  }

  static String getIncompatibilityReason(final AgentCompatibility compatibility) {
    if (compatibility.isCompatible()) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    if (compatibility.getIncompatibleRunner() != null) {
      sb.append("Incompatible runner: ").append(compatibility.getIncompatibleRunner().getDisplayName()).append('\n');
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
        sb.append("\t'").append(entry.getKey()).append("' defined in ").append(entry.getValue()).append('\n');
      }
    }
    return sb.toString();
  }
}
