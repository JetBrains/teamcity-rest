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

import com.intellij.openapi.diagnostic.Logger;
import java.util.Collection;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 10.01.14
 */
public class ValueWithDefault {
  final static Logger LOG = Logger.getInstance(ValueWithDefault.class.getName());

  @Nullable
  public static <T> T decideDefault(@Nullable Boolean include, @Nullable T value) {
    return decide(include, value, null, !ValueWithDefault.isDefault(value));
  }

  @Nullable
  public static <T> T decideIncludeByDefault(@Nullable Boolean include, @Nullable T value) {
    return decide(include, value, null, true);
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
  public static <T> T decide(@Nullable Boolean decision, @Nullable Value<T> trueValue, @Nullable Value<T> falseValue, boolean defaultDecision) {
    boolean actualDecision;
    if (decision == null) {
      actualDecision = defaultDecision;
    } else {
      actualDecision = decision;
    }

    if (actualDecision) {
      return trueValue == null ? null : trueValue.get();
    } else {
      return falseValue == null ? null : falseValue.get();
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

  @Nullable
  public static <T> T decideDefaultIgnoringAccessDenied(@Nullable Boolean include, @NotNull Value<T> value) {
    try {
      return decideDefault(include, value);
    } catch (AccessDeniedException e) {
      if (LOG.isDebugEnabled()) LOG.debug("Got permisisons issue while getting value, ignoring the field. Error: " + e.toString());
      return null;
    } catch (AuthorizationFailedException e) {
      if (LOG.isDebugEnabled()) LOG.debug("Got permisisons issue while getting value, ignoring the field. Error: " + e.toString());
      return null;
    }
  }

  public static <T> boolean isDefault(@Nullable final T value) {
    if (value == null) return true;

    if (Integer.class.isAssignableFrom(value.getClass())) return (Integer)value == 0;
    if (Long.class.isAssignableFrom(value.getClass())) return (Long)value == 0;
    if (Boolean.class.isAssignableFrom(value.getClass())) {//noinspection PointlessBooleanExpression
      return (Boolean)value == false;
    }
    if (Collection.class.isAssignableFrom(value.getClass())) return ((Collection)value).size() == 0;
    if (DefaultValueAware.class.isAssignableFrom(value.getClass())) {
      return ((DefaultValueAware)value).isDefault();
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
