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

package jetbrains.buildServer.server.rest.data.change;

import java.util.Collection;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

public class CommiterData {
  @NotNull
  private final String myVcsUsername;
  @NotNull
  private final Collection<SUser> myUsers;

  public CommiterData(@NotNull String vcsUsername, @NotNull Collection<SUser> users) {
    myVcsUsername = vcsUsername;
    myUsers = users;
  }

  @NotNull
  public String getVcsUsername() {
    return myVcsUsername;
  }

  @NotNull
  public Collection<SUser> getUsers() {
    return myUsers;
  }
}
