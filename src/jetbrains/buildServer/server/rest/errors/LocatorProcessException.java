/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.errors;

import jetbrains.buildServer.server.rest.data.Locator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 15.08.2010
 */
public class LocatorProcessException extends RuntimeException {
  @Nullable private Locator myLocator;

  public LocatorProcessException(final String locator, final int index, final String message) {
    super("Bad locator syntax: " + message + ". Details: locator: '" + locator + "', at position " + index);
  }

  public LocatorProcessException(@NotNull final Locator errorWithLocator, @NotNull final String message) {
    super("Error processing locator '" + errorWithLocator.getStringRepresentation() + "': " + message);
    myLocator = new Locator(errorWithLocator); //creating a copy
  }

  public LocatorProcessException(@NotNull final String message, @NotNull final LocatorProcessException cause) {
    super(message + ". Original error: " + cause.getMessage(), cause);
    Locator locator = cause.getLocator();
    if (locator != null) {
      myLocator = new Locator(locator); //creating a copy
    }
  }

  public LocatorProcessException(final String message) {
    super(message);
  }

  public LocatorProcessException(final String message, final Throwable cause) {
    super(message, cause);
  }

  @Nullable
  public Locator getLocator() {
    return myLocator;
  }
}
