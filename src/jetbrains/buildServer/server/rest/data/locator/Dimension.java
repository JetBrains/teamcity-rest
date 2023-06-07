/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.locator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Locator dimension definition.
 * Two most important parts are `name and `syntax`.
 * `name` is unique in the locator definition, only one dimension with the same name can be defined.
 * `syntax` defines what values can be passed to this dimension. It may be some simple value, e.g. integer or string, or compound syntax with subdimensions.
 */
public interface Dimension {
  @NotNull
  String getName();

  @NotNull
  Syntax getSyntax();

  @Nullable
  String getDescription();

  boolean isHidden();

  boolean isRepeatable();

  @NotNull
  static DimensionBuilder ofName(@NotNull String name) {
    return new DimensionBuilder(name);
  }
}
