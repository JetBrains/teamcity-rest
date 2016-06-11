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

package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 17.01.12
 */
public class ValueCondition {
  @Nullable private final String myParameterValue;
  @NotNull private final RequirementType myRequirementType;
  @Nullable private Boolean myIgnoreCase;

  public ValueCondition(@NotNull final RequirementType requirementType, @Nullable final String value, @Nullable final Boolean ignoreCase) {
    myParameterValue = value;
    myRequirementType = requirementType;
    myIgnoreCase = ignoreCase;
    if (myRequirementType.isParameterRequired() && myParameterValue == null) {
      throw new BadRequestException("Wrong parameter condition: requirement type '" + requirementType.getName() + "' requires specification of the value");
    }
  }

  public boolean getActualIgnoreCase() {
    return myIgnoreCase == null ? false : myIgnoreCase;
  }

  @Nullable
  public Boolean getIgnoreCase() {
    return myIgnoreCase;
  }

  public void setIgnoreCase(final boolean ignoreCase) {
    myIgnoreCase = ignoreCase;
  }

  public boolean matches(@Nullable final String value) {
    if (myRequirementType.isParameterRequired() && myParameterValue == null) {
      return false;
    }
    if (myRequirementType.isActualValueRequired() && value == null) {
      return false;
    }
    if (!myRequirementType.isActualValueCanBeEmpty() && (value == null || value.length() == 0)) {
      return false;
    }
    try {
      if (getActualIgnoreCase()) {
        return myRequirementType.matchValues(toLower(myParameterValue), toLower(value));
      } else {
        return myRequirementType.matchValues(myParameterValue, value);
      }
    } catch (Exception e) {
      //e.g. more-than can throw NumberFormatException for non-number
      return false;
    }
  }

  @Nullable
  @Contract("!null -> !null; null -> null")
  private String toLower(@Nullable final String value) {
    return value == null ? null : value.toLowerCase();
  }

  @Nullable
  public String getConstantValueIfSimpleEqualsCondition() {
    if (RequirementType.EQUALS.equals(myRequirementType) && !getActualIgnoreCase()) return myParameterValue;
    return null;
  }

  @Override
  public String toString() {
    final StringBuilder result = new StringBuilder();
    result.append("Value condition (");
    result.append(myRequirementType.getName()).append(", ");
    if (myParameterValue != null) result.append("value:").append(myParameterValue).append(", ");
    result.append(")");
    return result.toString();
  }
}
