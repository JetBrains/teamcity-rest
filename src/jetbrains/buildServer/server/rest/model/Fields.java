package jetbrains.buildServer.server.rest.model;

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 28.11.13
 */
public class Fields {
  public static final String ALL_FIELDS_PATTERN = "*";
  public static final String ALL_NESTED_FIELDS_PATTERN = "**";
  @Nullable private final String myFieldsSpec;

  public static Fields ALL_FIELDS = new Fields(null, ALL_FIELDS_PATTERN);
  public static Fields ALL_NESTED_FIELDS = new Fields(null, ALL_NESTED_FIELDS_PATTERN);

  public Fields() {
    myFieldsSpec = null;
  }

  public Fields (@Nullable String fieldsSpec){
    myFieldsSpec = fieldsSpec;
  }

  public Fields (@Nullable String fieldsSpec, @NotNull String defaultFieldsSpec){
    myFieldsSpec = StringUtil.isEmpty(fieldsSpec) ? defaultFieldsSpec : fieldsSpec;
  }

  public boolean isAllFieldsIncluded(){
    return ALL_FIELDS_PATTERN.equals(myFieldsSpec) || ALL_NESTED_FIELDS_PATTERN.equals(myFieldsSpec);
  }

  public boolean isDefault(){
    return myFieldsSpec == null;
  }

  public boolean isIncluded(final String fieldName){
    if (isAllFieldsIncluded()){
      return true;
    }
    if (myFieldsSpec == null){
      throw new InvalidStateException("Should not call isIncluded for a field, which has default value");
    }
    return myFieldsSpec.contains(fieldName); //todo: implement! This is a hack!
  }

  @NotNull
  public Fields getNestedField(final String nestedFieldName) {
    if (ALL_NESTED_FIELDS_PATTERN.equals(myFieldsSpec)){
      return ALL_NESTED_FIELDS;
    }
    return new Fields(null);
  }
}
