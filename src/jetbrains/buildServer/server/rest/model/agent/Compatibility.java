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
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.AgentCompatibility;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "compatibility")
@SuppressWarnings({"PublicField", "PackageVisibleField"})
public class Compatibility {
  @XmlElement(name = "agent-href") String agent;
  @XmlElement(name = "build-type") BuildType buildType;
  @XmlAttribute(name = "compatible") boolean isCompatible;
  @XmlAttribute(name = "reason") String reason;

  public Compatibility() {
  }

  public Compatibility(@Nullable final SBuildAgent agent, @NotNull final AgentCompatibility compatibility, @NotNull final Fields fields, @NotNull final BeanContext context) {
    this.agent = fields.isIncluded("agent", false, true) && agent != null ? context.getApiUrlBuilder().getHref(agent) : null;
    final Boolean build = fields.isIncluded("build-type");
    this.buildType =
      new BuildType(new BuildTypeOrTemplate(compatibility.getBuildType()), (build != null && build) ? fields.getNestedField("build-type") : new Fields("href"), context);
    this.isCompatible = compatibility.isCompatible();
    this.reason = (!isCompatible && fields.isIncluded("reason", false, true)) ? getIncompatibilityReason(compatibility) : null;
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
        sb.append('\t').append(r.getPropertyName()).append(' ').append(type.getDisplayName());
        if (!StringUtil.isEmpty(r.getPropertyValue()) || !type.isParameterCanBeEmpty() || !type.isParameterRequired()) {
          sb.append(' ').append(r.getPropertyValue());
        }
        sb.append('\n');
      }
    }
    if (!compatibility.getMissedVcsPluginsOnAgent().isEmpty()) {
      final Map<String, String> missed = compatibility.getMissedVcsPluginsOnAgent();
      sb.append("Missing VCS plugins on agent:\n");
      for (String v : missed.values()) {
        sb.append('\t').append(v).append('\n');
      }
    }
    if (!compatibility.getInvalidRunParameters().isEmpty()) {
      final List<InvalidProperty> irp = compatibility.getInvalidRunParameters();
      sb.append("Missing or invalid build configuration parameters:\n");
      for (InvalidProperty ip : irp) {
        sb.append('\t').append(ip.getPropertyName()).append(": ").append(ip.getInvalidReason()).append('\n');
      }
    }

    if (!compatibility.getUndefinedParameters().isEmpty()) {
      final Map<String, String> undefined = compatibility.getUndefinedParameters();
      sb.append("Implicit requirements:\n");
      for (Map.Entry<String, String> entry : undefined.entrySet()) {
        sb.append('\t').append(entry.getKey()).append(" defined in ").append(entry.getValue()).append('\n');
      }
    }
    return sb.toString();
  }
}
