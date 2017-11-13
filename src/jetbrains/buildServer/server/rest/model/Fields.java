/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 28.11.13
 */
public class Fields {
  private static final String NONE_FIELDS_PATTERN = "-";
  private static final String DEFAULT_FIELDS_SHORT_PATTERN = "";
  private static final String DEFAULT_FIELDS_SHORT_PATTERN_ALTERNATIVE = "$short";
  private static final String DEFAULT_FIELDS_LONG_PATTERN = "$long";
  private static final String ALL_FIELDS_PATTERN = "*";
  private static final String ALL_NESTED_FIELDS_PATTERN = "**";
  private static final String OPTIONAL_FIELDS_PATTERN = "$optional";

  private static final String LOCATOR_CUSTOM_NAME = "$locator";

  public static final Fields NONE = new Fields(NONE_FIELDS_PATTERN, null, null, true); // no fields at all
  public static final Fields SHORT = new Fields(DEFAULT_FIELDS_SHORT_PATTERN, null, null, true); // short (reference) form. Uses short or none form for the fields.
  public static final Fields ALL = new Fields(ALL_FIELDS_PATTERN, null, null, true); // all fields are present and are in the short form
  public static final Fields LONG = new Fields(DEFAULT_FIELDS_LONG_PATTERN, null, null, true);
    // long form. Uses long, short or none form for the fields. Generally fields with default values are not included.
  public static final Fields ALL_NESTED = new Fields(ALL_NESTED_FIELDS_PATTERN, null, null, true); // maximum, all fields are included in the same maximum form

  @NotNull private final String myFieldsSpec;
  private Locator myFieldsSpecLocator;
  @NotNull private final Map<String, Fields> myRestrictedFields;

  @Nullable
  private Context myContext = null;

  private Fields(@NotNull String actualFieldsSpec, @Nullable Map<String, Fields> restrictedFields, @Nullable Context context, boolean isInternal) {
    myFieldsSpec = actualFieldsSpec;
    myRestrictedFields = restrictedFields != null ? new HashMap<String, Fields>(restrictedFields) : new HashMap<String, Fields>();
    myContext = context;
  }

  public Fields (@Nullable String fieldsSpec){
    this(fieldsSpec, getDefaultFields());
  }

  public Fields(@Nullable String fieldsSpec, @NotNull Fields defaultFields) {
    this(fieldsSpec != null ? fieldsSpec : defaultFields.myFieldsSpec, null, null, true);
  }

  private static Fields getDefaultFields() {
    final String defaultFieldsProperty = TeamCityProperties.getPropertyOrNull("rest.beans.fields.default");
    if (defaultFieldsProperty == null){
      return LONG;
    }else{
      return new Fields(defaultFieldsProperty, null, null, true);
    }
  }

  public boolean isMoreThenShort() {
    return !isShort() && !isNone();
  }

  public boolean isShort() {
    return DEFAULT_FIELDS_SHORT_PATTERN.equals(myFieldsSpec) || getCustomDimension(DEFAULT_FIELDS_SHORT_PATTERN_ALTERNATIVE) != null;
  }

  public boolean isAll() {
    return getCustomDimension(ALL_FIELDS_PATTERN) != null;
  }

  public boolean isLong() {
    return getCustomDimension(DEFAULT_FIELDS_LONG_PATTERN) != null;
  }

  public boolean isAllNested() {
    return getCustomDimension(ALL_NESTED_FIELDS_PATTERN) != null;
  }

  public boolean isNone() {
    return getCustomDimension(NONE_FIELDS_PATTERN) != null;
  }

  /**
   *
   * @param fieldName
   * @return null if the defaults should be used
   */
  @Nullable
  public Boolean isIncluded(@NotNull final String fieldName){
    return isIncluded(fieldName, null, null);
  }

  @Nullable
  public Boolean isIncluded(@NotNull final String fieldName, @Nullable final Boolean defaultForShort) {
    return isIncluded(fieldName, defaultForShort, null);
  }

  @Nullable
  public Boolean isIncludedOr(@NotNull final String[] fieldNames, @Nullable final Boolean defaultForShort, @Nullable final Boolean defaultForLong) {
    Boolean result = false;
    for (String fieldName : fieldNames) {
      final Boolean included = isIncluded(fieldName, defaultForShort, defaultForLong);
      if (included != null) {
        if (included) {
          return true;
        }
      } else {
        result = null;
      }
    }
    return result;
  }

  @Nullable
  @Contract("_, !null, !null -> !null")
  public Boolean isIncluded(@NotNull final String fieldName, @Nullable final Boolean defaultForShort, @Nullable final Boolean defaultForLong) {
    return isIncluded(fieldName, null, defaultForShort, defaultForLong);
  }

  @Nullable
  @Contract("_, _, !null, !null -> !null")
  public Boolean isIncluded(@NotNull final String fieldName,  @Nullable final Boolean isCashed, @Nullable final Boolean defaultForShort, @Nullable final Boolean defaultForLong) {
    final String fieldSpec = getCustomDimension(fieldName);
    if (fieldSpec != null) {
      if (NONE_FIELDS_PATTERN.equals(fieldSpec)) {
        return false;
      }
      if (OPTIONAL_FIELDS_PATTERN.equals(fieldSpec) || new Fields(fieldSpec).getCustomDimension(OPTIONAL_FIELDS_PATTERN) != null) {
        if (isCashed == null && TeamCityProperties.getBoolean("rest.beans.fields.optional.errorIfUsedForNotSupportedField")) {
          throw new  BadRequestException("Special fields pattern \"" + OPTIONAL_FIELDS_PATTERN + "\" is not supported for field \"" + fieldName + "\"");
        }
        return isCashed != null && isCashed;
      }
      return true;
    }

    if (isNone()){
      return false;
    }
    final Fields restrictedFields = myRestrictedFields.get(fieldName);
    if (restrictedFields != null) {
      if (!restrictedFields.isIncluded(fieldName, true, true)) {
        return false;
      }
    }

    if (isAllNested() || isAll()) {
      return true;
    }
    if (isShort()) {
      return defaultForShort;
    }
    if (isLong()) {
      return defaultForLong;
    }

    return false;
  }

  @NotNull
  public Fields getNestedField(@NotNull final String nestedFieldName) {
    return getNestedField(nestedFieldName, NONE, SHORT);
  }

  /**
   * Returnes fields for the nested field 'nestedFieldName' defaulting to 'defaultForShort' and 'defaultForLong' for corresponding presentations.
   * Excludes stored in 'default*Presentation' paramters are ignored.
   * @param nestedFieldName
   * @param defaultForShort - default to use if the current Fields is short presentation
   * @param defaultForLong - default to use if the current Fields is long presentation
   * @return
   */
  @NotNull
  public Fields getNestedField(@NotNull final String nestedFieldName, @NotNull final Fields defaultForShort, @NotNull final Fields defaultForLong) {
    final Boolean included = isIncluded(nestedFieldName);
    if (included != null && !included) {
      return NONE;
    }

    Fields restrictedField = myRestrictedFields.get(nestedFieldName);
    if (restrictedField == null) {
      restrictedField = ALL_NESTED;
    }

    final Map<String, Fields> newRestrictedFields = new HashMap<String, Fields>(myRestrictedFields);
    newRestrictedFields.put(nestedFieldName, SHORT);

    final String fieldSpec = getCustomDimension(nestedFieldName);
    if(fieldSpec != null){
      return new Fields(fieldSpec, newRestrictedFields, myContext, true);
    }

    if (isAllNested()) {
      return new Fields(minPattern(restrictedField.myFieldsSpec, ALL_NESTED_FIELDS_PATTERN), newRestrictedFields, myContext, true);
    }

    if (isLong()) {
      return new Fields(minPattern(restrictedField.myFieldsSpec, defaultForLong.myFieldsSpec), newRestrictedFields, myContext, true);
    }

    if (isAll()) {
      return new Fields(minPattern(restrictedField.myFieldsSpec, DEFAULT_FIELDS_SHORT_PATTERN), newRestrictedFields, myContext, true);
    }

    if (isShort()) {
      newRestrictedFields.put(nestedFieldName, NONE);
      return new Fields(minPattern(restrictedField.myFieldsSpec, defaultForShort.myFieldsSpec), newRestrictedFields, myContext, true);
    }

    return new Fields(NONE_FIELDS_PATTERN, newRestrictedFields, myContext, true);
  }

  @Nullable
  public String getCustomDimension(@NotNull final String fieldName) {
    final Locator parsedCustomFields = getParsedCustomFields();
    return parsedCustomFields == null ? null : parsedCustomFields.getSingleDimensionValue(fieldName);
  }

  @Nullable
  public String getLocator() {
    return  doContextResolve(getCustomDimension(LOCATOR_CUSTOM_NAME));
  }

  @Nullable
  private String doContextResolve(@Nullable final String locator) {
    if (locator == null) return null;
    if (myContext == null) return locator;
    return myContext.process(locator);
  }

  private static String minPattern(final String a, final String b) {
    if (ALL_NESTED_FIELDS_PATTERN.equals(a)) {
      return b;
    }
    if (DEFAULT_FIELDS_LONG_PATTERN.equals(a)) {
      if (ALL_NESTED_FIELDS_PATTERN.equals(b)) return a;
      return b;
    }
    if (ALL_FIELDS_PATTERN.equals(a)) {
      if (ALL_NESTED_FIELDS_PATTERN.equals(b)) return a;
      if (DEFAULT_FIELDS_LONG_PATTERN.equals(b)) return a;
      return b;
    }
    if (DEFAULT_FIELDS_SHORT_PATTERN.equals(a) || DEFAULT_FIELDS_SHORT_PATTERN_ALTERNATIVE.equals(a)) {
      if (ALL_NESTED_FIELDS_PATTERN.equals(b)) return a;
      if (DEFAULT_FIELDS_LONG_PATTERN.equals(b)) return a;
      if (ALL_FIELDS_PATTERN.equals(b)) return a;
      return b;
    }
    if (NONE_FIELDS_PATTERN.equals(a)) return NONE_FIELDS_PATTERN;

    throw new BadRequestException("Comparing non standard Fields is not supported: a='" +a + "', b='" + b + "'");
  }

  @NotNull
  public Fields increaseRestrictedField(@NotNull final String fieldName, @NotNull Fields newRestriction) {
    final Fields currentRestriction = myRestrictedFields.get(fieldName);
    if (minPattern(currentRestriction.myFieldsSpec, newRestriction.myFieldsSpec).equals(currentRestriction.myFieldsSpec)) {
      return resetRestrictedField(fieldName, newRestriction);
    }
    return currentRestriction;
  }

  @NotNull
  public Fields resetRestrictedField(@NotNull final String fieldName, @NotNull Fields newRestriction) {
    final Map<String, Fields> newRestrictedFields = new HashMap<String, Fields>(myRestrictedFields);
    newRestrictedFields.put(fieldName, newRestriction);
    return new Fields(myFieldsSpec, newRestrictedFields, myContext, true);
  }

  @NotNull
  public Fields removeRestrictedField(@NotNull final String fieldName) {
    final Map<String, Fields> newRestrictedFields = new HashMap<String, Fields>(myRestrictedFields);
    newRestrictedFields.remove(fieldName);
    return new Fields(myFieldsSpec, newRestrictedFields, myContext, true);
  }

  @Nullable
  Locator getParsedCustomFields() {
    if (myFieldsSpecLocator == null && !StringUtil.isEmpty(myFieldsSpec)) {
      try {
        myFieldsSpecLocator = new Locator(myFieldsSpec, true,
                                          NONE_FIELDS_PATTERN, DEFAULT_FIELDS_SHORT_PATTERN_ALTERNATIVE, DEFAULT_FIELDS_LONG_PATTERN, ALL_FIELDS_PATTERN, ALL_NESTED_FIELDS_PATTERN,
                                          LOCATOR_CUSTOM_NAME, OPTIONAL_FIELDS_PATTERN);
        myFieldsSpecLocator.addHiddenDimensions(OPTIONAL_FIELDS_PATTERN);
      } catch (LocatorProcessException e) {
        throw new LocatorProcessException("Error parsing fields specification: " + e.getMessage(), e);
      }
    }
    return myFieldsSpecLocator;
  }

  @NotNull
  public String getFieldsSpec() {
    return myFieldsSpec;
  }

  @Override
  public String toString() {
    return getFieldsSpec();
  }

  public void setContext(@NotNull final BuildPromotion buildPromotion) {
    if (myContext == null) myContext = new Context();
    myContext.buildPromotion = buildPromotion;
  }

  private static class Context {
    @Nullable BuildPromotion buildPromotion;

    @NotNull
    public String process(@NotNull final String locator) {
      if (buildPromotion == null) return locator;
      return StringUtil.replace(locator, "$context.build.id", String.valueOf(buildPromotion.getId()));
    }
  }
}
