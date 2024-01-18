package jetbrains.buildServer.server.rest.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a single page, when next and previous pages are known to be absent or these concepts make no sense.
 * <br/>
 * One example is when we have a link in the inner field, e.g. <code>builds.build.compatibleAgents.href</code>.
 * In such cases, there is no point in providing link to "second page of compatible agents" for each build, so <code>nextHref</code>
 * does not really make sense.
 */
public class SinglePagePagerData implements PagerData {
  private final String myHref;

  public SinglePagePagerData(@NotNull String href) {
    myHref = href;
  }

  @NotNull
  @Override
  public String getHref() {
    return myHref;
  }

  @Nullable
  @Override
  public String getNextHref() {
    return null;
  }

  @Nullable
  @Override
  public String getPrevHref() {
    return null;
  }
}
