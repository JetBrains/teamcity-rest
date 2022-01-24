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

package jetbrains.buildServer.server.rest.model.problem;

import java.util.Arrays;
import java.util.Date;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.server.rest.data.TimeCondition;
import jetbrains.buildServer.server.rest.data.TimeWithPrecision;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.RelatedEntity;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.mute.UnmuteOptions;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 30.01.14
 */
@XmlType(name = "resolution")
@ModelDescription("Represents an investigation resolution timestamp and details.")
public class Resolution {
  @XmlAttribute public ResolutionType type;
  @XmlElement public String time;

  public Resolution() {
  }

  public Resolution(@NotNull final UnmuteOptions unmuteOptions, @NotNull final Fields fields) {
    type = ValueWithDefault.decideDefault(fields.isIncluded("type"), ResolutionType.getType(unmuteOptions));

    Date unmuteByTime = unmuteOptions.getUnmuteByTime();
    if (unmuteByTime != null) {
      time = ValueWithDefault.decideDefault(fields.isIncluded("time"), Util.formatTime(unmuteByTime));
    }
  }
  
  public enum ResolutionType {
    manually, whenFixed, atTime;

    public boolean equalsIgnoreCase(ResolutionType type) {
      return equalsIgnoreCase(type.toString());
    }
    
    public boolean equalsIgnoreCase(String s) {
      return toString().equalsIgnoreCase(s);
    }

    public static ResolutionType getType(final @NotNull UnmuteOptions unmuteOptions) {
      if (unmuteOptions.isUnmuteManually()) {
        return ResolutionType.manually;
      } else if (unmuteOptions.isUnmuteWhenFixed()) {
        return ResolutionType.whenFixed;
      } else {
        if (unmuteOptions.getUnmuteByTime() != null) {
          return ResolutionType.atTime;
        }
      }
      return null;
    }
  }

  public Resolution(@NotNull final ResponsibilityEntry.RemoveMethod removeMethod, @NotNull final Fields fields) {
    if (removeMethod.isManually()) {
      type = ValueWithDefault.decideDefault(fields.isIncluded("type"), ResolutionType.manually);
    } else if (removeMethod.isWhenFixed()) {
      type = ValueWithDefault.decideDefault(fields.isIncluded("type"), ResolutionType.whenFixed);
    }
  }

  @NotNull
  public ResponsibilityEntry.RemoveMethod getFromPostedForInvestigation(@NotNull final ServiceLocator serviceLocator) { //parameter here heps to workaround https://youtrack.jetbrains.com/issue/TW-54657
    if (type == null) {
      throw new BadRequestException("Invalid 'resolution' entity: 'type' should be specified");
    }
    try {
      return getRemoveMethodForInvestigation(type.toString());
    } catch (BadRequestException e) {
      throw new BadRequestException("Invalid 'resolution' entity for investigation: " + e.getMessage(), e);
    }
  }

  @NotNull
  public static ResponsibilityEntry.RemoveMethod getRemoveMethodForInvestigation(@NotNull final String resolutionTextValue) {
    try {
      return ResponsibilityEntry.RemoveMethod.from(resolutionTextValue);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage(), e);
    }
  }

  @NotNull
  public UnmuteOptions getFromPostedForMute(@NotNull final ServiceLocator serviceLocator) {
    if (type == null) {
      throw new BadRequestException("Invalid 'resolution' entity: 'type' should be specified");
    }
    if (Arrays.stream(getKnownTypesForMute()).noneMatch(s -> type.equalsIgnoreCase(s))) {
      throw new BadRequestException("Invalid 'resolution' entity's 'type': '" + type + "'. Should be one of: " + StringUtil.join(getKnownTypesForMute(), ", "));
    }

    return new UnmuteOptions() {
      @Override
      public boolean isUnmuteManually() {
        return ResolutionType.manually.equalsIgnoreCase(type);
      }

      @Override
      public boolean isUnmuteWhenFixed() {
        return ResolutionType.whenFixed.equalsIgnoreCase(type);
      }

      @Nullable
      @Override
      public Date getUnmuteByTime() {
        if (!ResolutionType.atTime.equalsIgnoreCase(type)) {
          return null;
        }
        if (time == null) {
          throw new BadRequestException("Invalid 'resolution' entity for mute: no 'time' is present for '" + ResolutionType.atTime + "' type");
        }
        return TimeWithPrecision.parse(time, TimeCondition.getTimeService(serviceLocator)).getTime();
      }
    };
  }

  @NotNull
  public static String[] getKnownTypesForMute() {
    return Arrays.stream(ResolutionType.values()).map(Enum::name).toArray(String[]::new);
  }

}
