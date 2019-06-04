/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
 *         Date: 17.11.2009
 */
public class Util {
  @Nullable
  public static String formatTime(@Nullable final Date time) {
    if (time == null) {
      return null;
    }
    return (new SimpleDateFormat(TeamCityProperties.getProperty("rest.defaultDateFormat", Constants.TIME_FORMAT), Locale.ENGLISH)).format(time);
  }

  public static String concatenatePath(final String... pathParts) {
    final StringBuilder result = new StringBuilder();
    for (String pathPart : pathParts) {
      if (pathPart == null) continue;;
      if (result.length() > 0 && '/' == result.charAt(result.length() - 1)) {
        result.append(StringUtil.removeLeadingSlash(pathPart));
      } else if (pathPart.startsWith("/")){
        result.append(pathPart);
      } else{
        result.append('/').append(pathPart);
      }
    }
    return result.toString();
  }

  @Nullable
  @Contract("null, _ -> null; !null, _ -> !null")
  public static <T, R> R resolveNull(@Nullable T t, @NotNull Function<T, R> f) { //todo: do we already have this?
    return t == null ? null : f.apply(t);
  }

  @NotNull
  public static <T, R> R resolveNull(@Nullable T t, @NotNull Function<T, R> f, @NotNull R resultForNull) {
    return t == null ? resultForNull : f.apply(t);
  }

  @Nullable
  public static String encodeUrlParamValue(@Nullable final String value) {
    if (value == null) return null;
    String result = WebUtil.encode(value);
    //make it more readable by not encoding common characters which regularly work in the clients and on the server
    result = result.replace("%24", "$")
                   .replace("%28", "(")
                   .replace("%29", ")")
                   .replace("%3A", ":")
                   .replace("%2C", ",");
    return result;
  }
}
