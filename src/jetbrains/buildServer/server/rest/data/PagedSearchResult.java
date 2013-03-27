package jetbrains.buildServer.server.rest.data;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 26.03.13
 */
public class PagedSearchResult<T> {
  @NotNull public final List<T> myEntries;
  @Nullable public final Long myStart;
  @Nullable public final Integer myCount;

  public PagedSearchResult(@NotNull final List<T> entries, @Nullable final Long start, @Nullable final Integer count) {
    myEntries = entries;
    myStart = start;
    myCount = count;
  }
}
