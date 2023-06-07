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


import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class DimensionImpl implements Dimension {
  private final String myName;
  private final Supplier<? extends Syntax> mySyntax;
  private final String myDescription;
  private final boolean myHidden;
  private final boolean myRepeatable;

  public DimensionImpl(@NotNull String name, @NotNull Supplier<? extends Syntax> syntax, @Nullable String description, boolean hidden, boolean repeatable) {
    myName = name;
    mySyntax = syntax;
    myDescription = description;
    myHidden = hidden;
    myRepeatable = repeatable;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public Syntax getSyntax() {
    return mySyntax.get();
  }

  @Override
  @Nullable
  public String getDescription() {
    return myDescription;
  }

  @Override
  public boolean isHidden() {
    return myHidden;
  }

  @Override
  public boolean isRepeatable() {
    return myRepeatable;
  }

  @Override
  public String toString() {
    return myName + " " +
           (isHidden() ? "hidden ": "") +
           (isRepeatable() ? "repeatable ": "");
  }
}
