/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 10.01.14
 */
public class ValueWithDefault {
  @Nullable
  public static <T> T decideDefault(@Nullable Boolean include, @Nullable T value) {
    return decide(include, value, null, !ValueWithDefault.isDefault(value));
  }

  @Nullable
  public static <T> T decide(@Nullable Boolean decision, @Nullable T trueValue, @Nullable T falseValue, boolean defaultDecision) {
    boolean actualDecision;
    if (decision == null) {
      actualDecision = defaultDecision;
    } else {
      actualDecision = decision;
    }

    if (actualDecision) {
      return trueValue;
    } else {
      return falseValue;
    }
  }

  @Nullable
  public static <T> T decideDefault(@Nullable Boolean include, @NotNull Value<T> value) {
    if (include == null) {
      final T resultValue = value.get();
      return ValueWithDefault.isDefault(resultValue) ? null : resultValue;
    } else {
      return include ? value.get() : null;
    }
  }

  public static <T> boolean isDefault(@Nullable final T value) {
    if (value == null) return true;

    if (value.getClass().isAssignableFrom(Integer.class)) return (Integer)value == 0;
    if (value.getClass().isAssignableFrom(Long.class)) return (Long)value == 0;
    if (value.getClass().isAssignableFrom(Boolean.class)) {//noinspection PointlessBooleanExpression
      return (Boolean)value == false;
    }
    if (value.getClass().isAssignableFrom(Collection.class)) return ((Collection)value).size() == 0;
    if (value.getClass().isAssignableFrom(DefaultValueAware.class)) {
      return !((DefaultValueAware)value).isDefault();
    }

    return false;
  }

  public static <T> boolean isAllDefault(@Nullable final T... values) {
    if (values != null) {
      for (T value : values) {
        if (!isDefault(value)) return false;
      }
    }
    return true;
  }


  public interface Value<S> {
    public static Value NULL = new Value() {
      @Nullable
      public Object get() {
        return null;
      }
    };

    @Nullable
    S get();
  }

}
