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


import org.jetbrains.annotations.NotNull;

/**
 * Describes a syntax of the full locator or one of it's {@link Dimension}s.
 * May represent a simple or a complex value, see {@link PlainValue} and {@link SubDimensionSyntax}.
 */
public interface Syntax {
  String getFormat();

  static NamedLocator forLocator(@NotNull String locatorName) {
    return new NamedLocator(locatorName);
  }

  static TODO TODO(@NotNull String msg) {
    return new TODO(msg);
  }

  class TODO implements Syntax {
    private final String myFormatDescription;

    public TODO(@NotNull String formatDescription) {
      myFormatDescription = formatDescription;
    }

    @Override
    public String getFormat() {
      return myFormatDescription;
    }
  }

  class NamedLocator implements Syntax {
    private final String myLocatorName;
    NamedLocator(@NotNull String locatorName) {
      myLocatorName = locatorName;
    }

    @Override
    public String getFormat() {
      return myLocatorName;
    }
  }
}
