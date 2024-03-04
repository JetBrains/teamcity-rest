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

package jetbrains.buildServer.server.rest.data.problem;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.locator.Dimension;
import jetbrains.buildServer.server.rest.data.locator.EnumValue;
import jetbrains.buildServer.server.rest.data.locator.SubDimensionSyntax;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import org.jetbrains.annotations.NotNull;

public class Orders<T> {
  private final Map<String, Supplier<Comparator<T>>> myComparators = new LinkedHashMap<>();

  @NotNull
  public Orders<T> add(@NotNull String orderName, @NotNull Comparator<T> comparator) {
    myComparators.put(orderName, () -> comparator);
    return this;
  }

  @NotNull
  public Orders<T> add(@NotNull String orderName, @NotNull Supplier<Comparator<T>> comparatorFactory) {
    myComparators.put(orderName, comparatorFactory);
    return this;
  }

  @NotNull
  private Comparator<T> get(@NotNull String orderName) {
    Comparator<T> result = myComparators.get(orderName).get();
    if (result == null) throw new BadRequestException("Order \"" + orderName + "\" is not supported. Supported orders are: " + Arrays.toString(getNames()));
    return result;
  }

  @NotNull
  public String[] getNames() {
    return myComparators.keySet().toArray(new String[0]);
  }

  @NotNull
  public Comparator<T> getComparator(@NotNull final String orderLocatorText) {
    Locator locator = new Locator(orderLocatorText, getNames());
    if (locator.isSingleValue()) {
      // Not null by locator contract
      //noinspection ConstantConditions
      return get(locator.getSingleValue());
    }
    Comparator<T> ALL_EQUAL = (o1, o2) -> 0;
    Comparator<T> comparator = ALL_EQUAL;
    for (Map.Entry<String, Supplier<Comparator<T>>> compPair : myComparators.entrySet()) {
      String name = compPair.getKey();
      String dimensionValue = locator.getSingleDimensionValue(name);
      if (dimensionValue != null) {
        if ("asc".equals(dimensionValue) || dimensionValue.isEmpty()) {
          comparator = comparator.thenComparing(compPair.getValue().get());
        } else if ("desc".equals(dimensionValue)) {
          comparator = comparator.thenComparing(compPair.getValue().get().reversed());
        } else {
          throw new BadRequestException("Dimension \"" + name + "\" has invalid value \"" + dimensionValue + "\". Should be \"asc\" or \"desc\"");
        }
      }
    }
    locator.checkLocatorFullyProcessed();
    if (comparator == ALL_EQUAL) {
      throw new BadRequestException("No order is defined by the supplied ordering locator");
    }
    return comparator;
  }

  @NotNull
  public SubDimensionSyntax getSyntax() {
    return new SubDimensionSyntax() {
      @Override
      public Collection<Dimension> getSubDimensions() {
        return myComparators.keySet().stream()
                            .map(name -> Dimension.ofName(name).syntax(EnumValue.of("asc", "desc")).build())
                            .collect(Collectors.toList());
      }

      @Override
      public String getFormat() {
        return "";
      }
    };
  }
}