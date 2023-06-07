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

package jetbrains.buildServer.server.rest.data.locator.definition;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.server.rest.data.finder.syntax.CommonLocatorDimensions;
import jetbrains.buildServer.server.rest.data.locator.Dimension;
import jetbrains.buildServer.server.rest.data.locator.SubDimensionSyntaxImpl;
import org.jetbrains.annotations.NotNull;

public class LocatorDefinitionUtil {
  @NotNull
  public static Stream<Dimension> getVisibleDimensions(@NotNull Class<? extends LocatorDefinition> definition) {
    return getAllDimensions(definition).filter(dim -> !dim.isHidden());
  }

  @NotNull
  public static Stream<Dimension> getHiddenDimensions(@NotNull Class<? extends LocatorDefinition> definition) {
    return getAllDimensions(definition).filter(Dimension::isHidden);
  }

  @NotNull
  public static Stream<Dimension> getAllDimensions(@NotNull Class<? extends LocatorDefinition> definition) {
    Map<String, Dimension> dimensions = getDimensionsFromDefinition(definition);

    if (FinderLocatorDefinition.class.isAssignableFrom(definition)) {
      Map<String, Dimension> finderDims = getFinderSpecialDimensions((Class<? extends FinderLocatorDefinition>) definition);

      for(String name : finderDims.keySet()) {
        dimensions.computeIfAbsent(name, n -> finderDims.get(n));
      }
    }

    return dimensions.values().stream();
  }

  @NotNull
  private static Map<String, Dimension> getDimensionsFromDefinition(@NotNull Class<? extends LocatorDefinition> definition) {
    return Arrays.stream(definition.getFields())
                 .filter(field -> java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                 .filter(field -> Dimension.class.isAssignableFrom(field.getType()))
                 .map(field -> {
                        try {
                          return (Dimension)field.get(null);
                        } catch (IllegalAccessException e) {
                          throw new RuntimeException(e);
                        }
                      }
                 ).collect(Collectors.toMap(Dimension::getName, dim -> dim));
  }

  @NotNull
  private static Map<String, Dimension> getFinderSpecialDimensions(@NotNull Class<? extends FinderLocatorDefinition> definition) {
    return Stream.of(
      CommonLocatorDimensions.PAGER_COUNT,
      CommonLocatorDimensions.PAGER_START,
      CommonLocatorDimensions.UNIQUE,
      CommonLocatorDimensions.LOOKUP_LIMIT,
      CommonLocatorDimensions.LOGICAL_OR(() -> new SubDimensionSyntaxImpl(definition)),
      CommonLocatorDimensions.LOGICAL_AND(() -> new SubDimensionSyntaxImpl(definition)),
      CommonLocatorDimensions.LOGICAL_NOT(() -> new SubDimensionSyntaxImpl(definition)),
      CommonLocatorDimensions.ITEM(() -> new SubDimensionSyntaxImpl(definition))
    ).collect(Collectors.toMap(Dimension::getName, dim -> dim));
  }
}
