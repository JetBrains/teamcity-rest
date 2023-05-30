/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.locator;

import jetbrains.buildServer.server.rest.data.Locator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StubDimension implements Dimension {
  private final String myName;
  private final Syntax mySyntax = Syntax.TODO("Unknown syntax");

  public StubDimension(@NotNull String name) {
    myName = name;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  public static StubDimension single() {
    return new StubDimension(Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
  }

  @NotNull
  @Override
  public Syntax getSyntax() {
    return mySyntax;
  }

  @Nullable
  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public boolean isHidden() {
    return false;
  }

  @Override
  public boolean isRepeatable() {
    return false;
  }
}
