package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.util.MultiValuesMap;
import java.util.Collection;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class that support parsing of "locators".
 * Locator is a string with single value or several named "dimensions".
 * Example:
 * <tt>31</tt> - locator wth single value "31"
 * <tt>name:Frodo</tt> - locator wth single dimension "name" which has value "Frodo"
 * <tt>name:Frodo,age:14</tt> - locator with two dimensions "name" which has value "Frodo" and "age", which has value "14"
 *
 * @author Yegor.Yarko
 *         Date: 13.08.2010
 */
public class Locator {
  private static final String DIMENSION_NAME_VALUE_DELIMITER = ":";
  private static final String DIMENSIONS_DELIMITER = ",";

  private final MultiValuesMap<String, String> myDimensions = new MultiValuesMap<String, String>();
  private final boolean myHasDimentions;
  private final String mySingleValue;

  public Locator(@NotNull final String locator) {
    if (StringUtil.isEmpty(locator)) {
      throw new BadRequestException("Invalid locator. Cannot be empty.");
    }
    myHasDimentions = locator.indexOf(DIMENSION_NAME_VALUE_DELIMITER) != -1;
    if (!myHasDimentions) {
      mySingleValue = locator;
    } else {
      mySingleValue = null;
      for (String dimension : locator.split(DIMENSIONS_DELIMITER)) {
        int delimiterIndex = dimension.indexOf(DIMENSION_NAME_VALUE_DELIMITER);
        if (delimiterIndex > 0) {
          myDimensions.put(dimension.substring(0, delimiterIndex), dimension.substring(delimiterIndex + 1));
        } else {
          throw new NotFoundException(
            "Bad locator syntax: '" + locator + "'. Can't find dimension name in dimension string '" + dimension + "'");
        }
      }
    }
  }

  public boolean isSingleValue() {
    return !myHasDimentions;
  }

  @Nullable
  public String getSingleValue() {
    return mySingleValue;
  }

  @Nullable
  public Long getSingleValueAsLong() {
    if (mySingleValue == null) {
      return null;
    }
    try {
      return Long.parseLong(mySingleValue);
    } catch (NumberFormatException e) {
      throw new BadRequestException("Invalid single value: " + mySingleValue + ". Should be a number.");
    }
  }

  @Nullable
  public Long getSingleDimensionValueAsLong(@NotNull final String dimensionName) {
    final String value = getSingleDimensionValue(dimensionName);
    if (value == null) {
      return null;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new BadRequestException("Invalid value of dimension '" + dimensionName + "': " + value + ". Should be a number.");
    }
  }

  /**
   * Extracts the single dimension value from dimensions.
   *
   * @param dimensions    dimensions to extract value from.
   * @param dimensionName the name of the dimension to extract value.   @return 'null' if no such dimension is found, value of the dimension otherwise.
   * @throws jetbrains.buildServer.server.rest.errors.BadRequestException
   *          if there are more then a single dimension defiition for a 'dimensionName' name or the dimension has no value specified.
   */
  @Nullable
  public String getSingleDimensionValue(@NotNull final String dimensionName) {
    Collection<String> idDimension = myDimensions.get(dimensionName);
    if (idDimension == null || idDimension.size() == 0) {
      return null;
    }
    if (idDimension.size() > 1) {
      throw new BadRequestException("Only single '" + dimensionName + "' dimension is supported in locator. Found: " + idDimension);
    }
    String result = idDimension.iterator().next();
    if (StringUtil.isEmpty(result)) {
      throw new BadRequestException("Value is empty for dimension '" + dimensionName + "'.");
    }
    return result;
  }

  @NotNull
  public int getDimensionsCount() {
    return myDimensions.keySet().size();
  }
}
