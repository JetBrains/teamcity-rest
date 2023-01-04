/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.util;

import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 * @since 24.01.14
 */
public abstract class CachingValue<S> {
  private S myValue;

  @NotNull
  final public S get() {
    if (myValue == null) {
      myValue = doGet();
    }
    return myValue;
  }

  public boolean isCached() {
    return myValue != null;
  }

  /**
   * Should not return null
   *
   * @return calculates the value to be cached and used.
   */
  @NotNull
  protected abstract S doGet();

  @NotNull
  public static <S> CachingValue<S> simple(@NotNull final S value) {
    return new CachingValue<S>() {
      @NotNull
      @Override
      protected S doGet() {
        return value;
      }

      @Override
      public boolean isCached() {
        return true;
      }
    };
  }

  @NotNull
  public static <S> CachingValue<S> simple(@NotNull Supplier<S> value) {
    return new CachingValue<S>() {
      @NotNull
      @Override
      protected S doGet() {
        return value.get();
      }
    };
  }
}

