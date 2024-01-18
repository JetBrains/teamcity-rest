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
import jetbrains.buildServer.server.rest.data.locator.definition.LocatorDefinition;
import org.jetbrains.annotations.NotNull;

public class DimensionBuilder {
  private final String myName;
  private boolean myRepeatable = false;
  private boolean myHidden = false;
  private Supplier<? extends Syntax> mySyntax;
  private String myDescription = null;

  DimensionBuilder(@NotNull String name) {
    myName = name;
  }

  public DimensionBuilder description(@NotNull String description) {
    myDescription = description;
    return this;
  }

  public DimensionBuilder repeatable() {
    myRepeatable = true;
    return this;
  }

  public DimensionBuilder hidden() {
    myHidden = true;
    return this;
  }

  public DimensionBuilder syntax(@NotNull Supplier<? extends Syntax> syntax) {
    mySyntax = syntax;
    return this;
  }

  public DimensionBuilder syntax(@NotNull Syntax syntax) {
    mySyntax = () -> syntax;
    return this;
  }

  public DimensionBuilder dimensions(@NotNull Class<? extends LocatorDefinition> dimensions) {
    mySyntax = () -> new SubDimensionSyntaxImpl(dimensions);
    return this;
  }

  public Dimension build() {
    if(mySyntax == null) {
      if(myHidden) {
        mySyntax = () -> Syntax.TODO("");
      } else {
        throw new InvalidConfigurationException("Syntax is undefined for dimension '" + myName + '"');
      }
    }
    return new DimensionImpl(myName, mySyntax, myDescription, myHidden, myRepeatable);
  }

  public static class InvalidConfigurationException extends RuntimeException {
    public InvalidConfigurationException(String message) {
      super(message);
    }
  }
}
