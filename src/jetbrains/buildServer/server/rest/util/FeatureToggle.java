/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.util;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"WeakerAccess", "unused"})
public class FeatureToggle {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(FeatureToggle.class);

  @Nullable
  public static <T> T withToggle(@NotNull final String toggle, @NotNull final ValueWithDefault.Value<T> supplier) {
    return withToggle(toggle, supplier, (T)null);
  }

  @Nullable
  public static <T> T withToggle(@NotNull final String toggle, @NotNull final ValueWithDefault.Value<T> supplier, @Nullable final T valueForDisabled) {
    return withToggle(toggle, supplier, (ValueWithDefault.Value<T>)() -> valueForDisabled);
  }

  @Nullable
  public static <T> T withToggle(@NotNull final String toggle, @NotNull final ValueWithDefault.Value<T> supplier, @NotNull final ValueWithDefault.Value<T> supplierForDisabled) {
    return withToggleDeferred(toggle, supplier, supplierForDisabled).get();
  }

  @NotNull
  public static <T> ValueWithDefault.Value<T> withToggleDeferred(@NotNull final String toggle, @NotNull final ValueWithDefault.Value<T> supplier) {
    return withToggleDeferred(toggle, supplier, (T)null);
  }

  @NotNull
  public static <T> ValueWithDefault.Value<T> withToggleDeferred(@NotNull final String toggle,
                                                                 @NotNull final ValueWithDefault.Value<T> supplier,
                                                                 @Nullable final T valueForDisabled) {
    return withToggleDeferred(toggle, supplier, (ValueWithDefault.Value<T>)() -> valueForDisabled);
  }

  @NotNull
  public static <T> ValueWithDefault.Value<T> withToggleDeferred(@NotNull final String toggle,
                                                                 @NotNull final ValueWithDefault.Value<T> supplier,
                                                                 @NotNull final ValueWithDefault.Value<T> supplierForDisabled) {
    if (TeamCityProperties.getBoolean(toggle)) {
      return supplier;
    } else {
      return () -> {
        LOGGER.debug(() -> "Feature " + toggle + " is not enabled, " + supplier + " will not be executed. Returning default value instead");
        return supplierForDisabled.get();
      };
    }
  }

  @Nullable
  public static <T> T withToggleOnByDefault(@NotNull final String toggle, @NotNull final ValueWithDefault.Value<T> supplier) {
    return withToggleOnByDefault(toggle, supplier, (T)null);
  }

  @Nullable
  public static <T> T withToggleOnByDefault(@NotNull final String toggle, @NotNull final ValueWithDefault.Value<T> supplier, @Nullable final T valueForDisabled) {
    return withToggleOnByDefault(toggle, supplier, (ValueWithDefault.Value<T>)() -> valueForDisabled);
  }

  @Nullable
  public static <T> T withToggleOnByDefault(@NotNull final String toggle,
                                            @NotNull final ValueWithDefault.Value<T> supplier,
                                            @NotNull final ValueWithDefault.Value<T> supplierForDisabled) {
    return withToggleOnByDefaultDeferred(toggle, supplier, supplierForDisabled).get();
  }

  @NotNull
  public static <T> ValueWithDefault.Value<T> withToggleOnByDefaultDeferred(@NotNull final String toggle, @NotNull final ValueWithDefault.Value<T> supplier) {
    return withToggleOnByDefaultDeferred(toggle, supplier, (T)null);
  }

  @NotNull
  public static <T> ValueWithDefault.Value<T> withToggleOnByDefaultDeferred(@NotNull final String toggle,
                                                                            @NotNull final ValueWithDefault.Value<T> supplier,
                                                                            @Nullable final T valueForDisabled) {
    return withToggleOnByDefaultDeferred(toggle, supplier, (ValueWithDefault.Value<T>)() -> valueForDisabled);
  }

  @NotNull
  public static <T> ValueWithDefault.Value<T> withToggleOnByDefaultDeferred(@NotNull final String toggle,
                                                                            @NotNull final ValueWithDefault.Value<T> supplier,
                                                                            @NotNull final ValueWithDefault.Value<T> supplierForDisabled) {
    if (TeamCityProperties.getBooleanOrTrue(toggle)) {
      return supplier;
    } else {
      return () -> {
        LOGGER.debug(() -> "Feature " + toggle + " is not enabled, " + supplier + " will not be executed. Returning default value instead");
        return supplierForDisabled.get();
      };
    }
  }

  public static void withToggle(@NotNull final String toggle, @NotNull final Runnable action) {
    if (TeamCityProperties.getBoolean(toggle)) {
      action.run();
    } else {
      LOGGER.debug(() -> "Feature " + toggle + " is not enabled, " + action + " will not be executed");
    }
  }

  @NotNull
  public static Runnable withToggleDeferred(@NotNull final String toggle, @NotNull final Runnable action) {
    if (TeamCityProperties.getBoolean(toggle)) {
      return action;
    } else {
      return () -> LOGGER.debug(() -> "Feature " + toggle + " is not enabled, " + action + " will not be executed");
    }
  }

  public static void withToggleOnByDefault(@NotNull final String toggle, @NotNull final Runnable action) {
    if (TeamCityProperties.getBooleanOrTrue(toggle)) {
      action.run();
    } else {
      LOGGER.debug(() -> "Feature " + toggle + " is not enabled, " + action + " will not be executed");
    }
  }

  @NotNull
  public static Runnable withToggleOnByDefaultDeferred(@NotNull final String toggle, @NotNull final Runnable action) {
    if (TeamCityProperties.getBooleanOrTrue(toggle)) {
      return action;
    } else {
      return () -> LOGGER.debug(() -> "Feature " + toggle + " is not enabled, " + action + " will not be executed");
    }
  }
}
