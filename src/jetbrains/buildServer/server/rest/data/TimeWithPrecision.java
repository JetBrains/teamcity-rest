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

package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Constants;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.TimeService;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Instant;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.ISODateTimeFormat;

/**
 * @author Yegor.Yarko
 *         Date: 15/02/2016
 */
public class TimeWithPrecision {
  private static Logger LOG = Logger.getInstance(TimeWithPrecision.class.getName());

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
    Locator.processHelpRequest(timeString, "Date/time string in format '" + DateTimeFormat.forPattern(Constants.TIME_FORMAT).withLocale(Locale.ENGLISH).print(Instant.now()) + "'" +
                                           " or offset in format '-4w2d5h30m5s'");
    if (timeString.contains(" ")) {
      throw new BadRequestException("Invalid date/time string '" + timeString + "'. Note that '+' should be escaped in URL as '%2B'.");
    }
    boolean useSecondsPrecision = TeamCityProperties.getBoolean("rest.timeMatching.roundEntityTimeToSeconds"); //compatibility switch to mimic pre-2018.2 behavior
    if (timeString.startsWith("-")) {
      return new TimeWithPrecision(new Date(timeService.now() - TimeWithPrecision.getMsFromRelativeTime(timeString.substring("-".length()))), useSecondsPrecision);
    } else if (timeString.startsWith("+")) {
      return new TimeWithPrecision(new Date(timeService.now() + TimeWithPrecision.getMsFromRelativeTime(timeString.substring("+".length()))), useSecondsPrecision);
    }

    // try different parsers
    // the order can be important as SimpleDateFormat will parse the beginning of the string ignoring any unparsed trailing characters

    BadRequestException firstError;
    if (TeamCityProperties.getBoolean("rest.compatibilityDateParsing")) {
      try {
        return new TimeWithPrecision(new SimpleDateFormat(Constants.TIME_FORMAT, Locale.ENGLISH).parse(timeString), true);
      } catch (ParseException e) {
        firstError = new BadRequestException("Was not able to parse date '" + timeString + "' using format '" + Constants.TIME_FORMAT + "' and others." +
                                             " Supported format example: '" + new SimpleDateFormat(Constants.TIME_FORMAT, Locale.ENGLISH).format(new Date()) + "'." +
                                             " Error: " + e.toString(), e);
      }
    } else {
      try {
        return new TimeWithPrecision(DateTimeFormat.forPattern(Constants.TIME_FORMAT).withLocale(Locale.ENGLISH).parseDateTime(timeString).toDate(), useSecondsPrecision);
      } catch (Exception e) {
        firstError = new BadRequestException("Was not able to parse date '" + timeString + "' using format '" + Constants.TIME_FORMAT + "' and others." +
                                             " Supported format example: '" +
                                             DateTimeFormat.forPattern(Constants.TIME_FORMAT).withLocale(Locale.ENGLISH).withZone(DateTimeZone.getDefault()).print(Instant.now()) +
                                             "'. Error: " + e.toString(), e);
      }
    }

    try {
      return new TimeWithPrecision(ISODateTimeFormat.localTimeParser().withLocale(Locale.ENGLISH).
        parseLocalTime(timeString).toDateTime(new DateTime(timeService.now())).toDate(), false);
    } catch (Exception e) {
      //ignore
      if (LOG.isDebugEnabled()) LOG.debug("Was not able to parse date/time '" + timeString + "' using ISODateTimeFormat.localTimeParser. Error: " + e.toString(), e);
    }

    try {
      return new TimeWithPrecision(ISODateTimeFormat.dateTimeParser().withLocale(Locale.ENGLISH).parseDateTime(timeString).toDate(), false);
    } catch (Exception e) {
      //ignore
      if (LOG.isDebugEnabled()) LOG.debug("Was not able to parse date/time '" + timeString + "' using ISODateTimeFormat.dateTimeParser. Error: " + e.toString(), e);
    }

    try {
      return new TimeWithPrecision(ISODateTimeFormat.basicDateTime().withLocale(Locale.ENGLISH).parseDateTime(timeString).toDate(), false);
    } catch (Exception e) {
      //ignore
      if (LOG.isDebugEnabled()) LOG.debug("Was not able to parse date/time '" + timeString + "' using ISODateTimeFormat.basicDateTime. Error: " + e.toString(), e);
    }

    try {
      return new TimeWithPrecision(ISODateTimeFormat.basicDateTimeNoMillis().withLocale(Locale.ENGLISH).parseDateTime(timeString).toDate(), false);
    } catch (Exception e) {
      //ignore
      if (LOG.isDebugEnabled()) LOG.debug("Was not able to parse date/time '" + timeString + "' using ISODateTimeFormat.basicDateTimeNoMillis. Error: " + e.toString(), e);
    }

    try {
      DateTime dateTime = ISODateTimeFormat.localDateOptionalTimeParser().withLocale(Locale.ENGLISH).parseDateTime(timeString).withZoneRetainFields(DateTimeZone.getDefault());
      return new TimeWithPrecision(dateTime.toDate(), false);
    } catch (Exception e) {
      //ignore
      if (LOG.isDebugEnabled()) LOG.debug("Was not able to parse date/time '" + timeString + "' using ISODateTimeFormat.localDateOptionalTimeParser. Error: " + e.toString(), e);
    }

    try {
      return new TimeWithPrecision(LocalTime.parse(timeString).toDateTime(new DateTime(timeService.now()).withMillisOfDay(0)).toDate(), false);
    } catch (Exception e) {
      //ignore
      if (LOG.isDebugEnabled()) LOG.debug("Was not able to parse date/time '" + timeString + "' using LocalTime. Error: " + e.toString(), e);
    }

    try {
      return new TimeWithPrecision(DateTimeFormat.forPattern("yyyyMMdd'T'HHmmss.SSS").withLocale(Locale.ENGLISH).parseLocalDateTime(timeString).toDate(), false);
//      return new TimeWithPrecision(new SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS", Locale.ENGLISH).parse(timeString), false);
    } catch (Exception e) {
      //ignore
      if (LOG.isDebugEnabled()) LOG.debug("Was not able to parse date/time '" + timeString + "' using format '" + "yyyyMMdd'T'HHmmss.SSS" + "'. Error: " + e.toString(), e);
    }

    try {
      return new TimeWithPrecision(DateTimeFormat.forPattern("yyyyMMdd'T'HHmmss").withLocale(Locale.ENGLISH).parseLocalDateTime(timeString).toDate(), false);
//      return new TimeWithPrecision(new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.ENGLISH).parse(timeString), false);
    } catch (Exception e) {
      //ignore
      if (LOG.isDebugEnabled()) LOG.debug("Was not able to parse date/time '" + timeString + "' using format '" + "yyyyMMdd'T'HHmmss" + "'. Error: " + e.toString(), e);
    }

    StringBuilder message = new StringBuilder();
    Date exampleTime = new Date();
    // throwing original error
    message.append("Could not parse date from value '").append(timeString).append("'. Supported formats are ISO8601 or for example: ");
    message.append(new SimpleDateFormat(Constants.TIME_FORMAT, Locale.ENGLISH).format(exampleTime));
    message.append(". First reported error: ").append(firstError.getMessage());
    throw firstError;
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
    //see also jetbrains.buildServer.diagnostic.TimeParser
    ParseResult result = new ParseResult(relativeTimeString);
    result.processTimeValue("y", 365L * 24 * 60 * 60 * 1000);
    result.processTimeValue("w", 7L * 24 * 60 * 60 * 1000);
    result.processTimeValue("mo", 30L * 24 * 60 * 60 * 1000);
    result.processTimeValue("d", 24L * 60 * 60 * 1000);
    result.processTimeValue("h", 60L * 60 * 1000);
    result.processTimeValue("m", 60L * 1000);
    result.processTimeValue("s", 1000);
    result.processTimeValue("ms", 1);
    if (!result.isFullyParsed()) {
      throw new BadRequestException("Unsupported relative time for the remaining text '" + result.myTimeText + "': supported format example: '-4w2d5h30m5s'");
    }
    return result.myTimeMs;
  }

  private static class ParseResult {
    @NotNull private String myTimeText;
    private long myTimeMs;
    private int myNextUnitStartIndex = -1;
    private int myNextUnitEndIndex;

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
      if (isFullyParsed() || !dimension.equals(getNextUnit())) {
        return this;
      }

      long firstNumber = getNextNumber();
      removeNextSection();
      myTimeMs = myTimeMs + firstNumber * dimensionValue;

      return this;
    }

    private long getNextNumber() {
      if (myNextUnitStartIndex == -1) {
        parseNext();
      }
      String numberSubstring = myTimeText.substring(0, myNextUnitStartIndex);
      try {
        return Long.valueOf(numberSubstring);
      } catch (NumberFormatException e) {
        throw new BadRequestException("Could not parse number from '" + numberSubstring + "'");
      }
    }

    @NotNull
    private String getNextUnit() {
      if (myNextUnitStartIndex == -1) {
        parseNext();
      }
      return myTimeText.substring(myNextUnitStartIndex, myNextUnitEndIndex);
    }

    private void removeNextSection() {
      myTimeText = myTimeText.substring(myNextUnitEndIndex);
      myNextUnitStartIndex = -1;
      myNextUnitEndIndex = -1;
    }

    private void parseNext() {
      myNextUnitStartIndex = 0;
      try {
        while (Character.isDigit(myTimeText.charAt(myNextUnitStartIndex))) {
          myNextUnitStartIndex++;
        }
      } catch (IndexOutOfBoundsException e) {
        throw new BadRequestException("Could not parse remaining text '" + myTimeText + "': does not end with a textual unit");
      }
      if (myNextUnitStartIndex == 0) {
        throw new BadRequestException("Could not parse remaining text '" + myTimeText + "': does not start with pattern <digits><letters>");
      }
      myNextUnitEndIndex = myNextUnitStartIndex;
      try {
        while (Character.isAlphabetic(myTimeText.charAt(myNextUnitEndIndex))) {
          myNextUnitEndIndex++;
        }
      } catch (IndexOutOfBoundsException e) {
        //ignore
      }
      if (myNextUnitEndIndex == myNextUnitStartIndex) {
        throw new BadRequestException("Could not parse remaining text '" + myTimeText + "': does not start with pattern <digits><letters>");
      }
    }

    public boolean isFullyParsed() {
      return myTimeText.isEmpty();
    }
  }
}
