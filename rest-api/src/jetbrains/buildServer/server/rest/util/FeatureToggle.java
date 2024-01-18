/*
 * Copyright 2000-2024 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.util.ValueWithDefault.Value;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FeatureToggle {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(FeatureToggle.class);

  @Nullable
  public static <T> T withToggleOnByDefault(@NotNull String toggle, @NotNull Value<T> supplier) {
    return withToggleOnByDefault(toggle, supplier, (T)null);
  }

  @Nullable
  public static <T> T withToggleOnByDefault(@NotNull String toggle, @NotNull Value<T> supplier, @Nullable T valueForDisabled) {
    return withToggleOnByDefault(toggle, supplier, (Value<T>)() -> valueForDisabled);
  }

  @Nullable
  public static <T> T withToggleOnByDefault(@NotNull String toggle,
                                            @NotNull Value<T> supplier,
                                            @NotNull Value<T> supplierForDisabled) {
    return withToggleOnByDefaultDeferred(toggle, supplier, supplierForDisabled).get();
  }

  @NotNull
  public static <T> Value<T> withToggleOnByDefaultDeferred(@NotNull String toggle, @NotNull Value<T> supplier) {
    return withToggleOnByDefaultDeferred(toggle, supplier, (T)null);
  }

  @NotNull
  public static <T> Value<T> withToggleOnByDefaultDeferred(
    @NotNull String toggle,
    @NotNull Value<T> supplier,
    @Nullable T valueForDisabled
  ) {
    return withToggleOnByDefaultDeferred(toggle, supplier, (Value<T>)() -> valueForDisabled);
  }

  @NotNull
  public static <T> Value<T> withToggleOnByDefaultDeferred(
    @NotNull String toggle,
    @NotNull Value<T> supplier,
    @NotNull Value<T> supplierForDisabled
  ) {
    if (TeamCityProperties.getBooleanOrTrue(toggle)) {
      return supplier;
    } else {
      return () -> {
        LOGGER.debug(() -> "Feature " + toggle + " is not enabled, " + supplier + " will not be executed. Returning default value instead");
        return supplierForDisabled.get();
      };
    }
  }

}