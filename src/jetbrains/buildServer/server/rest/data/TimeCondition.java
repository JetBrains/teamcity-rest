/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import java.util.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 23/11/2015
 */
public class TimeCondition implements Matcher<Date> {
  public static final String DATE_CONDITION_EQUALS = "equals";
  public static final String DATE_CONDITION_BEFORE = "before";
  public static final String DATE_CONDITION_AFTER = "after";
  static final Map<String, Condition<Date>> ourTimeConditions = new HashMap<String, Condition<Date>>();
  protected static final String DATE = "date";
  protected static final String BUILD = "build";
  protected static final String CONDITION = "condition";
  protected static final String INCLUDE_INITIAL = "includeInitial";

  static {
    ourTimeConditions.put(TimeCondition.DATE_CONDITION_EQUALS, new TimeCondition.Condition<Date>() {
      @Override
      public boolean matches(@Nullable final Date refDate, @NotNull final Date tryDate) {
        //noinspection SimplifiableConditionalExpression
        return refDate == null ? false : tryDate.equals(refDate);
      }
    });
    ourTimeConditions.put(TimeCondition.DATE_CONDITION_AFTER, new TimeCondition.Condition<Date>() {
      @Override
      public boolean matches(@Nullable final Date refDate, @NotNull final Date tryDate) {
        //noinspection SimplifiableConditionalExpression
        return refDate == null ? false : tryDate.after(refDate);
      }
    });
    ourTimeConditions.put(DATE_CONDITION_BEFORE, new TimeCondition.Condition<Date>() {
      @Override
      public boolean matches(@Nullable final Date refDate, @NotNull final Date tryDate) {
        //noinspection SimplifiableConditionalExpression
        return refDate == null ? true : tryDate.before(refDate);
      }
    });
  }


  @NotNull private final String myTimeLocator;
  @NotNull private final ValueExtractor<BuildPromotion, Date> myValueExtractor;
  @Nullable private Date myLimitingSinceDate;
  @Nullable private Date myLimitingDate;
  private Condition<Date> myCondition;

  public TimeCondition(@NotNull String timeLocator, @NotNull final ValueExtractor<BuildPromotion, Date> valueExtractor, final BuildPromotionFinder buildPromotionFinder) {
    myTimeLocator = timeLocator;
    myValueExtractor = valueExtractor;
    init(buildPromotionFinder);
  }

  private void init(final BuildPromotionFinder buildPromotionFinder) {
    final Locator timeLocator = new Locator(myTimeLocator, DATE, BUILD, CONDITION, INCLUDE_INITIAL, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    String build = null;
    String time = timeLocator.getSingleValue();
    if (time == null) {
      time = timeLocator.getSingleDimensionValue(DATE);
      if (time == null) {
        build = timeLocator.getSingleDimensionValue(BUILD);
        if (build == null) {
          throw new BadRequestException("Invalid locator: should contain '" + DATE + "' or '" + BUILD + "' dimensions");
        }
      }
    }
    @Nullable final Date parsedTime = time != null ? DataProvider.parseDate(time) : myValueExtractor.get(buildPromotionFinder.getItem(build));
    final boolean secondsPrecision = time != null; //force rounding to seconds as the passed time does not support more

    final String conditionText = timeLocator.getSingleDimensionValue(CONDITION);
    final String conditionName = conditionText == null ? DATE_CONDITION_AFTER : conditionText; //todo: should it be "equal" instead???
    final Condition<Date> definedCondition = getCondition(conditionName);
    if (definedCondition == null) {
      throw new BadRequestException("Invalid condition name '" + conditionName + "'. Supported names are: " + Arrays.toString(getAllConditions()));
    }

    Boolean includeInitial = timeLocator.getSingleDimensionValueAsBoolean(INCLUDE_INITIAL, false);
    if (includeInitial == null) {
      includeInitial = false;
    }

    timeLocator.checkLocatorFullyProcessed();

    Condition<Date> resultingCondition;
    if (!includeInitial) {
      resultingCondition = definedCondition;
    } else {
      resultingCondition = new Condition<Date>() {
        @Override
        boolean matches(@Nullable final Date refDate, @NotNull final Date tryDate) {
          final boolean nestedResult = definedCondition.matches(refDate, tryDate);
          return refDate == null ? nestedResult : nestedResult || refDate.equals(tryDate);
        }
      };
    }

    if (secondsPrecision) {
      final Condition<Date> currentCondition = resultingCondition;
      resultingCondition = new Condition<Date>() {
        @Override
        boolean matches(@Nullable final Date refDate, @NotNull final Date tryDate) {
          Calendar calendar = Calendar.getInstance();
          calendar.setTime(tryDate);
          calendar.set(Calendar.MILLISECOND, 0);

          return currentCondition.matches(refDate, calendar.getTime());
        }
      };
    }

    myCondition = resultingCondition;
    myLimitingDate = parsedTime;
    myLimitingSinceDate = DATE_CONDITION_AFTER.equals(conditionName) || DATE_CONDITION_EQUALS.equals(conditionName) ? parsedTime : null;
  }

  @Override
  public boolean matches(@NotNull final Date date) {
    return myCondition.matches(myLimitingDate, date);
  }

  @Nullable
  public Date getLimitingSinceDate() {
    return myLimitingSinceDate;
  }

  @Nullable
  private static Condition<Date> getCondition(@NotNull String name) {
    return ourTimeConditions.get(name);
  }

  @NotNull
  private static String[] getAllConditions() {
    return CollectionsUtil.toArray(ourTimeConditions.keySet(), String.class);
  }

  interface ValueExtractor<T, V> {
    @Nullable
    public V get(@NotNull T t);
  }

  abstract static class Condition<T> {
    abstract boolean matches(@Nullable final T refValue, @NotNull final T tryValue);
  }
}
