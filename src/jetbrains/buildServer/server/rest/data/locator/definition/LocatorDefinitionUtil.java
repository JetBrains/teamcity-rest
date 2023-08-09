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
                 );
  }
}
