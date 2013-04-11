package jetbrains.buildServer.server.rest.data;

/**
 * @author Yegor.Yarko
 *         Date: 10.04.13
 */
public class FilterUtil {
  public static boolean isIncludedByBooleanFilter(final Boolean filterValue, final boolean actualValue) {
    return filterValue == null || (!(filterValue ^ actualValue));
  }
}
