/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.deployment;

import java.text.ParseException;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.finder.impl.BuildPromotionFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentState;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentStateEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "deploymentStateEntry")
@XmlType(name = "deploymentStateEntry", propOrder = {"state", "deploymentDate", "build"})
@ModelDescription(
  value = "Represents a state of deployment instance."
)
public class StateEntry {
  @XmlAttribute
  public DeploymentState state;
  @XmlAttribute
  public String deploymentDate;
  @XmlElement(name = "build")
  @Nullable
  public Build build;

  public StateEntry() {
  }

  public StateEntry(
    @NotNull final DeploymentStateEntry deploymentStateEntry,
    @NotNull final Fields fields,
    @NotNull final BeanContext beanContext
  ) {
    state = ValueWithDefault.decideIncludeByDefault(
      fields.isIncluded("state"),
      deploymentStateEntry.getState()
    );

    deploymentDate = ValueWithDefault.decideIncludeByDefault(
      fields.isIncluded("name"),
      Util.formatTime(deploymentStateEntry.getChangeDate())
    );

    build = ValueWithDefault.decideDefault(fields.isIncluded("build", false), () -> {
      Long buildId = deploymentStateEntry.getBuildId();

      if (buildId == null) {
        return null;
      }

      try {
        BuildPromotion promotion = beanContext
          .getSingletonService(BuildPromotionFinder.class)
          .getBuildPromotionByIdOrByBuildId(buildId);
        return new Build(promotion, fields.getNestedField("build"), beanContext);
      } catch (NotFoundException e) {
        return null;
      }
    });
  }

  @NotNull
  public DeploymentStateEntry getEntryFromPosted() {
    try {
      return new DeploymentStateEntry(
        state,
        Util.resolveTime(deploymentDate),
        build == null ? null : build.getId()
      );
    } catch (ParseException e) {
      throw new BadRequestException(
        String.format("Could not parse value '%s' for attribute 'deploymentDate'.", deploymentDate)
      );
    }
  }
}
