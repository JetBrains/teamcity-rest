package jetbrains.buildServer.server.rest.model;

import com.intellij.util.containers.HashSet;
import java.util.Collection;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 28.11.13
 */
public class Fields {
  private static final String NONE_FIELDS_PATTERN = "";
  private static final String ALL_FIELDS_PATTERN = "*";
  private static final String ALL_NESTED_FIELDS_PATTERN = "**";

  public static final Fields DEFAULT_FIELDS = new Fields(null);
  public static final Fields NONE_FIELDS = new Fields(NONE_FIELDS_PATTERN);
  public static final Fields ALL_FIELDS = new Fields(ALL_FIELDS_PATTERN);
  public static final Fields ALL_NESTED_FIELDS = new Fields(ALL_NESTED_FIELDS_PATTERN);

  @Nullable private final String myFieldsSpec;
  @NotNull private final Set<String> myExcludedFields;

  private Fields(@Nullable String actualFieldsSpec, @Nullable Collection<String> excludedFields, boolean isInternal) {
    myFieldsSpec = actualFieldsSpec;
    myExcludedFields = excludedFields != null ? new HashSet<String>(excludedFields) : new HashSet<String>();
  }

  public Fields() {
    this(null, null, true);
  }

  public Fields (@Nullable String fieldsSpec){
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

  public boolean isDefault(){
    return myFieldsSpec == null;
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
    if (isNone()){
      return false;
    }
    if (myExcludedFields.contains(fieldName)) {
      return false;
    }

    if (isAllFieldsIncluded()){
      return true;
    }
    if (myFieldsSpec == null){
      return null;
    }

    return myFieldsSpec.contains(fieldName); //todo: implement! This is a hack!
  }

  public boolean isIncluded(@NotNull final String fieldName, final boolean defaultValue){
    if (isNone()){
      return false;
    }
    if (myExcludedFields.contains(fieldName)) {
      return false;
    }

    if (isAllFieldsIncluded()){
      return true;
    }
    if (myFieldsSpec == null){
      return defaultValue;
    }

    return myFieldsSpec.contains(fieldName); //todo: implement! This is a hack!
  }

  @NotNull
  public Fields getNestedField(final String nestedFieldName) {
    if (NONE_FIELDS_PATTERN.equals(myFieldsSpec)) {
      return NONE_FIELDS;
//      throw new OperationException("Should never get nested field for NONE fileds filter. Querying for nested field '" + nestedFieldName + "'. Excluded fields: " + myExcludedFields);
    }

    if (myExcludedFields.contains(nestedFieldName)) {
      return NONE_FIELDS;
    }

    final HashSet<String> excludedFields = new HashSet<String>(myExcludedFields);
    excludedFields.add(nestedFieldName);

    if (ALL_NESTED_FIELDS_PATTERN.equals(myFieldsSpec)) {
      return new Fields(ALL_NESTED_FIELDS_PATTERN, excludedFields, true);
    }
    return new Fields(null, excludedFields, true);
  }
}
