/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class is not thread-safe
 * See also CachingValue class
 * @author Yegor.Yarko
 * Date: 19.02.2020
 */
public abstract class CachingValueNullable<S> implements ValueWithDefault.Value<S> {
  private S myValue;
  private boolean myInitialized = false;

  @Override
  @Nullable
  public S get() {
    if (!myInitialized) {
      myValue = doGet();
      myInitialized = true;
    }
    return myValue;
  }

  public boolean isCached() {
    return myInitialized;
  }

  @Nullable
  protected abstract S doGet();

  @NotNull
  public static <S> CachingValueNullable<S> simple(@Nullable final S value) {
    return new CachingValueNullable<S>() {
      @Override public S get() { return value; }
      @Override protected S doGet() { return value;}
      @Override public boolean isCached() { return true;}
    };
  }

  @NotNull
  public static <S> CachingValueNullable<S> simple(@NotNull final ValueWithDefault.Value<S> value) {
    return new CachingValueNullable<S>() {
      @Override protected S doGet() { return value.get(); }
    };
  }
}