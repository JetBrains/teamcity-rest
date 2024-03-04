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

package jetbrains.buildServer.server.rest.model;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Function;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 * @since 17.11.2009
 */
public class Util {
  @Contract("null -> null; !null -> !null")
  public static String formatTime(@Nullable final Date time) {
    if (time == null) {
      return null;
    }
    return (new SimpleDateFormat(TeamCityProperties.getProperty("rest.defaultDateFormat", Constants.TIME_FORMAT), Locale.ENGLISH)).format(time);
  }

  @Contract("null -> null; !null -> !null")
  public static Date resolveTime(@Nullable final String timestamp) throws ParseException {
    if (timestamp == null || timestamp.isEmpty()) {
      return null;
    }

    SimpleDateFormat format = new SimpleDateFormat(
      TeamCityProperties.getProperty("rest.defaultDateFormat", Constants.TIME_FORMAT),
      Locale.ENGLISH
    );

    return format.parse(timestamp);
  }

  public static String concatenatePath(final String... pathParts) {
    final StringBuilder result = new StringBuilder();
    for (String pathPart : pathParts) {
      if (pathPart == null) continue;;
      if (result.length() > 0 && '/' == result.charAt(result.length() - 1)) {
        result.append(StringUtil.removeLeadingSlash(pathPart));
      } else if (pathPart.startsWith("/")) {
        result.append(pathPart);
      } else {
        result.append('/').append(pathPart);
      }
    }
    return result.toString();
  }

  /**
   * Applies mapping function if the value is not null. Returns null otherwise.
   * It does not resolve any nulls. The method should be named `resolveOrNull`.
   *
   * @param value the initial value
   * @param mapper mapping function to be applied if value is not null
   * @param <T> the initial value type
   * @param <R> the result type
   * @return
   * @deprecated since 01.2023. Use `Optional.ofNullable(value).map(mapper).orElse(null);` instead;
   */
  @Nullable
  @Contract("null, _ -> null; !null, _ -> !null")
  @Deprecated
  public static <T, R> R resolveNull(@Nullable T value, @NotNull Function<T, R> mapper) { //todo: do we already have this?
    return value == null ? null : mapper.apply(value);
  }

  /**
   * Maps value with function, or returns default result if value is null.
   *
   * @param value         the value
   * @param mapper        the mapping function
   * @param resultForNull the return value if `value` is null
   * @param <T>           the initial value type
   * @param <R>           the result type
   * @return the result of mapping function applied to `value`, or if `value` is null returns `resultForNull`
   * @deprecated since 01.2023 Use `Optional.ofNullable(value).map(mapper).orElse(resultForNull);` instead;
   */
  @NotNull
  @Deprecated
  public static <T, R> R resolveNull(@Nullable T value, @NotNull Function<T, R> mapper, @NotNull R resultForNull) {
    return value == null ? resultForNull : mapper.apply(value);
  }

  @Contract("null -> null; !null -> !null")
  public static String encodeUrlParamValue(@Nullable final String value) {
    if (value == null) {
      return null;
    }

    return humanReadableUrlParamValue(WebUtil.encode(value));
  }

  /**
   * Makes the url more readable by not encoding common characters, which regularly work in the clients and on the server.
   */
  @NotNull
  public static String humanReadableUrlParamValue(@NotNull String sourceUrl) {
    if(TeamCityProperties.getBooleanOrTrue("rest.model.readableHrefsEnabled")) {
      return sourceUrl.replace("%24", "$")
                      .replace("%28", "(")
                      .replace("%29", ")")
                      .replace("%3A", ":")
                      .replace("%2C", ",");
    } else {
      return sourceUrl;
    }

  }
}