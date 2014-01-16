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

package jetbrains.buildServer.server.rest.model;

import com.intellij.util.containers.HashSet;
import java.util.Collection;
import java.util.Set;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 28.11.13
 */
public class Fields {
  private static final String NONE_FIELDS_PATTERN = "";
  private static final String DEFAULT_FIELDS_SHORT_PATTERN = "$short";
  private static final String DEFAULT_FIELDS_LONG_PATTERN = "$long";
  private static final String ALL_FIELDS_PATTERN = "*";
  private static final String ALL_NESTED_FIELDS_PATTERN = "**";

  public static final Fields NONE = new Fields(NONE_FIELDS_PATTERN);
  public static final Fields SHORT = new Fields(DEFAULT_FIELDS_SHORT_PATTERN);
  public static final Fields LONG = new Fields(DEFAULT_FIELDS_LONG_PATTERN);
  public static final Fields ALL = new Fields(ALL_FIELDS_PATTERN);
  public static final Fields ALL_NESTED = new Fields(ALL_NESTED_FIELDS_PATTERN);

  @NotNull private final String myFieldsSpec;
  @NotNull private final Set<String> myExcludedFields;

  private Fields(@NotNull String actualFieldsSpec, @Nullable Collection<String> excludedFields, boolean isInternal) {
    myFieldsSpec = actualFieldsSpec;
    myExcludedFields = excludedFields != null ? new HashSet<String>(excludedFields) : new HashSet<String>();
  }

  public Fields() {
    this(DEFAULT_FIELDS_SHORT_PATTERN, null, true);
  }

  public Fields (@NotNull String fieldsSpec){
    this(fieldsSpec, null, true);
  }

  public Fields (@Nullable String fieldsSpec, @NotNull String defaultFieldsSpec){
    this(fieldsSpec != null ? fieldsSpec : defaultFieldsSpec, null, true);
  }

  public Fields(@Nullable String fieldsSpec, @NotNull Fields defaultFields) {
    this(fieldsSpec != null ? fieldsSpec : defaultFields.myFieldsSpec, null, true);
  }

  public boolean isAllFieldsIncluded(){
    return ALL_FIELDS_PATTERN.equals(myFieldsSpec) || ALL_NESTED_FIELDS_PATTERN.equals(myFieldsSpec);
  }

  public boolean isShort() {
    return DEFAULT_FIELDS_SHORT_PATTERN.equals(myFieldsSpec);
  }

  public boolean isLong() {
    return DEFAULT_FIELDS_LONG_PATTERN.equals(myFieldsSpec);
  }

  public boolean isNone() {
    return NONE_FIELDS_PATTERN.equals(myFieldsSpec);
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
  @Contract("_, !null, !null -> !null")
  public Boolean isIncluded(@NotNull final String fieldName, @Nullable final Boolean defaultForShort, @Nullable final Boolean defaultForLong) {
    if (isNone()){
      return false;
    }
    if (myExcludedFields.contains(fieldName)) {
      return false;
    }

    if (isAllFieldsIncluded()){
      return true;
    }
    if (isShort()) {
      return defaultForShort;
    }
    if (isLong()) {
      return defaultForLong;
    }

    return myFieldsSpec.contains(fieldName); //todo: implement! This is a hack!
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
    if (isNone()) {
      return NONE;
//      throw new OperationException("Should never get nested field for NONE fileds filter. Querying for nested field '" + nestedFieldName + "'. Excluded fields: " + myExcludedFields);
    }

    if (myExcludedFields.contains(nestedFieldName)) {
      return NONE;
    }

    final HashSet<String> excludedFields = new HashSet<String>(myExcludedFields);
    excludedFields.add(nestedFieldName);

    if (ALL_FIELDS_PATTERN.equals(myFieldsSpec)) {
      return new Fields(DEFAULT_FIELDS_SHORT_PATTERN, excludedFields, true);
    }

    if (ALL_NESTED_FIELDS_PATTERN.equals(myFieldsSpec)) {
      return new Fields(ALL_NESTED_FIELDS_PATTERN, excludedFields, true);
    }

    if (isShort()) {
      return new Fields(defaultForShort.myFieldsSpec, excludedFields, true);
    }
    if (isLong()) {
      return new Fields(defaultForLong.myFieldsSpec, excludedFields, true);
    }

    throw new BadRequestException("Sorry, getting nested fields for non-default fields is not implemented yet");
//    return new Fields(DEFAULT_FIELDS_SHORT_PATTERN, excludedFields, true); //todo: implement this.
  }
}
