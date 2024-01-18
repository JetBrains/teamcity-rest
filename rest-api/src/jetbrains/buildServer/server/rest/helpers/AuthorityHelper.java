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

package jetbrains.buildServer.server.rest.helpers;

import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.auth.AccessChecker;
import jetbrains.buildServer.serverSide.auth.Permission;
import org.jetbrains.annotations.NotNull;

public final class AuthorityHelper {
  private AuthorityHelper() {
  }

  public static void checkGlobalPermission(@NotNull final BeanContext serviceLocator, @NotNull final Permission... permissions) {
    serviceLocator.getSingletonService(AccessChecker.class).checkHasGlobalPermission(permissions);
  }
}