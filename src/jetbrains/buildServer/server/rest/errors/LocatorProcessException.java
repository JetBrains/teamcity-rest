package jetbrains.buildServer.server.rest.errors;

/**
 * @author Yegor.Yarko
 *         Date: 15.08.2010
 */
public class LocatorProcessException extends RuntimeException {
  public LocatorProcessException(final String locator, final int index, final String message) {
    super("Bad locator syntax: " + message + ". Details: locator: '" + locator + "', at position " + index);
  }

  public LocatorProcessException(final String message) {
    super(message);
  }
}
