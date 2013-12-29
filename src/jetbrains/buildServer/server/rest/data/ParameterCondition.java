package jetbrains.buildServer.server.rest.data;

import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 17.01.12
 */
public class ParameterCondition {

  public static final String NAME = "name";
  public static final String VALUE = "value";
  public static final String TYPE = "matchType";
  @NotNull private final String myParameterName;
  @Nullable private final String myParameterValue;
  @NotNull private final RequirementType myRequirementType;

  public ParameterCondition(@NotNull final String name, @Nullable final String value, final @NotNull RequirementType requirementType) {
    myParameterName = name;
    myParameterValue = value;
    myRequirementType = requirementType;
  }

  public static ParameterCondition create(@Nullable final String propertyConditionLocator) {
    if (propertyConditionLocator == null){
      return null;
    }
    final Locator locator = new Locator(propertyConditionLocator, NAME, VALUE, TYPE);

    final String name = locator.getSingleDimensionValue(NAME);
    if (StringUtil.isEmpty(name)){
      throw new BadRequestException("Property name should not be empty in dimension 'name' of the locator : '" + propertyConditionLocator + "'");
    }

    final String value = locator.getSingleDimensionValue(VALUE);

    RequirementType requirement = value != null ? RequirementType.CONTAINS : RequirementType.EXISTS;

    final String type = locator.getSingleDimensionValue(TYPE);
    if (type != null){
      requirement = RequirementType.findByName(type);
      if (requirement == null){
        throw new BadRequestException("Unsupported parameter match type. Supported are: " + getAllRequirementTypes());
      }
    }
    final ParameterCondition result = new ParameterCondition(name, value, requirement);
    locator.checkLocatorFullyProcessed();
    return result;
  }

  public static List<String> getAllRequirementTypes() {
    return CollectionsUtil.convertCollection(RequirementType.ALL_REQUIREMENT_TYPES, new Converter<String, RequirementType>() {
      public String createFrom(@NotNull final RequirementType source) {
        return source.getName();
      }
    });
  }

  public boolean matches(final ParametersProvider parametersProvider) {
    final String value = parametersProvider.get(myParameterName);
    return myRequirementType.match(new Requirement(myParameterName, myParameterValue, myRequirementType), Collections.singletonMap(myParameterName, value), false);
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
