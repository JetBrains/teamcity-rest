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

package jetbrains.buildServer.server.rest.model.buildType;

import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name="agent-requirements")
@SuppressWarnings("PublicField")
public class PropEntitiesAgentRequirement {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "agent-requirement")
  public List<PropEntityAgentRequirement> propEntities;

  public PropEntitiesAgentRequirement() {
  }

  public PropEntitiesAgentRequirement(@NotNull final BuildTypeSettings buildType, @NotNull final Fields fields) {
    final List<Requirement> requirements = buildType.getRequirements();
    propEntities = ValueWithDefault.decideDefault(fields.isIncluded("agent-requirement"), new ValueWithDefault.Value<List<PropEntityAgentRequirement>>() {
      @Nullable
      public List<PropEntityAgentRequirement> get() {
        return CollectionsUtil.convertCollection(requirements, new Converter<PropEntityAgentRequirement, Requirement>() {
                  public PropEntityAgentRequirement createFrom(@NotNull final Requirement source) {
                    return new PropEntityAgentRequirement(source, fields.getNestedField("agent-requirement", Fields.NONE, Fields.LONG));
                  }
                });
      }
    });
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), requirements.size());
  }
}
