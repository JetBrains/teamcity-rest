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
import jetbrains.buildServer.util.TimeService;
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
  public static TimeWithPrecision parse(@NotNull String timeString, @NotNull final TimeService timeService) {
    if (timeString.startsWith("-")) {
      return new TimeWithPrecision(new Date(timeService.now() - TimeWithPrecision.getMsFromRelativeTime(timeString.substring("-".length()))), true);
    } else if (timeString.startsWith("+")) {
      return new TimeWithPrecision(new Date(timeService.now() + TimeWithPrecision.getMsFromRelativeTime(timeString.substring("+".length()))), true);
    }

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

  public static long getMsFromRelativeTime(@NotNull final String relativeTimeString) {
    ParseResult result = new ParseResult(relativeTimeString);
    result.processTimeValue("y", 365 * 24 * 60 * 60 * 1000L);
    result.processTimeValue("w", 7 * 24 * 60 * 60 * 1000L);
    result.processTimeValue("mo", 30 * 24 * 60 * 60 * 1000L);
    /* support for "m" for month in case of 5m2h
    int mIndex = result.myTimeText.indexOf("m");
    int dIndex = result.myTimeText.indexOf("d");
    int hIndex = result.myTimeText.indexOf("h");
    if (mIndex != -1 && (dIndex != -1 || hIndex != -1) && (mIndex < dIndex || mIndex < hIndex)){
      //treat this "m" as month
      result.processTimeValue("m", 30 * 24 * 60 * 60 * 1000L);
    }
    */
    result.processTimeValue("d", 24 * 60 * 60 * 1000L);
    result.processTimeValue("h", 60 * 60 * 1000L);
    result.processTimeValue("m", 60 * 1000L);
//    result.processTimeValue("min", 60 * 1000L);
    result.processTimeValue("s", 1000L);
    if (!result.myTimeText.isEmpty()) {
      throw new BadRequestException("Unsupported relative time '" + result.myTimeText + "': supported format example: '-4w2d5h30m5s'");
    }
    return result.myTimeMs;
  }

  private static class ParseResult {
    @NotNull private String myTimeText;
    private long myTimeMs;

    public ParseResult(@NotNull String timeText) {
      myTimeText = timeText;
      myTimeMs = 0;
    }

    /**
     * @param dimension
     * @param dimensionValue
     * @param timeCondition
     * @return rest of the parsed relativeTimeString
     */
    @NotNull
    private ParseResult processTimeValue(@NotNull final String dimension, final long dimensionValue) {
      int index = myTimeText.indexOf(dimension);
      if (index >= 0) {
        Long parsedNumber;
        try {
          parsedNumber = Long.valueOf(myTimeText.substring(0, index));
        } catch (NumberFormatException e) {
          throw new BadRequestException("Could not parse number from '" + myTimeText.substring(0, index) + "'");
        }
        myTimeText = myTimeText.substring(index + dimension.length());
        myTimeMs = myTimeMs + parsedNumber * dimensionValue;
      }
      return this;
    }
  }
}
