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

package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.server.rest.data.finder.FinderImpl;
import jetbrains.buildServer.server.rest.data.locator.Dimension;
import jetbrains.buildServer.server.rest.data.locator.StubDimension;
import jetbrains.buildServer.server.rest.model.PagerData;
import org.jetbrains.annotations.NotNull;

public final class PagerDimensions {
  @NotNull
  private static final Dimension COUNT = new StubDimension(count());
  @NotNull
  private static final Dimension START = new StubDimension(start());
  @NotNull
  private static final Dimension LOOKUP_LIMIT = new StubDimension(lookupLimit());

  @NotNull
  public static Dimension dimensionCount() {
    return COUNT;
  }

  @NotNull
  public static Dimension dimensionStart() {
    return START;
  }

  @NotNull
  public static Dimension dimensionLookupLimit() {
    return LOOKUP_LIMIT;
  }

  @NotNull
  public static String start() {
    return PagerData.START;
  }

  @NotNull
  public static String count() {
    return PagerData.COUNT;
  }

  @NotNull
  public static String lookupLimit() {
    return FinderImpl.DIMENSION_LOOKUP_LIMIT;
  }
}
