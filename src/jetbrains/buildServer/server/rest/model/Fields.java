package jetbrains.buildServer.server.rest.model;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 28.11.13
 */
public class Fields {
  public static final String ALL_FILEDS = "*";
  @Nullable private final String myFieldsSpec;

  public Fields (@Nullable String fieldsSpec){
    myFieldsSpec = fieldsSpec;
  }

  public Fields (@Nullable String fieldsSpec, @NotNull String defaultFieldsSpec){
    myFieldsSpec = StringUtil.isEmpty(fieldsSpec) ? defaultFieldsSpec : fieldsSpec;
  }

  public boolean isAllFieldsIncluded(){
    return ALL_FILEDS.equals(myFieldsSpec);
  }
}
