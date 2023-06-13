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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class EnumValue implements PlainValue {
  public static <E extends Enum<E>> EnumValue of(@NotNull Class<E> enumClass) {
    return new EnumValue(Arrays.stream(enumClass.getEnumConstants()).map(v -> v.name().toLowerCase()).collect(Collectors.toList()));
  }

  public static EnumValue of(@NotNull Collection<String> values) {
    return new EnumValue(values.stream().distinct().collect(Collectors.toList()));
  }

  public static EnumValue of(@NotNull String... values) {
    return new EnumValue(Arrays.stream(values).distinct().collect(Collectors.toList()));
  }

  private final List<String> myValues;
  private EnumValue(@NotNull List<String> values) {
    myValues = values;
  }

  @NotNull
  public List<String> getValues() {
    return myValues;
  }

  @Override
  public String getFormat() {
    return "['" + String.join("','") + "']";
  }
}
