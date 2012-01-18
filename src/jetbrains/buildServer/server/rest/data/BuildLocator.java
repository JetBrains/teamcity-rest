package jetbrains.buildServer.server.rest.data;

import org.jetbrains.annotations.Nullable;

/**
 * Specifies build locator.
 * @author Yegor.Yarko
 *         Date: 18.01.12
 */
public class BuildLocator extends Locator {
  public BuildLocator(@Nullable final String locator) {
    super(locator);
  }
}
