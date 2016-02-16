/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Constants;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 15/02/2016
 */
public class TimeWithPrecision {
  protected static final List<TimeFormat> TIME_FORMATS;

  static {
    TIME_FORMATS = new ArrayList<>();
    TIME_FORMATS.add(new TimeFormat(Constants.TIME_FORMAT, true));  //ISO 8601-like, with RFC 822 timezone - the only used format before TeamCity 10
    TIME_FORMATS.add(new TimeFormat("yyyyMMdd'T'HHmmssX", true)); //ISO 8601 (the same as above with ISO 8601 time zone)
    TIME_FORMATS.add(new TimeFormat("yyyyMMdd'T'HHmmss.SSSX", false)); //ms precision
    TIME_FORMATS.add(new TimeFormat("yyyy-MM-dd'T'HH:mm:ssX", true)); //another variance of ISO 8601
  }

  @NotNull private final Date myTime;
  private final boolean mySecondsPrecision;

  public TimeWithPrecision(@NotNull final Date time, final boolean secondsPrecision) {
    myTime = time;
    mySecondsPrecision = secondsPrecision;
  }

  @NotNull
  public Date getTime() {
    return myTime;
  }

  public boolean isSecondsPrecision() {
    return mySecondsPrecision;
  }

  @NotNull
  public static TimeWithPrecision parse(@NotNull String timeString) {
    ParseException firstError = null;
    for (TimeFormat timeFormat : TIME_FORMATS) {
      try {
        return new TimeWithPrecision(new SimpleDateFormat(timeFormat.myTimeFormat, Locale.ENGLISH).parse(timeString), timeFormat.mySecondsPrecision);
      } catch (ParseException e) {
        if (firstError == null) firstError = e;
      }
    }
    //todo: consider using ISODateTimeFormat here

    assert firstError != null;

    StringBuilder message = new StringBuilder();
    Date exampleTime = new Date();
    // throwing original error
    message.append("Could not parse date from value '").append(timeString).append("'. Supported format examples are: ");
    for (TimeFormat timeFormat : TIME_FORMATS) {
      message.append(new SimpleDateFormat(timeFormat.myTimeFormat, Locale.ENGLISH).format(exampleTime)).append(", ");
    }
    message.delete(message.length() - ", ".length(), message.length());
    message.append(". First reported error: ").append(firstError.getMessage());
    throw new BadRequestException(message.toString(), firstError);
  }

  private static class TimeFormat {
    @NotNull private final String myTimeFormat;
    private final boolean mySecondsPrecision;

    public TimeFormat(@NotNull final String timeFormat, final boolean secondsPrecision) {
      myTimeFormat = timeFormat;
      mySecondsPrecision = secondsPrecision;
    }
  }
}
