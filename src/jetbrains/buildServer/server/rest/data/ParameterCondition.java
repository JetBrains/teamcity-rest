package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 17.01.12
 */
public class ParameterCondition {

  @NotNull private final String myParameterName;
  private final String myParameterValue;

  public ParameterCondition(@NotNull final String name, final String value) {
    myParameterName = name;
    myParameterValue = value;
  }

  public static ParameterCondition create(@Nullable final String propertyConditionLocator) {
    if (propertyConditionLocator == null){
      return null;
    }
    final Locator locator = new Locator(propertyConditionLocator);
    final String name = locator.getSingleDimensionValue("name");
    if (StringUtil.isEmpty(name)){
      throw new BadRequestException("Property name should not be empty in dimension 'name' of the locator : '" + propertyConditionLocator + "'");
    }
    //noinspection ConstantConditions
    return new ParameterCondition(name, locator.getSingleDimensionValue("value"));
  }

  public boolean matches(@NotNull final SBuild build) {
    final String value = build.getParametersProvider().get(myParameterName);
    if (StringUtil.isEmpty(myParameterValue)) {
      return true;
    } else {
      return !StringUtil.isEmpty(value) && value.contains(myParameterValue);
    }
  }

  @Override
  public String toString() {
    final StringBuilder result = new StringBuilder();
    result.append("Parameter condition (");
    result.append("name:").append(myParameterName).append(", ");
    if (myParameterValue!= null) result.append("value:").append(myParameterValue).append(", ");
    result.append(")");
    return result.toString();
  }
}
