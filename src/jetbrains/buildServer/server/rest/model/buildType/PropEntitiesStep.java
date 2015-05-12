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

package jetbrains.buildServer.server.rest.model.buildType;

import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.SBuildRunnerDescriptor;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "steps")
@SuppressWarnings("PublicField")
public class PropEntitiesStep {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "step")
  public List<PropEntityStep> propEntities;

  public PropEntitiesStep() {
  }

  public PropEntitiesStep(@NotNull final BuildTypeSettings buildType, @NotNull final Fields fields) {
    final List<SBuildRunnerDescriptor> buildRunners = buildType.getBuildRunners();
    propEntities = ValueWithDefault.decideDefault(fields.isIncluded("step"), new ValueWithDefault.Value<List<PropEntityStep>>() {
      @Nullable
      public List<PropEntityStep> get() {
        return CollectionsUtil.convertCollection(buildRunners,
                                                 new Converter<PropEntityStep, SBuildRunnerDescriptor>() {
                                                   public PropEntityStep createFrom(@NotNull final SBuildRunnerDescriptor source) {
                                                     return new PropEntityStep(source, buildType, fields.getNestedField("step", Fields.NONE, Fields.LONG));
                                                   }
                                                 });
      }
    });
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), buildRunners.size());
  }

}
