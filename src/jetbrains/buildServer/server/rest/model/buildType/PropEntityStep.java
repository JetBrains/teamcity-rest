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

import java.util.Collections;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.serverSide.BuildRunnerDescriptor;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.SBuildRunnerDescriptor;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "step")
public class PropEntityStep extends PropEntity {
  public PropEntityStep() {
  }

  public PropEntityStep(@NotNull SBuildRunnerDescriptor descriptor, @NotNull final BuildTypeSettings buildType, @NotNull final Fields fields) {
    super(descriptor.getId(), descriptor.getName(), descriptor.getType(), buildType.isEnabled(descriptor.getId()), descriptor.getParameters(), fields);
  }

  public SBuildRunnerDescriptor addStep(final BuildTypeSettings buildType) {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Created step cannot have empty 'type'.");
    }

    @SuppressWarnings("ConstantConditions")
    final SBuildRunnerDescriptor runnerToCreate =
      buildType.addBuildRunner(StringUtil.isEmpty(name) ? "" : name, type, properties != null ? properties.getMap() : Collections.<String, String>emptyMap());
    if (disabled != null) {
      buildType.setEnabled(runnerToCreate.getId(), !disabled);
    }
    return runnerToCreate;
  }

  public SBuildRunnerDescriptor updateStep(@NotNull final BuildTypeSettings buildType, @NotNull SBuildRunnerDescriptor step) {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Created step cannot have empty 'type'.");
    }

    if (!buildType.updateBuildRunner(step.getId(), StringUtil.isEmpty(name) ? "" : name, type, properties != null ? properties.getMap() : Collections.<String, String>emptyMap())) {
      throw new InvalidStateException("Update failed");
    }
    if (disabled != null) {
      buildType.setEnabled(step.getId(), !disabled);
    }
    return buildType.findBuildRunnerById(step.getId());
  }


  public static String getSetting(final BuildTypeSettings buildType, final BuildRunnerDescriptor step, final String name) {
    if ("name".equals(name)) {
      return step.getName();
    }
    if ("disabled".equals(name)) {
      return String.valueOf(!buildType.isEnabled(step.getId()));
    }
    throw new BadRequestException("Only 'name'and 'disabled' setting names are supported. '" + name + "' unknown.");
  }

  public static void setSetting(final BuildTypeSettings buildType,
                                final BuildRunnerDescriptor step,
                                final String name,
                                final String value) {
    if ("name".equals(name)) {
      buildType.updateBuildRunner(step.getId(), value, step.getType(), step.getParameters());
    } else if ("disabled".equals(name)) {
      buildType.setEnabled(step.getId(), !Boolean.parseBoolean(value));
    } else {
      throw new BadRequestException("Only 'name'and 'disabled' setting names are supported. '" + name + "' unknown.");
    }
  }
}
