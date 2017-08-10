/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import java.util.Date;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.mute.UnmuteOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 30.01.14
 */
@XmlType(name = "resolution")
public class Resolution {
  protected static final String MANUALLY = "manually";
  protected static final String WHEN_FIXED = "whenFixed";
  protected static final String TIME = "atTime";

  @XmlAttribute public String type;
  @XmlElement public String time;

  public Resolution() {
  }

  public Resolution(@NotNull final UnmuteOptions unmuteOptions, @NotNull final Fields fields) {
    type = ValueWithDefault.decideDefault(fields.isIncluded("type"), getType(unmuteOptions));

    Date unmuteByTime = unmuteOptions.getUnmuteByTime();
    if (unmuteByTime != null) {
      time = ValueWithDefault.decideDefault(fields.isIncluded("time"), Util.formatTime(unmuteByTime));
    }
  }

  @Nullable
  public static String getType(final @NotNull UnmuteOptions unmuteOptions) {
    if (unmuteOptions.isUnmuteManually()) {
      return MANUALLY;
    } else if (unmuteOptions.isUnmuteWhenFixed()) {
      return WHEN_FIXED;
    } else {
      if (unmuteOptions.getUnmuteByTime() != null) {
        return TIME;
      }
    }
    return null;
  }

  public Resolution(@NotNull final ResponsibilityEntry.RemoveMethod removeMethod, @NotNull final Fields fields) {
    if (removeMethod.isManually()) {
      type = ValueWithDefault.decideDefault(fields.isIncluded("type"), MANUALLY);
    } else if (removeMethod.isWhenFixed()) {
      type = ValueWithDefault.decideDefault(fields.isIncluded("type"), WHEN_FIXED);
    }
  }

  @NotNull
  public ResponsibilityEntry.RemoveMethod getFromPosted() {
    if (type == null) {
      throw new BadRequestException("Invalid 'resolution' entity: 'type' should be specified");
    }
    switch (type) {
      case MANUALLY: return ResponsibilityEntry.RemoveMethod.MANUALLY;
      case WHEN_FIXED: return ResponsibilityEntry.RemoveMethod.WHEN_FIXED;
    }
    throw new BadRequestException("Invalid 'resolution' entity: unknown 'type' value '" + type + "', supported are: " + MANUALLY + ", " + WHEN_FIXED);
  }

  @NotNull
  public static String[] getKnownTypesForMute() {
    return new String[]{MANUALLY, WHEN_FIXED, TIME};
  }

}
