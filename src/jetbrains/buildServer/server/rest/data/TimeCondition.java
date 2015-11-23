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
public class TimeCondition {
  public static final String DATE_CONDITION_EQUALS = "equals";
  public static final String DATE_CONDITION_BEFORE = "before";
  public static final String DATE_CONDITION_AFTER = "after";
  static final Map<String, Condition<Date>> ourTimeConditions = new HashMap<String, Condition<Date>>();

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


  /**
   * @return Date if it can be used for cutting builds processing
   */
  @Nullable
  static Date processTimeCondition(@NotNull final String timeLocatorText,
                                   @NotNull final MultiCheckerFilter<BuildPromotion> result,
                                   @NotNull final ValueExtractor<BuildPromotion, Date> valueExtractor,
                                   @NotNull final BuildPromotionFinder finder) {
    final Locator timeLocator = new Locator(timeLocatorText, "date", "build", "condition", "includeInitial", Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    String build = null;
      String time = timeLocator.getSingleValue();
    if (time == null) {
      time = timeLocator.getSingleDimensionValue("date");
      if (time == null) {
        build = timeLocator.getSingleDimensionValue("build");
        if (build == null) {
          throw new BadRequestException("Invalid locator: should contain '" + "date" + "' or '" + "build" + "' dimensions");
        }
      }
    }
    @Nullable final Date parsedTime = time != null ? DataProvider.parseDate(time) : valueExtractor.get(finder.getItem(build));
    final boolean secondsPrecision = time != null; //force rounding to seconds as the passed time does not support more

    final String conditionText = timeLocator.getSingleDimensionValue("condition");
    final String conditionName = conditionText == null ? DATE_CONDITION_AFTER : conditionText; //todo: should it be "equal" instead???
    final Condition<Date> definedCondition = getCondition(conditionName);
    if (definedCondition == null) {
      throw new BadRequestException("Invalid condition name '" + conditionName + "'. Supported names are: " + Arrays.toString(getAllConditions()));
    }

    Boolean includeInitial = timeLocator.getSingleDimensionValueAsBoolean("includeInitial", false);
    if (includeInitial == null) includeInitial = false;
    final Condition<Date> resultingCondition = !includeInitial ? definedCondition : new Condition<Date>() {
      @Override
      boolean matches(@Nullable final Date refDate, @NotNull final Date tryDate) {
        final boolean nestedResult = definedCondition.matches(refDate, tryDate);
        return refDate == null ? nestedResult : nestedResult || refDate.equals(tryDate);
      }
    };

    timeLocator.checkLocatorFullyProcessed();

    result.add(new FilterConditionChecker<BuildPromotion>() {
      public boolean isIncluded(@NotNull final BuildPromotion item) {
        Date tryValue = valueExtractor.get(item);
        if (tryValue == null){
          return false; //do not include if no date present (e.g. not started build). This can be reworked to treat nulls as "future" instead of "never"
        }
        if (secondsPrecision) {
          Calendar calendar = Calendar.getInstance();
          calendar.setTime(tryValue);
          calendar.set(Calendar.MILLISECOND, 0);
          tryValue = calendar.getTime();
        }
        return resultingCondition.matches(parsedTime, tryValue);
      }
    });
    return DATE_CONDITION_AFTER.equals(conditionName) || DATE_CONDITION_EQUALS.equals(conditionName) ? parsedTime : null;
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
