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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 17.01.12
 */
public class ValueCondition {
  @Nullable private final String myParameterValue;
  @NotNull private final RequirementType myRequirementType;

  public ValueCondition(@NotNull final RequirementType requirementType, @Nullable final String value) {
    myParameterValue = value;
    myRequirementType = requirementType;
    //todo: use this code  and drop isInvalid()
    //if (myRequirementType.isParameterRequired() && myParameterValue == null) {
    //  throw new BadRequestException("Wrong parameter condition: requirement type '" + requirementType.getName() + "' requires specification of the value");
    //}
  }

  private static boolean matches(@NotNull final RequirementType requirementType, @Nullable final String requirementValue, @Nullable final String actualValue) {
    if (requirementType.isParameterRequired() && requirementValue == null) {
      return false;
    }
    if (requirementType.isActualValueRequired() && actualValue == null) {
      return false;
    }
    if (!requirementType.isActualValueCanBeEmpty() && (actualValue == null || actualValue.length() == 0)) {
      return false;
    }
    try {
      return requirementType.matchValues(requirementValue, actualValue);
    } catch (Exception e) {
      //e.g. more-than can throw NumberFormatException for non-number
      return false;
    }
  }

  public boolean matches(@Nullable final String value) {
    return matches(myRequirementType, myParameterValue, value);
  }

  @Nullable
  public String getConstantValueIfSimpleEqualsCondition() {
    if (RequirementType.EQUALS.equals(myRequirementType)) return myParameterValue;
    return null;
  }

  public boolean isInvalid() {
    return myRequirementType.isParameterRequired() && myParameterValue == null;
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
