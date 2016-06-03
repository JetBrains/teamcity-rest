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

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class that support parsing of "locators".
 * Locator is a string with single value or several named "dimensions".
 * text enclosed into matching parentheses "()" is excluded from parsing and the parentheses are omitted
 * "$any" text (when not enclosed into parentheses) means "no value" to force dimension use, but treat the value as "null"
 * Example:
 * <tt>31</tt> - locator wth single value "31"
 * <tt>name:Frodo</tt> - locator wth single dimension "name" which has value "Frodo"
 * <tt>name:Frodo,age:14</tt> - locator with two dimensions "name" which has value "Frodo" and "age", which has value "14"
 * <tt>text:(Freaking symbols:,name)</tt> - locator with single dimension "text" which has value "Freaking symbols:,name"
 * <p/>
 * Dimension name contain only alpha-numeric symbols in usual mode. Extended mode allows in addition to use any non-empty known dimensions name which contain no ":", ",", "(", symbols)
 *<p/> Dimension value should not contain symbol "," if not enclosed in "(" and ")" or
 * should contain properly paired parentheses ("(" and ")") if enclosed in "(" and ")"
 *
 * Usual mode supports single value locators. In extended mode, those will result in single dimension with value as name and empty value.
 *
 * @author Yegor.Yarko
 *         Date: 13.08.2010
 */
public class Locator {
  private static final Logger LOG = Logger.getInstance(Locator.class.getName());
  private static final String DIMENSION_NAME_VALUE_DELIMITER = ":";
  private static final String DIMENSIONS_DELIMITER = ",";
  private static final String DIMENSION_COMPLEX_VALUE_START_DELIMITER = "(";
  private static final String DIMENSION_COMPLEX_VALUE_END_DELIMITER = ")";
  public static final String LOCATOR_SINGLE_VALUE_UNUSED_NAME = "single value";
  protected static final String ANY_LITERAL = "$any";

  private final String myRawValue;
  private final boolean myExtendedMode;
  private boolean modified = false;
  private final LinkedHashMap<String, List<String>> myDimensions;
  private final String mySingleValue;

  @NotNull private final Set<String> myUsedDimensions = new HashSet<String>();
  @Nullable private String[] mySupportedDimensions;
  @NotNull private final Collection<String> myIgnoreUnusedDimensions = new HashSet<String>();
  @NotNull private final Collection<String> myHddenSupportedDimensions = new HashSet<String>();
  private DescriptionProvider myDescriptionProvider = null;

  public Locator(@Nullable final String locator) throws LocatorProcessException {
    this(locator, null);
  }

  /**
   * Creates a new locator as a copy of the passed one preserving the entire state.
   *
   * @param locator
   */
  public Locator(@NotNull final Locator locator) {
    myRawValue = locator.myRawValue;
    modified = locator.modified;
    myDimensions = new LinkedHashMap<String, List<String>>();
    for (Map.Entry<String, List<String>> entry : locator.myDimensions.entrySet()) {
      myDimensions.put(entry.getKey(), new ArrayList<String>(entry.getValue()));
    }

    mySingleValue = locator.mySingleValue;
    myUsedDimensions.addAll(locator.myUsedDimensions);
    mySupportedDimensions = locator.mySupportedDimensions != null ? locator.mySupportedDimensions.clone() : null;
    myIgnoreUnusedDimensions.addAll(locator.myIgnoreUnusedDimensions);
    myHddenSupportedDimensions.addAll(locator.myHddenSupportedDimensions);
    myExtendedMode = locator.myExtendedMode;
  }

  /**
   * Creates usual mode locator
   *
   * @param locator
   * @param supportedDimensions dimensions supported in this locator, used in {@link #checkLocatorFullyProcessed()}
   * @throws LocatorProcessException
   */
  public Locator(@Nullable final String locator, final String... supportedDimensions) throws LocatorProcessException {
    this(locator, false, supportedDimensions);
  }

  /**
   * Creates usual or extended mode locator
   *
   * @param locator
   * @param supportedDimensions dimensions supported in this locator, used in {@link #checkLocatorFullyProcessed()}
   * @throws LocatorProcessException
   */
  public Locator(@Nullable final String locator, final boolean extendedMode, final String... supportedDimensions) throws LocatorProcessException {
    myRawValue = locator;
    myExtendedMode = extendedMode;
    if (StringUtil.isEmpty(locator)) {
      throw new LocatorProcessException("Invalid locator. Cannot be empty.");
    }
    mySupportedDimensions = supportedDimensions;
    if (isEscapedValue(locator)) {
      mySingleValue = locator.substring(DIMENSION_COMPLEX_VALUE_START_DELIMITER.length(), locator.length() - DIMENSION_COMPLEX_VALUE_END_DELIMITER.length());
      myDimensions = new LinkedHashMap<String, List<String>>();
    } else if (!extendedMode && !hasDimensions(locator)) {
      mySingleValue = locator;
      myDimensions = new LinkedHashMap<String, List<String>>();
    } else {
      mySingleValue = null;
      myDimensions = parse(locator);
    }
  }

  private boolean isEscapedValue(final @NotNull String locator) {
    return locator.length() > (DIMENSION_COMPLEX_VALUE_START_DELIMITER.length() + DIMENSION_COMPLEX_VALUE_END_DELIMITER.length()) &&
           locator.startsWith(DIMENSION_COMPLEX_VALUE_START_DELIMITER) && locator.endsWith(DIMENSION_COMPLEX_VALUE_END_DELIMITER);
  }

  private boolean hasDimensions(final @NotNull String locatorText) {
    if (locatorText.contains(DIMENSION_NAME_VALUE_DELIMITER)) {
      return true;
    }
    //noinspection RedundantIfStatement
    if (locatorText.contains(DIMENSION_COMPLEX_VALUE_START_DELIMITER) && locatorText.contains(DIMENSION_COMPLEX_VALUE_END_DELIMITER)) {
      return true;
    }
    return false;
  }

  /**
   * Creates an empty locator with dimensions.
   */
  private Locator() {
    myRawValue = "";
    mySingleValue = null;
    myDimensions = new LinkedHashMap<String, List<String>>();
    mySupportedDimensions = null;
    myExtendedMode = false;
  }

  /**
   * The resultant locator will have all the dimensions from "defualts" locator set unless already defined.
   * If "locator" text is empty, "defaults" locator will be used
   *
   * @param locator
   * @param defaults
   * @param supportedDimensions
   * @return
   */
  public static Locator createLocator(@Nullable final String locator, @Nullable final Locator defaults, final String[] supportedDimensions) {
    Locator result;
    if (locator != null || defaults == null) {
      result = new Locator(locator, supportedDimensions);
    } else {
      result = Locator.createEmptyLocator(supportedDimensions);
    }

    if (defaults != null && !result.isSingleValue()) {
      for (String dimensionName : defaults.myDimensions.keySet()) {
        final String value = defaults.getSingleDimensionValue(dimensionName);
        if (value != null) {
          result.setDimensionIfNotPresent(dimensionName, value);
          if (defaults.myHddenSupportedDimensions.contains(dimensionName)){
            result.myHddenSupportedDimensions.add(dimensionName);
          }
          if (defaults.myIgnoreUnusedDimensions.contains(dimensionName)){
            result.myIgnoreUnusedDimensions.add(dimensionName);
          }
        }
      }
    }

    return result;
  }

  public static Locator createEmptyLocator(final String... supportedDimensions) {
    final Locator result = new Locator();
    result.mySupportedDimensions = supportedDimensions;
    return result;
  }

  public boolean isEmpty() {
    return mySingleValue == null && myDimensions.isEmpty();
  }

  public void addIgnoreUnusedDimensions(final String... ignoreUnusedDimensions) {
    myIgnoreUnusedDimensions.addAll(Arrays.asList(ignoreUnusedDimensions));
  }

  /**
   * Sets dimensions which will not be reported by checkLocatorFullyProcessed method as used but not declared
   *
   * @param hiddenDimensions
   */
  public void addHiddenDimensions(final String... hiddenDimensions) {
    myHddenSupportedDimensions.addAll(Arrays.asList(hiddenDimensions));
  }

  @NotNull
  private LinkedHashMap<String, List<String>> parse(@NotNull final String locator) {
    LinkedHashMap<String, List<String>> result = new LinkedHashMap<String, List<String>>();
    String currentDimensionName;
    String currentDimensionValue;
    int parsedIndex = 0;
    while (parsedIndex < locator.length()) {
      //expecting name start at parsedIndex
      int nameEnd = locator.length();

      String nextDelimeter = null;
      int currentIndex = parsedIndex;
      while (currentIndex < locator.length()) {
        if (locator.startsWith(DIMENSIONS_DELIMITER, currentIndex)) {
          nextDelimeter = DIMENSIONS_DELIMITER;
          nameEnd = currentIndex;
          break;
        }
        if (locator.startsWith(DIMENSION_COMPLEX_VALUE_START_DELIMITER, currentIndex)) {
          nextDelimeter = DIMENSION_COMPLEX_VALUE_START_DELIMITER;
          nameEnd = currentIndex;
          break;
        }
        if (locator.startsWith(DIMENSION_NAME_VALUE_DELIMITER, currentIndex)) {
          nextDelimeter = DIMENSION_NAME_VALUE_DELIMITER;
          nameEnd = currentIndex;
          break;
        }
        currentIndex++;
      }

      if (nameEnd == parsedIndex) {
        throw new LocatorProcessException(locator, parsedIndex, "Could not find dimension name, found '" + nextDelimeter + "' instead");
      }

      currentDimensionName = locator.substring(parsedIndex, nameEnd);
      if (!isValidName(currentDimensionName)) {
        throw new LocatorProcessException(locator, parsedIndex, "Invalid dimension name :'" + currentDimensionName +
                                                                "'. Should contain only alpha-numeric symbols or be known one: " + Arrays.asList(mySupportedDimensions));
      }
      currentDimensionValue = "";
      parsedIndex = nameEnd;
      if (nextDelimeter != null) {
        if (DIMENSIONS_DELIMITER.equals(nextDelimeter)) {
          parsedIndex = nameEnd + nextDelimeter.length();
        } else {
          if (DIMENSION_NAME_VALUE_DELIMITER.equals(nextDelimeter)) {
            parsedIndex = nameEnd + nextDelimeter.length();
          }

          if (DIMENSION_COMPLEX_VALUE_START_DELIMITER.equals(nextDelimeter)) {
            parsedIndex = nameEnd;
          }

          //here begins the value at parsedIndex
          final String valueAndRest = locator.substring(parsedIndex);
          if (valueAndRest.startsWith(DIMENSION_COMPLEX_VALUE_START_DELIMITER)) {
            //complex value detected
            final int complexValueEnd = findMatchingEndDelimeterIndex(valueAndRest);
            if (complexValueEnd == -1) {
              throw new LocatorProcessException(locator, parsedIndex + DIMENSION_COMPLEX_VALUE_START_DELIMITER.length(),
                                                "Could not find matching '" + DIMENSION_COMPLEX_VALUE_END_DELIMITER + "'");
            }
            currentDimensionValue = valueAndRest.substring(DIMENSION_COMPLEX_VALUE_START_DELIMITER.length(), complexValueEnd);
            parsedIndex = parsedIndex + complexValueEnd + DIMENSION_COMPLEX_VALUE_END_DELIMITER.length();
            if (parsedIndex != locator.length()) {
              if (!locator.startsWith(DIMENSIONS_DELIMITER, parsedIndex)) {
                throw new LocatorProcessException(locator, parsedIndex, "No dimensions delimiter '" + DIMENSIONS_DELIMITER + "' after complex value");
              } else {
                parsedIndex += DIMENSIONS_DELIMITER.length();
              }
            }
          } else {
            int valueEnd = valueAndRest.indexOf(DIMENSIONS_DELIMITER);
            if (valueEnd == -1) {
              currentDimensionValue = valueAndRest;
              parsedIndex = locator.length();
            } else {
              currentDimensionValue = valueAndRest.substring(0, valueEnd);
              parsedIndex = parsedIndex + valueEnd + DIMENSIONS_DELIMITER.length();
            }
            if (ANY_LITERAL.equals(currentDimensionValue)) {
              currentDimensionValue = ANY_LITERAL; //this was not a complex value, so setting exactly the same string to be able to determine this on retrieving
            }
          }
        }
      }
      final List<String> currentList = result.get(currentDimensionName);
      final List<String> newList = currentList == null ? new ArrayList<String>(1) : new ArrayList<String>(currentList);
      newList.add(currentDimensionValue);
      result.put(currentDimensionName, newList);
    }

    return result;
  }

  private static int findMatchingEndDelimeterIndex(final String valueAndRest) {
    int pos = DIMENSION_COMPLEX_VALUE_START_DELIMITER.length();
    int nesting = 1;
    while (nesting != 0) {
      final int endDelimeterPosition = valueAndRest.indexOf(DIMENSION_COMPLEX_VALUE_END_DELIMITER, pos);
      final int startDelimeterPosition = valueAndRest.indexOf(DIMENSION_COMPLEX_VALUE_START_DELIMITER, pos);
      if (endDelimeterPosition == -1) {
        return -1;
      }
      if (startDelimeterPosition == -1 || endDelimeterPosition < startDelimeterPosition) {
        nesting--;
        pos = endDelimeterPosition + DIMENSION_COMPLEX_VALUE_END_DELIMITER.length();
      } else if (endDelimeterPosition > startDelimeterPosition) {
        nesting++;
        pos = startDelimeterPosition + DIMENSION_COMPLEX_VALUE_START_DELIMITER.length();
      }
    }
    return pos - DIMENSION_COMPLEX_VALUE_END_DELIMITER.length();
  }

  private boolean isValidName(final String name) {
    if ((mySupportedDimensions == null || !Arrays.asList(mySupportedDimensions).contains(name)) && !myHddenSupportedDimensions.contains(name)) {
      for (int i = 0; i < name.length(); i++) {
        if (!Character.isLetter(name.charAt(i)) && !Character.isDigit(name.charAt(i)) && !(name.charAt(i) == '-' && myExtendedMode)) return false;
      }
    }
    return true;
  }

  //todo: use this whenever possible
  public void checkLocatorFullyProcessed() {
    String reportKindString = TeamCityProperties.getProperty("rest.report.unused.locator", "error");
    if (!TeamCityProperties.getBooleanOrTrue("rest.report.locator.errors")) {
      reportKindString = "off";
    }
    if (!reportKindString.equals("off")) {
      if (reportKindString.contains("reportKnownButNotReportedDimensions")) {
        reportKnownButNotReportedDimensions();
      }
      final Set<String> unusedDimensions = getUnusedDimensions();
      if (unusedDimensions.size() > 0) {
        Set<String> ignoredDimensions = mySupportedDimensions == null ? Collections.<String>emptySet() :
                                        CollectionsUtil.intersect(unusedDimensions, CollectionsUtil.join(Arrays.asList(mySupportedDimensions), myHddenSupportedDimensions));
        Set<String> unknownDimensions = CollectionsUtil.minus(unusedDimensions, ignoredDimensions);
        StringBuilder message = new StringBuilder();
        if (unusedDimensions.size() > 1) {
          message.append("Locator dimensions ");
          if (!ignoredDimensions.isEmpty()) {
            message.append(ignoredDimensions).append(" ").append(ignoredDimensions.size() == 1 ? "is" : "are").append(" ignored");
          }
          if (!unknownDimensions.isEmpty()) {
            if (!ignoredDimensions.isEmpty()) {
              message.append(" and ");
            }
            message.append(unknownDimensions).append(" ").append(unknownDimensions.size() == 1 ? "is" : "are").append(" unknown");
          }
          message.append(".");
        } else {
          if (!unusedDimensions.contains(LOCATOR_SINGLE_VALUE_UNUSED_NAME)) {
            if (mySupportedDimensions != null) {
              message.append("Locator dimension ").append(unusedDimensions).append(" is ");
              if (!ignoredDimensions.isEmpty()) {
                message.append("known but was ignored during processing. Try omitting the dimension.");
              } else {
                message.append("unknown.");
              }
            } else {
              message.append("Locator dimension ").append(unusedDimensions).append(" is ignored or unknown.");
            }
          } else {
            message.append("Single value locator '").append(mySingleValue).append("' was ignored.");
          }
        }
        if (mySupportedDimensions != null && mySupportedDimensions.length > 0) message.append(" ").append(getLocatorDescription(reportKindString.contains("includeHidden")));
        if (reportKindString.contains("log")) {
          if (reportKindString.contains("log-warn")) {
            LOG.warn(message.toString());
          } else {
            LOG.debug(message.toString());
          }
        }
        if (reportKindString.contains("error")) {
          throw new LocatorProcessException(message.toString());
        }
      }
    }
  }

  protected interface DescriptionProvider{
    @NotNull String get(boolean includeHidden);
  }

  public void setDescriptionProvider(final DescriptionProvider descriptionProvider) {
    myDescriptionProvider = descriptionProvider;
  }

  @NotNull
  private String getLocatorDescription(boolean includeHidden) {
    if (myDescriptionProvider == null){
      StringBuilder result = new StringBuilder();
      result.append("Supported dimensions are: ").append(Arrays.toString(mySupportedDimensions)).append(".");
      if (includeHidden && !myHddenSupportedDimensions.isEmpty()){
        result.append(" Hidden supported are: ").append(Arrays.toString(myHddenSupportedDimensions.toArray()));
      }
      return result.toString();
    }
    return myDescriptionProvider.get(includeHidden);
  }

  private void reportKnownButNotReportedDimensions() {
    final Set<String> usedDimensions = new HashSet<String>(myUsedDimensions);
    if (mySupportedDimensions != null) usedDimensions.removeAll(Arrays.asList(mySupportedDimensions));
    usedDimensions.removeAll(myHddenSupportedDimensions);
    if (usedDimensions.size() > 0) {
      //found used dimensions which are not declared as used.

      //noinspection ThrowableInstanceNeverThrown
      final Exception exception = new Exception("Helper exception to get stacktrace");
      LOG.info("Locator " + StringUtil.pluralize("dimension", usedDimensions.size()) + " " + usedDimensions + (usedDimensions.size() > 1 ? " are" : " is") +
               " actually used but not declared as supported. Declared locator details: " + getLocatorDescription(true), exception);
    }
  }

  public boolean isSingleValue() {
    return mySingleValue != null;
  }

  /**
   * @return locator's not-null value if it is single-value locator, 'null' otherwise
   */
  @Nullable
  public String getSingleValue() {
    myUsedDimensions.add(LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    return lookupSingleValue();
  }

  @Nullable
  public String lookupSingleValue() {
    return mySingleValue;
  }

  @Nullable
  public Long getSingleValueAsLong() {
    final String singleValue = getSingleValue();
    if (singleValue == null) {
      return null;
    }
    try {
      return Long.parseLong(singleValue);
    } catch (NumberFormatException e) {
      throw new LocatorProcessException("Invalid single value: '" + singleValue + "'. Should be a number.");
    }
  }

  @Nullable
  public Long getSingleDimensionValueAsLong(@NotNull final String dimensionName) {
    return getSingleDimensionValueAsLong(dimensionName, null);
  }

  @Nullable
  @Contract("_, !null -> !null")
  public Long getSingleDimensionValueAsLong(@NotNull final String dimensionName, @Nullable Long defaultValue) {
    final String value = getSingleDimensionValue(dimensionName);
    if (value == null) {
      return defaultValue;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new LocatorProcessException("Invalid value of dimension '" + dimensionName + "': '" + value + "'. Should be a number.");
    }
  }

  /**
   *
   * @return "null" if not defined or set to "any"
   */
  @Nullable
  public Boolean getSingleDimensionValueAsBoolean(@NotNull final String dimensionName) {
    try {
      return getBooleanByValue(getSingleDimensionValue(dimensionName));
    } catch (LocatorProcessException e) {
      throw new LocatorProcessException("Invalid value of dimension '" + dimensionName + "': " + e.getMessage(), e);
    }
  }

  /**
   * Same as getSingleDimensionValueAsBoolean but does not mark the dimension as used
   */
  @Nullable
  public Boolean lookupSingleDimensionValueAsBoolean(@NotNull final String dimensionName) {
    try {
      return getBooleanByValue(lookupSingleDimensionValue(dimensionName));
    } catch (LocatorProcessException e) {
      throw new LocatorProcessException("Invalid value of dimension '" + dimensionName + "': " + e.getMessage(), e);
    }
  }

  public static Boolean getBooleanByValue(@Nullable final String value) {
    if (value == null || "all".equalsIgnoreCase(value) || BOOLEAN_ANY.equalsIgnoreCase(value) || isAny(value)) {
      return null;
    }
    final Boolean result = getStrictBoolean(value);
    if (result != null) return result;
    throw new LocatorProcessException("Invalid boolean value '" + value + "'. Should be 'true', 'false' or 'any'.");
  }

  public static final String BOOLEAN_TRUE = "true";
  public static final String BOOLEAN_FALSE = "false";
  public static final String BOOLEAN_ANY = "any";
  /**
   * "any" is not supported
   * @return "null" if cannot be parsed as boolean
   */
  @Nullable
  public static Boolean getStrictBoolean(final @Nullable String value) {
    if (BOOLEAN_TRUE.equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "in".equalsIgnoreCase(value)) {
      return true;
    }
    if (BOOLEAN_FALSE.equalsIgnoreCase(value) || "off".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value) || "out".equalsIgnoreCase(value)) {
      return false;
    }
    return null;
  }

  /**
   * @param dimensionName name of the dimension
   * @param defaultValue  default value to use if no dimension with the name is found
   * @return value specified by the dimension with name "dimensionName" (one of the possible values can be "null") or
   * "defaultValue" if such dimension is not present
   */
  @Nullable
  public Boolean getSingleDimensionValueAsBoolean(@NotNull final String dimensionName, @Nullable Boolean defaultValue) {
    final String value = getSingleDimensionValue(dimensionName);
    if (value == null) {
      return defaultValue;
    }
    return getSingleDimensionValueAsBoolean(dimensionName);
  }

  /**
   * Extracts the single dimension value from dimensions.
   *
   * @param dimensionName the name of the dimension to extract value.   @return 'null' if no such dimension is found, value of the dimension otherwise.
   * @throws jetbrains.buildServer.server.rest.errors.LocatorProcessException if there are more then a single dimension definition for a 'dimensionName' name or the dimension has no value specified.
   */
  @Nullable
  public String getSingleDimensionValue(@NotNull final String dimensionName) {
    myUsedDimensions.add(dimensionName);
    return lookupSingleDimensionValue(dimensionName);
  }

  /**
   * Extracts the multiple dimension value from dimensions.
   *
   * @param dimensionName the name of the dimension to extract value.   @return empty collection if no such dimension is found, values of the dimension otherwise.
   * @throws jetbrains.buildServer.server.rest.errors.LocatorProcessException if there are more then a single dimension definition for a 'dimensionName' name or the dimension has no value specified.
   */
  @NotNull
  public List<String> getDimensionValue(@NotNull final String dimensionName) {
    myUsedDimensions.add(dimensionName);
    return lookupDimensionValue(dimensionName);
  }

  @NotNull
  public List<String> lookupDimensionValue(@NotNull final String dimensionName) {
    Collection<String> idDimension = myDimensions.get(dimensionName);
    return idDimension != null ? new ArrayList<String>(idDimension) : Collections.<String>emptyList();
  }

  /**
   * Same as getSingleDimensionValue but does not mark the value as used
   */
  @Nullable
  public String lookupSingleDimensionValue(@NotNull final String dimensionName) {
    Collection<String> idDimension = myDimensions.get(dimensionName);
    if (idDimension == null || idDimension.isEmpty()) {
      return null;
    }
    if (idDimension.size() > 1) {
      throw new LocatorProcessException("Only single '" + dimensionName + "' dimension is supported in locator. Found: " + idDimension);
    }
    final String result = idDimension.iterator().next();
    //noinspection StringEquality
    if (result == ANY_LITERAL) {
      //if it was $any without ()-escaping as complex value, return no value
      return null;
    }
    return result;
  }

  public int getDimensionsCount() {
    return myDimensions.size();
  }

  /**
   * Replaces all the dimensions values to the one specified.
   * Should be used only for multi-dimension locators.
   *
   * @param name  name of the dimension
   * @param value value of the dimension
   */
  public Locator setDimension(@NotNull final String name, @NotNull final String value) {
    if (isSingleValue()) {
      throw new IllegalArgumentException("Attempt to set dimension '" + name + "' for single value locator.");
    }
    myDimensions.put(name, Collections.singletonList(value));
    markUnused(name);
    modified = true; // todo: use setDimension to replace the dimension in myRawValue
    return this;
  }

  /**
   * Sets the dimension specified to the passed value if the dimension is not yet set. Does noting is the dimension already has a value.
   * Should be used only for multi-dimension locators.
   *
   * @param name  name of the dimension
   * @param value value of the dimension
   */
  public Locator setDimensionIfNotPresent(@NotNull final String name, @NotNull final String value) {
    Collection<String> idDimension = myDimensions.get(name);
    if (idDimension == null || idDimension.isEmpty()) {
      setDimension(name, value);
    }
    return this;
  }

  /**
   * Removes the dimension from the loctor. If no other dimensions are present does nothing and returns false.
   * Should be used only for multi-dimension locators.
   *
   * @param name name of the dimension
   */
  public boolean removeDimension(@NotNull final String name) {
    if (isSingleValue()) {
      throw new LocatorProcessException("Attempt to remove dimension '" + name + "' for single value locator.");
    }
    boolean result = myDimensions.get(name) != null;
    myDimensions.remove(name);
    modified = true; // todo: use setDimension to replace the dimension in myRawValue
    return result;
  }

  /**
   * Provides the names of dimensions whose values were never retrieved and those not marked via addIgnoreUnusedDimensions
   *
   * @return names of the dimensions not yet queried
   */
  @NotNull
  public Set<String> getUnusedDimensions() {
    Set<String> result;
    if (isSingleValue()) {
      result = new HashSet<String>(Collections.singleton(LOCATOR_SINGLE_VALUE_UNUSED_NAME));
    } else {
      result = new HashSet<String>(myDimensions.keySet());
    }
    result.removeAll(myUsedDimensions);
    result.removeAll(myIgnoreUnusedDimensions);
    return result;
  }

  /**
   * Provides the names of dimensions whose values were retrieved
   */
  @NotNull
  public Set<String> getUsedDimensions() {
    return new HashSet<String>(myUsedDimensions);
  }

  public boolean isUnused(@NotNull final String dimensionName) {
    return !myUsedDimensions.contains(dimensionName);
  }

  /**
   * Marks the passed dimensions as not used.
   * This also has a side effect of not reporting the dimensions as known but not reported, see "reportKnownButNotReportedDimensions" method.
   *
   * @param dimensionNames
   */
  public void markUnused(@NotNull String... dimensionNames) {
    myUsedDimensions.removeAll(Arrays.asList(dimensionNames));
  }

  /**
   * Marks all the used dimensions as not used.
   * This also has a side effect of not reporting the dimensions as known but not reported, see "reportKnownButNotReportedDimensions" method.
   *
   * @param dimensionNames
   */
  public void markAllUnused() {
    myUsedDimensions.clear();
  }

  /**
   * Returns a locator based on the supplied one replacing the numeric value of the dimension specified with the passed number.
   * The structure of the returned locator might be different from the passed one, while the same dimensions and values are present.
   *
   * @param locator       existing locator (should be valid), or null to create new locator
   * @param dimensionName only alpha-numeric characters are supported! Only numeric values without brackets are supported!
   * @param value         new value for the dimension, only alpha-numeric characters are supported!
   * @return
   */
  public static String setDimension(@Nullable final String locator, @NotNull final String dimensionName, final String value) {
    if (locator == null){
      return Locator.getStringLocator(dimensionName, value);
    }

    try {
      return new Locator(locator).setDimension(dimensionName, value).getStringRepresentation();
    } catch (LocatorProcessException e) {
      //not a valid locator... try replacing in the string, but might actually need to throw an error here
      final Matcher matcher = Pattern.compile(dimensionName + DIMENSION_NAME_VALUE_DELIMITER + "\\d+").matcher(locator);
      String result = matcher.replaceFirst(dimensionName + DIMENSION_NAME_VALUE_DELIMITER + value);
      try {
        matcher.end();
      } catch (IllegalStateException ex) {
        throw new LocatorProcessException("Cannot replace locator values: invalid locator '" + locator + "'");
      }
      return result;
    }
  }

  public static String setDimension(@Nullable final String locator, @NotNull final String dimensionName, final long value) {
    return setDimension(locator, dimensionName, String.valueOf(value));
  }

  /**
   * Same as "setDimension" but only modifies the locator if the dimension was not present already.
   *
   * @param locator       existing locator, should be valid!
   * @param dimensionName only alpha-numeric characters are supported! Only numeric values without brackets are supported!
   * @param value         new value for the dimension, only alpha-numeric characters are supported!
   * @return
   */
  @Nullable
  public static String setDimensionIfNotPresent(@Nullable final String locator, @NotNull final String dimensionName, @Nullable final String value) {
    if (value == null){
      return locator;
    }
    if (locator == null){
      return Locator.getStringLocator(dimensionName, value);
    }
    return (new Locator(locator)).setDimensionIfNotPresent(dimensionName, value).getStringRepresentation();
  }

  public static boolean isAny(@NotNull final String value) {
    return ANY_LITERAL.equals(value);
  }

  public static String getStringLocator(final String... strings) {
    final Locator result = createEmptyLocator();
    if (strings.length % 2 != 0) {
      throw new IllegalArgumentException("The number of parameters should be even");
    }
    for (int i = 0; i < strings.length; i = i + 2) {
      result.setDimension(strings[i], strings[i + 1]);
    }
    return result.getStringRepresentation();
  }

  public String getStringRepresentation() {
    if (mySingleValue != null) {
      return mySingleValue;
    }
    if (!modified) {
      return myRawValue;
    }
    String result = "";
    for (Map.Entry<String, List<String>> dimensionEntries : myDimensions.entrySet()) {
      for (String value : dimensionEntries.getValue()) {
        if (!StringUtil.isEmpty(result)) {
          result += DIMENSIONS_DELIMITER;
        }
        result += dimensionEntries.getKey() + DIMENSION_NAME_VALUE_DELIMITER + getValueForRendering(value);
      }
    }
    return result;
  }

  private String getValueForRendering(final String value) {
    if (value.contains(DIMENSIONS_DELIMITER) || value.contains(DIMENSION_NAME_VALUE_DELIMITER)) {
      return DIMENSION_COMPLEX_VALUE_START_DELIMITER + value + DIMENSION_COMPLEX_VALUE_END_DELIMITER;
    }
    return value;
  }

  @Override
  public String toString() {
    return getStringRepresentation();
  }
}
