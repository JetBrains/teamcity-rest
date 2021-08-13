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

package jetbrains.buildServer.server.rest.data;

import java.util.Set;
import jetbrains.buildServer.serverSide.auth.TwoFactorPasswordGenerator;
import jetbrains.buildServer.serverSide.auth.TwoFactorPasswordManager;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;

public class TwoFactorSecretKeysUpdater {
  @NotNull private final TwoFactorPasswordGenerator myGenerator;
  @NotNull private final TwoFactorPasswordManager myManager;

  public TwoFactorSecretKeysUpdater(@NotNull final TwoFactorPasswordGenerator generator,
                                    @NotNull final TwoFactorPasswordManager manager) {
    myGenerator = generator;
    myManager = manager;
  }

  @NotNull
  public String generateAndSetSecretKey(@NotNull final SUser user) {
    final String generatedKey = myGenerator.generateSecretKey();
    myManager.setSecretKey(user, generatedKey);
    return generatedKey;
  }

  public void delete2FA(@NotNull final SUser user) {
    myManager.disable2FA(user);
  }

  @NotNull
  public Set<String> generateAndSetRecoveryKeys(@NotNull final SUser user) {
    final Set<String> generatedKeys = myGenerator.generateRecoveryKeys();
    myManager.setRecoveryKeys(user, generatedKeys);
    return generatedKeys;
  }

  public boolean hasSetUp2FA(@NotNull final User user) {
    return myManager.hasEnabled2FA(user);
  }

}
