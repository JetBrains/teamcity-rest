package jetbrains.buildServer.server.rest.data;

/**
 * @author Yegor.Yarko
 *         Date: 27.07.13
 */

import org.jetbrains.annotations.NotNull;

public interface FilterConditionChecker<T> {

  boolean isIncluded(@NotNull T item);
}
