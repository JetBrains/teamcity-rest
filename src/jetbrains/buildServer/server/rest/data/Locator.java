/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
import java.nio.charset.StandardCharsets;
import java.util.*;
import jetbrains.buildServer.server.rest.data.util.LocatorUtil;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.util.StringPool;
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
 * <p/>
 * Example:
 * <ul>
 * <li><tt>31</tt> - locator wth single value "31"</li>
 * <li><tt>name:Frodo</tt> - locator wth single dimension "name" which has value "Frodo"</li>
 * <li><tt>name:Frodo,age:14</tt> - locator with two dimensions "name" which has value "Frodo" and "age", which has value "14"</li>
 * <li><tt>text:(Freaking symbols:,name)</tt> - locator with single dimension "text" which has value "Freaking symbols:,name"</li>
 * </ul>
 * <p/>
 * Dimension name contain only alpha-numeric symbols in usual mode. Extended mode allows in addition to use any non-empty known dimensions name which contain no ":", ",", "(", symbols)
 * <p/>
 * Dimension value should not contain symbol "," if not enclosed in "(" and ")" or
 * should contain properly paired parentheses ("(" and ")") if enclosed in "(" and ")"
 * <p/>
 * Usual mode supports single value locators. In extended mode, those will result in single dimension with value as name and empty value.
 *
 * @author Yegor.Yarko
 * @date 13.08.2010
 */
public class Locator {
  private static final Logger LOG = Logger.getInstance(Locator.class.getName());
  public static final String DIMENSION_NAME_VALUE_DELIMITER = ":";
  private static final String DIMENSIONS_DELIMITER = ",";
  private static final String DIMENSION_COMPLEX_VALUE_START_DELIMITER = "(";
  private static final String DIMENSION_COMPLEX_VALUE_END_DELIMITER = ")";
  private static final List<String> LIST_WITH_EMPTY_STRING = Arrays.asList("");
  private static final String BASE64_ESCAPE_FAKE_DIMENSION = "$base64";
  public static final String LOCATOR_SINGLE_VALUE_UNUSED_NAME = "$singleValue";
  public static final String ANY_LITERAL = "$any";
  public static final String HELP_DIMENSION = "$help";

  public static final String BOOLEAN_TRUE = "true";
  public static final String BOOLEAN_FALSE = "false";
  public static final String BOOLEAN_ANY = "any";


  private final String myRawValue;
  @NotNull private final Metadata myMetadata;
  private boolean modified = false;
  private final Map<String, List<String>> myDimensions;
  private final String mySingleValue;

  @NotNull private final Set<String> myUsedDimensions;
  @Nullable private String[] mySupportedDimensions;
  @NotNull private final Collection<String> myIgnoreUnusedDimensions = new HashSet<>();
  @NotNull private final Collection<String> myHiddenSupportedDimensions = new HashSet<>();
  private DescriptionProvider myDescriptionProvider = null;

  public Locator(@Nullable final String locator) throws LocatorProcessException {
    this(locator, (String[])null);
  }

  /**
   * Creates a new locator as a copy of the passed one preserving the entire state.
   *
   * @param locator
   */
  public Locator(@NotNull final Locator locator) {
    myRawValue = locator.myRawValue;
    modified = locator.modified;

    myDimensions = new HashMap<>(locator.myDimensions);
    mySingleValue = locator.mySingleValue;
    myUsedDimensions = new HashSet<>(locator.myUsedDimensions);
    mySupportedDimensions = locator.mySupportedDimensions != null ? locator.mySupportedDimensions.clone() : null;
    myIgnoreUnusedDimensions.addAll(locator.myIgnoreUnusedDimensions);
    myHiddenSupportedDimensions.addAll(locator.myHiddenSupportedDimensions);
    myMetadata = locator.myMetadata;
  }

  /**
   * Creates usual mode locator
   *
   * @param locator
   * @param supportedDimensions dimensions supported in this locator, used in {@link #checkLocatorFullyProcessed()}
   * @throws LocatorProcessException
   */
  public Locator(@Nullable final String locator, @Nullable final String... supportedDimensions) throws LocatorProcessException {
    this(locator, false, supportedDimensions);
  }

  /**
   * Creates usual or extended mode locator
   *
   * @param locator
   * @param supportedDimensions dimensions supported in this locator, used in {@link #checkLocatorFullyProcessed()}
   * @throws LocatorProcessException
   */
  public Locator(@Nullable final String locator, final boolean extendedMode, @Nullable final String... supportedDimensions) throws LocatorProcessException {
    this(locator, new Metadata(extendedMode, true), supportedDimensions);
  }

  /**
   * Creates locator with given metadata
   *
   * @param locator
   * @param supportedDimensions dimensions supported in this locator, used in {@link #checkLocatorFullyProcessed()}
   * @throws LocatorProcessException
   */
  public Locator(@Nullable final String locator, final Metadata metadata, @Nullable final String... supportedDimensions) throws LocatorProcessException {
    myRawValue = RestContext.getThreadLocalStringPool().reuse(locator);
    myMetadata = new Metadata(metadata);
    if (StringUtil.isEmpty(locator)) {
      throw new LocatorProcessException("Invalid locator. Cannot be empty.");
    }
    mySupportedDimensions = supportedDimensions;
    myUsedDimensions = new HashSet<>(mySupportedDimensions == null ? 10 : Math.max(mySupportedDimensions.length, 10));
    String escapedValue = getUnescapedSingleValue(locator, myMetadata);

    if (escapedValue != null) {
      mySingleValue = escapedValue;
      myDimensions = new HashMap<>();
    } else if (!myMetadata.extendedMode && !hasDimensions(locator)) {
      mySingleValue = locator;
      myDimensions = new HashMap<>();
    } else {
      mySingleValue = null;
      myHiddenSupportedDimensions.add(HELP_DIMENSION);
      myIgnoreUnusedDimensions.add(HELP_DIMENSION);
      myDimensions = parse(locator, mySupportedDimensions, myHiddenSupportedDimensions, myMetadata.extendedMode);
    }
  }

  /**
   * Creates an empty locator with dimensions.
   */
  private Locator(@Nullable final String... supportedDimensions) {
    myRawValue = "";
    mySingleValue = null;
    myDimensions = new HashMap<>();
    mySupportedDimensions = supportedDimensions;
    if (mySupportedDimensions == null) {
      myUsedDimensions = new HashSet<>();
    } else {
      myUsedDimensions = new HashSet<>(mySupportedDimensions.length);
    }
    myHiddenSupportedDimensions.add(HELP_DIMENSION);
    myIgnoreUnusedDimensions.add(HELP_DIMENSION);
    myMetadata = new Metadata(false, true);
  }

  @Nullable
  private static String getUnescapedSingleValue(@NotNull final String text, final Metadata metadata) {
    if (text.length() > (DIMENSION_COMPLEX_VALUE_START_DELIMITER.length() + DIMENSION_COMPLEX_VALUE_END_DELIMITER.length()) &&
        text.startsWith(DIMENSION_COMPLEX_VALUE_START_DELIMITER) && text.endsWith(DIMENSION_COMPLEX_VALUE_END_DELIMITER)) {
      //the value is wrapped in braces
      if (metadata.surroundingBracesHaveSpecialMeaning) {
        return text.substring(DIMENSION_COMPLEX_VALUE_START_DELIMITER.length(), text.length() - DIMENSION_COMPLEX_VALUE_END_DELIMITER.length());
      } else {
        return text;
      }
    } else {
      return getBase64UnescapedSingleValue(text, metadata.extendedMode);
    }
  }

  @Nullable
  private static String getBase64UnescapedSingleValue(final @NotNull String text, final boolean extendedMode) {
    if (!TeamCityProperties.getBooleanOrTrue("rest.locator.allowBase64")) return null;
    if (!text.startsWith(BASE64_ESCAPE_FAKE_DIMENSION + DIMENSION_NAME_VALUE_DELIMITER)) {
      //optimization until more then one dimension is supported
      return null;
    }

    Map<String, List<String>> parsedDimensions;
    try {
      parsedDimensions = parse(text, new String[]{BASE64_ESCAPE_FAKE_DIMENSION}, Collections.emptyList(), extendedMode);
    } catch (LocatorProcessException e) {
      return null;
    }

    if (parsedDimensions.size() != 1) {
      //more then one dimension found
      return null;
    }

    List<String> base64EncodedValues = parsedDimensions.get(BASE64_ESCAPE_FAKE_DIMENSION);
    if (base64EncodedValues.isEmpty()) return null;
    if (base64EncodedValues.size() != 1) throw new LocatorProcessException("More then 1 " + BASE64_ESCAPE_FAKE_DIMENSION + " values, only single one is supported");
    String base64EncodedValue = base64EncodedValues.get(0);

    byte[] decoded;
    try {
      decoded = Base64.getUrlDecoder().decode(base64EncodedValue.getBytes(StandardCharsets.UTF_8));
    } catch (IllegalArgumentException first) {
      try {
        decoded = Base64.getDecoder().decode(base64EncodedValue.getBytes(StandardCharsets.UTF_8));
      } catch (IllegalArgumentException second) {
        throw new LocatorProcessException("Invalid Base64url character sequence: '" + base64EncodedValue + "'", first);
      }
    }

    try {
      return new String(decoded, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new LocatorProcessException("Error converting decoded '" + base64EncodedValue + "' value bytes to UTF-8 string", e);
    }
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
   * The resultant locator will have all the dimensions from "defaults" locator set unless already defined.
   * If "locator" text is empty, "defaults" locator will be used
   *
   * @param locator
   * @param defaults
   * @param supportedDimensions
   * @return
   */
  @NotNull
  public static Locator createLocator(@Nullable final String locator, @Nullable final Locator defaults, @Nullable final String[] supportedDimensions) {
    Locator result;
    if (locator != null || defaults == null) {
      result = new Locator(locator, supportedDimensions);
    } else {
      result = Locator.createEmptyLocator(supportedDimensions);
    }

    if (defaults != null && !result.isSingleValue()) {
      defaults.myDimensions.keySet().forEach(dimensionName -> {
        List<String> values = defaults.getDimensionValue(dimensionName);
        if (!values.isEmpty()) {
          result.setDimensionIfNotPresent(dimensionName, values);
          if (defaults.myHiddenSupportedDimensions.contains(dimensionName)) {
            result.myHiddenSupportedDimensions.add(dimensionName);
          }
          if (defaults.myIgnoreUnusedDimensions.contains(dimensionName)) {
            result.myIgnoreUnusedDimensions.add(dimensionName);
          }
        }
      });
    }

    return result;
  }

  /**
   * The resultant locator will have all the dimensions and values of "mainLocator" and those from "defaultsLocator" which are not defined in "mainLocator"
   * If "mainLocator" text is empty, "defaultsLocator" locator will be used
   *
   * @see #createLocator(String, Locator, String[])
   */
  @NotNull
  public static String merge(@Nullable final String mainLocator, @Nullable final String defaultsLocator) {
    return createLocator(mainLocator, defaultsLocator == null ? null : new Locator(defaultsLocator), null).getStringRepresentation();
  }

  @NotNull
  public static Locator createPotentiallyEmptyLocator(@Nullable final String locatorText) { //todo: may be support this in Locator constructor?
    return StringUtil.isEmpty(locatorText) ? Locator.createEmptyLocator() : new Locator(locatorText);
  }

  @Nullable
  @Contract("null -> null; !null -> !null")
  public static Locator locator(@Nullable final String locatorText) {
    return locatorText != null ? new Locator(locatorText) : null;
  }

  @NotNull
  public static Locator createEmptyLocator(@Nullable final String... supportedDimensions) {
    return new Locator(supportedDimensions);
  }

  public boolean isEmpty() {
    return mySingleValue == null && myDimensions.isEmpty();
  }

  public void addSupportedDimensions(final String... dimensions) {
    if (mySupportedDimensions == null) {
      mySupportedDimensions = dimensions;
    } else {
      mySupportedDimensions = CollectionsUtil.join(Arrays.asList(mySupportedDimensions), Arrays.asList(dimensions)).toArray(mySupportedDimensions);
    }
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
    myHiddenSupportedDimensions.addAll(Arrays.asList(hiddenDimensions));
  }

  @NotNull
  private static HashMap<String, List<String>> parse(@NotNull final String locator,
                                                     @Nullable final String[] supportedDimensions,
                                                     @NotNull final Collection<String> hiddenSupportedDimensions,
                                                     final boolean extendedMode) {
    StringPool stringPool = RestContext.getThreadLocalStringPool();
    HashMap<String, List<String>> result = new HashMap<>();
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
      if (!isValidName(currentDimensionName, supportedDimensions, hiddenSupportedDimensions, extendedMode)) {
        throw new LocatorProcessException(locator, parsedIndex, "Invalid dimension name :'" + currentDimensionName + "'. Should contain only alpha-numeric symbols" +
                                                                (supportedDimensions == null || supportedDimensions.length == 0
                                                                 ? ""
                                                                 : " or be known one: " + Arrays.toString(supportedDimensions)));
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
            final int complexValueEnd = findNextOrEndOfStringConsideringBraces(valueAndRest, null);
            if (complexValueEnd < 0) {
              throw new LocatorProcessException(locator, parsedIndex + DIMENSION_COMPLEX_VALUE_START_DELIMITER.length(),
                                                "Could not find matching '" + DIMENSION_COMPLEX_VALUE_END_DELIMITER + "'");
            }
            currentDimensionValue = valueAndRest.substring(DIMENSION_COMPLEX_VALUE_START_DELIMITER.length(), complexValueEnd - DIMENSION_COMPLEX_VALUE_END_DELIMITER.length());
            parsedIndex = parsedIndex + complexValueEnd;
            if (parsedIndex != locator.length()) {
              if (!locator.startsWith(DIMENSIONS_DELIMITER, parsedIndex)) {
                throw new LocatorProcessException(locator, parsedIndex, "No dimensions delimiter '" + DIMENSIONS_DELIMITER + "' after complex value");
              } else {
                parsedIndex += DIMENSIONS_DELIMITER.length();
              }
            }
          } else {
            int valueEnd = findNextOrEndOfStringConsideringBraces(valueAndRest, DIMENSIONS_DELIMITER);
            if (valueEnd < 0) {
              throw new LocatorProcessException(locator, parsedIndex, "Could not find matching '" + DIMENSION_COMPLEX_VALUE_END_DELIMITER + "'");
            } else if (valueEnd == valueAndRest.length()) {
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
          String unescapedValue = getBase64UnescapedSingleValue(currentDimensionValue, extendedMode);
          if (unescapedValue != null) currentDimensionValue = unescapedValue;
        }
      }
      currentDimensionName = stringPool.reuse(currentDimensionName);
      final List<String> currentList = result.get(currentDimensionName);
      final List<String> newList;
      if (currentList == null) {
        // Dimension with an empy string value is a frequent case in a Fields, so let's reuse a special list for that list.
        newList = currentDimensionValue.equals("") ? LIST_WITH_EMPTY_STRING : Arrays.asList(currentDimensionValue);
      } else {
        newList = new ArrayList<>(currentList);
        newList.add(stringPool.reuse(currentDimensionValue));
      }

      result.put(currentDimensionName, newList);
    }

    return result;
  }

  /**
   * Scans text skipping blocks wrapped in "()", returns on found stopText, after closing ")" if stopText is null or on reaching end of string
   *
   * @param text
   * @param stopText
   * @return negative value if text is not well-formed, position of a char before stopText, last char of () sequence if stopText is null or length of the text
   */
  private static int findNextOrEndOfStringConsideringBraces(@NotNull final String text, @Nullable final String stopText) {
    int pos = 0;
    int nesting = 0;
    while (pos < text.length()) {
      if (text.startsWith(DIMENSION_COMPLEX_VALUE_START_DELIMITER, pos)) {
        nesting++;
        pos = pos + DIMENSION_COMPLEX_VALUE_START_DELIMITER.length();
      } else if (text.startsWith(DIMENSION_COMPLEX_VALUE_END_DELIMITER, pos)) {
        if (nesting == 0) {
          //out of order ")", ignore
        } else {
          nesting--;
        }
        pos = pos + DIMENSION_COMPLEX_VALUE_END_DELIMITER.length();
        if (nesting == 0 && stopText == null) return pos;
      } else if (nesting == 0 && stopText != null && text.startsWith(stopText, pos)) {
        return pos;
      } else {
        pos++;
      }
    }
    if (nesting != 0) return -pos;
    return pos;
  }

  private static boolean isValidName(@Nullable final String name,
                                     final String[] supportedDimensions,
                                     @NotNull final Collection<String> hiddenSupportedDimensions,
                                     final boolean extendedMode) {
    if ((supportedDimensions == null || !Arrays.asList(supportedDimensions).contains(name)) && !hiddenSupportedDimensions.contains(name)) {
      for (int i = 0; i < name.length(); i++) {
        if (!Character.isLetter(name.charAt(i)) && !Character.isDigit(name.charAt(i)) && !(name.charAt(i) == '-' && extendedMode)) return false;
      }
    }
    return true;
  }

  //todo: use this whenever possible
  public void checkLocatorFullyProcessed() {
    processHelpRequest();
    String reportKindString = TeamCityProperties.getProperty("rest.report.unused.locator", "error");
    if (!TeamCityProperties.getBooleanOrTrue("rest.report.locator.errors")) {
      reportKindString = "off";
    }
    if (reportKindString.equals("off")) {
      return;
    }
    if (reportKindString.contains("reportKnownButNotReportedDimensions")) {
      reportKnownButNotReportedDimensions();
    }
    final Set<String> unusedDimensions = getUnusedDimensions();
    if (unusedDimensions.isEmpty()) {
      return;
    }
    Set<String> ignoredDimensions = mySupportedDimensions == null ? Collections.emptySet() :
                                    CollectionsUtil.intersect(unusedDimensions, CollectionsUtil.join(Arrays.asList(mySupportedDimensions), myHiddenSupportedDimensions));
    Set<String> unknownDimensions = CollectionsUtil.minus(unusedDimensions, ignoredDimensions);
    StringBuilder message = new StringBuilder();
    if (unknownDimensions.isEmpty() && unusedDimensions.size() == myDimensions.size()) {
      //nothing is used
      message.append("Unsupported locator: no dimensions are used, try another combination of the dimensions.");
    } else if (unusedDimensions.size() > 1) {
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
    } else if (unusedDimensions.size() == 1) {
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
    if (mySupportedDimensions != null && mySupportedDimensions.length > 0) {
      if (message.length() > 0) {
        message.append(" ");
      }
      message.append(getLocatorDescription(reportKindString.contains("includeHidden")));
    }
    if (reportKindString.contains("log")) {
      if (reportKindString.contains("log-warn")) {
        LOG.warn(message.toString());
      } else {
        LOG.debug(message.toString());
      }
    }
    if (reportKindString.contains("error")) {
      throw new LocatorProcessException(this, message.toString());
    }
  }

  public boolean isHelpRequested() {
    if (isSingleValue()) return HELP_DIMENSION.equals(lookupSingleValue());
    return getSingleDimensionValue(HELP_DIMENSION) != null;
  }

  @NotNull
  public Locator helpOptions() {
    return createPotentiallyEmptyLocator(getSingleDimensionValue(HELP_DIMENSION));
  }

  public void processHelpRequest() {
    if (isHelpRequested()) {
      throw new LocatorProcessException("Locator help requested: " + getLocatorDescription(helpOptions().getSingleDimensionValueAsStrictBoolean("hidden", false)));
    }
  }

  public static void processHelpRequest(@Nullable final String singleValue, @NotNull final String helpMessage) {
    if (HELP_DIMENSION.equals(singleValue)) {
      throw new LocatorProcessException("Locator help requested: " + helpMessage);
    }
  }

  public interface DescriptionProvider {
    @NotNull
    String get(@NotNull Locator locator, boolean includeHidden);
  }

  public void setDescriptionProvider(@NotNull final DescriptionProvider descriptionProvider) {
    myDescriptionProvider = descriptionProvider;
  }

  @NotNull
  public String getLocatorDescription(boolean includeHidden) {
    if (myDescriptionProvider != null) {
      return myDescriptionProvider.get(this, includeHidden);
    }
    StringBuilder result = new StringBuilder();
    if (mySupportedDimensions != null) {
      result.append("Supported dimensions are: [");
      for (String dimension : mySupportedDimensions) {
        if (!myHiddenSupportedDimensions.contains(dimension)) {
          result.append(dimension).append(", ");
        }
      }
      if (mySupportedDimensions.length > 0) result.delete(result.length() - ", ".length(), result.length());
      result.append("]");
    }
    if (includeHidden && !myHiddenSupportedDimensions.isEmpty()) {
      result.append(" Hidden supported are: [");
      result.append(StringUtil.join(", ", myHiddenSupportedDimensions));
      result.append("]");
    }
    return result.toString();
  }

  private void reportKnownButNotReportedDimensions() {
    final Set<String> usedDimensions = new HashSet<>(myUsedDimensions);
    if (mySupportedDimensions != null) usedDimensions.removeAll(Arrays.asList(mySupportedDimensions));
    usedDimensions.removeAll(myHiddenSupportedDimensions);
    if (usedDimensions.size() > 0) {
      //found used dimensions which are not declared as used.

      //noinspection ThrowableInstanceNeverThrown
      final Exception exception = new Exception("Helper exception to get stacktrace");
      LOG.info("Locator " + StringUtil.pluralize("dimension", usedDimensions.size()) + " " + usedDimensions + (usedDimensions.size() > 1 ? " are" : " is") +
               " actually used but not declared as supported. Declared locator details: " + getLocatorDescription(true), exception);
    }
  }

  /**
   * Examles:
   * <pre>
   *   12345 -> true
   *   bar -> true
   *   foo:bar -> false
   *   "" -> false
   * </pre>
   *
   * @return
   */
  public boolean isSingleValue() {
    return mySingleValue != null;
  }

  /**
   * Examples:
   * <pre>
   *   12345 -> 12345
   *   bar -> bar
   *   foo:bar -> null
   *   "" -> null
   * </pre>
   *
   * @return locator's not-null value if it is single-value locator, 'null' otherwise
   */
  @Nullable
  public String getSingleValue() {
    markUsed(LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    return lookupSingleValue();
  }

  /**
   * Gets the value without marking it as used.
   * @see {@link Locator#getSingleValue()}
   * @return the value of single-value-locator
   */
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
    markUsed(dimensionName);
    return lookupSingleDimensionValueAsLong(dimensionName, defaultValue);
  }

  @Nullable
  @Contract("_, !null -> !null")
  public Long lookupSingleDimensionValueAsLong(@NotNull final String dimensionName, @Nullable Long defaultValue) {
    final String value = lookupSingleDimensionValue(dimensionName);
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
   * @return "null" if not defined or set to "any"
   */
  @Nullable
  public Boolean getSingleDimensionValueAsBoolean(@NotNull final String dimensionName) {
    try {
      return LocatorUtil.getBooleanAllowingAny(getSingleDimensionValue(dimensionName));
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
      return LocatorUtil.getBooleanAllowingAny(lookupSingleDimensionValue(dimensionName));
    } catch (LocatorProcessException e) {
      throw new LocatorProcessException("Invalid value of dimension '" + dimensionName + "': " + e.getMessage(), e);
    }
  }

  /**
   * @param dimensionName name of the dimension
   * @param defaultValue default value to use if dimension is not found
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

  @Nullable
  @Contract("_, !null -> !null")
  public Boolean getSingleDimensionValueAsStrictBoolean(@NotNull final String dimensionName, @Nullable Boolean defaultValue) {
    final String value = getSingleDimensionValue(dimensionName);
    if (value == null) {
      return defaultValue;
    }
    return LocatorUtil.getStrictBooleanOrReportError(value);
  }

  /**
   * Extracts raw dimension value if it is single. Error if not single.
   *
   * Examples:
   * <pre>
   *   foo in foo:(bar:xyz,dee:jux),abc:def -> bar:xyz,dee:jux
   *   foo in foo:bar,foo:xyz -> error
   *   foo in foo:() -> empty locator
   *   foo in bar:buz -> null
   *   foo in foo:(buz) -> ???
   * </pre>
   *
   * @param dimensionName the name of the dimension to extract value.   @return 'null' if no such dimension is found, value of the dimension otherwise.
   * @throws jetbrains.buildServer.server.rest.errors.LocatorProcessException if there are more then a single dimension definition for a 'dimensionName' name or the dimension has no value specified.
   */
  @Nullable
  public String getSingleDimensionValue(@NotNull final String dimensionName) {
    markUsed(dimensionName);
    return lookupSingleDimensionValue(dimensionName);
  }

  /**
   * Get dimension value as locator.
   * <p/>
   * Examples:
   * <pre>
   *   foo in foo -> null
   *   foo in foo(bar) -> bar
   *   foo in foo(bar:buz) -> bar:buz
   *   foo in foo:bar,foo:buz -> error
   * </pre>
   *
   * @param dimensionName
   * @return
   */
  @Nullable
  public Locator get(@NotNull String dimensionName) {
    if (isEmpty()) {
      return null;
    }
    String dimensionRawValue = getSingleDimensionValue(dimensionName);
    return Locator.locator(dimensionRawValue);
  }

  /**
   * Extracts the multiple dimension value from dimensions.
   *
   * @param dimensionName the name of the dimension to extract value.   @return empty collection if no such dimension is found, values of the dimension otherwise.
   * @throws jetbrains.buildServer.server.rest.errors.LocatorProcessException if there are more then a single dimension definition for a 'dimensionName' name or the dimension has no value specified.
   */
  @NotNull
  public List<String> getDimensionValue(@NotNull final String dimensionName) {
    markUsed(dimensionName);
    return lookupDimensionValue(dimensionName);
  }

  @NotNull
  public List<String> lookupDimensionValue(@NotNull final String dimensionName) {
    Collection<String> idDimension = myDimensions.get(dimensionName);
    return idDimension != null ? new ArrayList<String>(idDimension) : Collections.emptyList();
  }

  public boolean isAnyPresent(@NotNull final String... dimensionName) {
    for (String name : dimensionName) {
      if (myDimensions.get(name) != null) return true;
    }
    return false;
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

  @NotNull
  public Collection<String> getDefinedDimensions() {
    return myDimensions.keySet();
  }

  /**
   * Replaces all the dimensions values to the one specified.
   * Should be used only for multi-dimension locators.
   *
   * @param name  name of the dimension
   * @param value value of the dimension
   */
  @NotNull
  public Locator setDimension(@NotNull final String name, @NotNull final String value) {
    return setDimension(name, Collections.singletonList(value));
  }

  /**
   * Replaces all the dimensions values to those specified.
   * Should be used only for multi-dimension locators.
   *
   * @param name  name of the dimension
   * @param values new values of the dimension
   */
  @NotNull
  public Locator setDimension(@NotNull final String name, @NotNull final List<String> values) {
    if (isSingleValue()) {
      throw new IllegalArgumentException("Attempt to set dimension '" + name + "' for single value locator.");
    }
    myDimensions.put(name, values);
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
  @NotNull
  public Locator setDimensionIfNotPresent(@NotNull final String name, @NotNull final String value) {
    Collection<String> idDimension = myDimensions.get(name);
    if (idDimension == null || idDimension.isEmpty()) {
      setDimension(name, value);
    }
    return this;
  }

  @NotNull
  public Locator setDimensionIfNotPresent(@NotNull final String name, @NotNull final List<String> values) {
    Collection<String> idDimension = myDimensions.get(name);
    if (idDimension == null || idDimension.isEmpty()) {
      setDimension(name, values);
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
      result = new HashSet<>(myDimensions.keySet());
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
    return new HashSet<>(myUsedDimensions);
  }

  /**
   * @param dimensionName the name of the dimention.
   * @return true if locator contains such dimension, and it is not marked as used. false otherwise.
   */
  public boolean isUnused(@NotNull final String dimensionName) {
    return myDimensions.containsKey(dimensionName) && !myUsedDimensions.contains(dimensionName);
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

  public void markUsed(@NotNull Collection<String> dimensionNames) {
    myUsedDimensions.addAll(dimensionNames);
  }

  public void markUsed(@NotNull String dimensionName) {
    myUsedDimensions.add(dimensionName);
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
   * Same as "setDimension" but only modifies the locator if the dimension was not present already.
   *
   * @param locator       existing locator, should be valid!
   * @param dimensionName only alpha-numeric characters are supported! Only numeric values without brackets are supported!
   * @param value         new value for the dimension, only alpha-numeric characters are supported!
   * @return
   */
  @Nullable
  @Contract("_, _, !null -> !null; !null, _, _ -> !null")
  public static String setDimensionIfNotPresent(@Nullable final String locator, @NotNull final String dimensionName, @Nullable final String value) {
    if (value == null) {
      return locator;
    }
    if (locator == null) {
      return Locator.getStringLocator(dimensionName, value);
    }
    return (new Locator(locator)).setDimensionIfNotPresent(dimensionName, value).getStringRepresentation();
  }

  /**
   * Creates locator text for several dimentions.
   * <br/>
   * Example:
   * <pre>
   *   ("foo", "bar", "xyz", "buz") -> "foo:bar,xyz:buz"
   * </pre>
   * <br/>
   * The number of arguments must be even.
   * <br/>
   *
   * @param strings each odd arguments is a name of a dimension. each event argument is the locator text.
   * @return
   * @throws IllegalArgumentException if odd number of arguments passed.
   * @implNote If no arguments passed, then undefined behaviour.
   * If some arguments are null, then undefined behaviour.
   */
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

  public String getStringRepresentation() { //todo: what is returned for empty locator???    and replace "actualLocator.isEmpty() ? null : actualLocator.getStringRepresentation()"
    if (mySingleValue != null) {
      return getValueForRendering(mySingleValue);
    }
    if (!modified) {
      return myRawValue;
    }
    StringBuilder result = new StringBuilder();
    myDimensions.entrySet().stream()
                .sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey()))
                .forEach(entry -> {
      String name = entry.getKey();
      List<String> values = entry.getValue();

      for (String value : values) {
        if (result.length() > 0) {
          result.append(DIMENSIONS_DELIMITER);
        }
        result.append(name).append(DIMENSION_NAME_VALUE_DELIMITER).append(getValueForRendering(value));
      }
    });

    return result.toString();
  }

  @NotNull
  private static String getValueForRendering(@NotNull final String value) {
    LevelData nestingData = getNestingData(value);
    if (nestingData.getCurrentLevel() != 0 || nestingData.getMinLevel() < 0) {
      return DIMENSION_COMPLEX_VALUE_START_DELIMITER
             + BASE64_ESCAPE_FAKE_DIMENSION + DIMENSION_NAME_VALUE_DELIMITER +
             new String(Base64.getUrlEncoder().encode(value.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8)
             + DIMENSION_COMPLEX_VALUE_END_DELIMITER;
    }
    if (nestingData.getMaxLevel() > 0 || value.contains(DIMENSIONS_DELIMITER) || value.contains(DIMENSION_NAME_VALUE_DELIMITER)) {
      // this also covers case of (value.startsWith(DIMENSION_COMPLEX_VALUE_START_DELIMITER) && value.endsWith(DIMENSION_COMPLEX_VALUE_END_DELIMITER))
      return DIMENSION_COMPLEX_VALUE_START_DELIMITER + value + DIMENSION_COMPLEX_VALUE_END_DELIMITER;
    }
    return value;
  }

  private static LevelData getNestingData(@NotNull final String value) {
    LevelData data = new LevelData();
    for (int index = 0; index < value.length(); index++) {
      data.process(value.charAt(index));
    }
    return data;
  }

  private static class LevelData {
    private int myMaxLevel = 0;
    private int myMinLevel = 0;
    private int myCurrentLevel = 0;

    void process(char ch) {
      switch (ch) {
        case '(':  //DIMENSION_COMPLEX_VALUE_START_DELIMITER
          myCurrentLevel++;
          if (myMaxLevel < myCurrentLevel) myMaxLevel = myCurrentLevel;
          break;
        case ')':  //DIMENSION_COMPLEX_VALUE_END_DELIMITER
          myCurrentLevel--;
          if (myMinLevel > myCurrentLevel) myMinLevel = myCurrentLevel;
          break;
      }
    }

    public int getMaxLevel() {
      return myMaxLevel;
    }

    public int getMinLevel() {
      return myMinLevel;
    }

    public int getCurrentLevel() {
      return myCurrentLevel;
    }
  }

  @Override
  public String toString() {
    return getStringRepresentation();
  }

  public static class Metadata {
    final boolean extendedMode;
    final boolean surroundingBracesHaveSpecialMeaning;

    public Metadata(@NotNull final Metadata source) {
      extendedMode = source.extendedMode;
      surroundingBracesHaveSpecialMeaning = source.surroundingBracesHaveSpecialMeaning;
    }

    public Metadata(final boolean extendedMode, final boolean surroundingBracesHaveSpecialMeaning) {
      this.extendedMode = extendedMode;
      this.surroundingBracesHaveSpecialMeaning = surroundingBracesHaveSpecialMeaning;
    }
  }
}
