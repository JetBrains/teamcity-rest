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

package jetbrains.buildServer.server.rest.data.util;

import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.server.rest.data.Locator.ANY_LITERAL;
import static jetbrains.buildServer.server.rest.data.Locator.BOOLEAN_ANY;
import static jetbrains.buildServer.server.rest.data.Locator.BOOLEAN_FALSE;
import static jetbrains.buildServer.server.rest.data.Locator.BOOLEAN_TRUE;

public class LocatorUtil {
  public static boolean isAny(@NotNull final String value) {
    return ANY_LITERAL.equals(value);
  }

  @Nullable
  public static Boolean getBooleanByValue(@Nullable final String value) {
    if (value == null || "all".equalsIgnoreCase(value) || BOOLEAN_ANY.equalsIgnoreCase(value) || isAny(value)) {
      return null;
    }
    final Boolean result = getStrictBoolean(value);
    if (result != null) return result;
    throw new LocatorProcessException("Invalid boolean value '" + value + "'. Should be 'true', 'false' or 'any'.");
  }

  /**
   * "any" is not supported
   * @return "null" if cannot be parsed as boolean
   */
  @Nullable
  public static Boolean getStrictBoolean(@Nullable final String value) {
    if (BOOLEAN_TRUE.equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "in".equalsIgnoreCase(value)) {
      return true;
    }
    if (BOOLEAN_FALSE.equalsIgnoreCase(value) || "off".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value) || "out".equalsIgnoreCase(value)) {
      return false;
    }
    return null;
  }

  public static boolean getStrictBooleanOrReportError(@NotNull final String value) {
    final Boolean result = LocatorUtil.getStrictBoolean(value);
    if (result != null) return result;
    throw new LocatorProcessException("Invalid strict boolean value '" + value + "'. Should be 'true' or 'false'.");
  }
}
