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

package jetbrains.buildServer.server.graphql.model;

import jetbrains.buildServer.controllers.agent.OSKind;
import org.jetbrains.annotations.NotNull;

public enum OSType {
  Windows,
  macOS,
  Linux,
  Solaris,
  FreeBSD,
  Unix,
  Unknown;

  @NotNull
  public static OSType guessByName(@NotNull String osName) {
    OSKind osKind = OSKind.guessByName(osName);

    if (osKind == null) {
      return Unknown;
    }

    switch (osKind) {
      case WINDOWS:
        return Windows;
      case MAC:
        return macOS;
      case LINUX:
        return Linux;
      case SOLARIS:
        return Solaris;
      case FREEBSD:
        return FreeBSD;
      case OTHERUNIX:
        return Unix;
      default:
        return Unknown;
    }
  }
}
