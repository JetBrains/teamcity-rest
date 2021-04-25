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

import org.jetbrains.annotations.NotNull;

public class OS {
  @NotNull
  private final String myName;
  @NotNull
  private final String myVersion;

  public OS(@NotNull String name, @NotNull String version) {
    myName = name;
    myVersion = version;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getVersion() {
    return myVersion;
  }
}