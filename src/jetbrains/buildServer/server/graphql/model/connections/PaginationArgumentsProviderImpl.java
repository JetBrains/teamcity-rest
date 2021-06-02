/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.server.graphql.model.connections;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class PaginationArgumentsProviderImpl implements PaginationArgumentsProvider {
  private static final Logger LOG = Logger.getInstance(PaginationArgumentsProviderImpl.class);

  @NotNull
  @Override
  public PaginationArguments get(@Nullable Integer first, @Nullable String after, @NotNull FallbackBehaviour fallbackBehaviour) {
    return get(first, after, null, null, fallbackBehaviour);
  }

  @NotNull
  @Override
  public PaginationArguments get(@Nullable Integer first, @Nullable String after, @Nullable Integer last, @Nullable String before, @NotNull FallbackBehaviour fallbackBehaviour) {
    if (after != null) {
      return new PaginationArgumentsProviderImpl.PaginationArgumentsImpl(after, PaginationArgumentsProviderImpl.getIntOrDefault(first), PaginationArguments.Direction.FORWARD);
    }

    if (before != null) {
      return new PaginationArgumentsProviderImpl.PaginationArgumentsImpl(before, PaginationArgumentsProviderImpl.getIntOrDefault(last), PaginationArguments.Direction.BACKWARD);
    }

    if (first != null) {
      return new PaginationArgumentsProviderImpl.PaginationArgumentsImpl(null, first, PaginationArguments.Direction.FORWARD);
    }

    if (last != null) {
      return new PaginationArgumentsProviderImpl.PaginationArgumentsImpl(null, last, PaginationArguments.Direction.BACKWARD);
    }

    return getFallback(fallbackBehaviour);
  }

  private PaginationArguments getFallback(@NotNull FallbackBehaviour fallbackBehaviour) {
    switch (fallbackBehaviour) {
      case RETURN_EVERYTHING:
        return PaginationArguments.everything();
      case RETURN_FIRST_PAGE:
        return getFirstPage();
      default:
        LOG.error(String.format("%s fallback behaviour is not implemented. Will use %s instead.", fallbackBehaviour, FallbackBehaviour.RETURN_FIRST_PAGE));
        return getFirstPage();
    }
  }

  @NotNull
  @Override
  public PaginationArguments getFirstPage() {
    return new PaginationArgumentsImpl(null, DEFAULT_PAGE_SIZE, PaginationArguments.Direction.FORWARD);
  }

  @NotNull
  @Override
  public PaginationArguments getLastPage() {
    return new PaginationArgumentsImpl(null, DEFAULT_PAGE_SIZE, PaginationArguments.Direction.BACKWARD);
  }

  private static int getIntOrDefault(@Nullable Integer val) {
    if(val == null) {
      return PaginationArgumentsProviderImpl.DEFAULT_PAGE_SIZE;
    }

    return val;
  }

  private static class PaginationArgumentsImpl implements PaginationArguments {
    @Nullable
    private final String myCursor;
    private final int myCount;
    @NotNull
    private final Direction myDirection;

    public PaginationArgumentsImpl(@Nullable String afterCursor, int count, @NotNull Direction direction) {
      myCursor = afterCursor;
      myCount = count;
      myDirection = direction;
    }

    @Override
    public String getAfter() {
      return myCursor;
    }

    @Override
    public int getCount() {
      return myCount;
    }

    @NotNull
    @Override
    public Direction getDirection() {
      return myDirection;
    }
  }
}
