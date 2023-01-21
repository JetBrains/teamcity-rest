/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
import java.util.UUID;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.model.user.TwoFactorCredentials;
import jetbrains.buildServer.server.rest.model.user.TwoFactorRecoveryKeys;
import jetbrains.buildServer.serverSide.auth.TwoFactorPasswordGenerator;
import jetbrains.buildServer.serverSide.auth.TwoFactorPasswordManager;
import jetbrains.buildServer.serverSide.auth.impl.TwoFactorConfirmationException;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * Context class for two-factor authentication actions via REST
 *
 * @author Daniil Boger
 */
@JerseyContextSingleton
@Component
public class TwoFactorSecretKeysUpdater {
  @NotNull private final TwoFactorPasswordGenerator myGenerator;
  @NotNull private final TwoFactorPasswordManager myManager;

  public TwoFactorSecretKeysUpdater(@NotNull final TwoFactorPasswordGenerator generator,
                                    @NotNull final TwoFactorPasswordManager manager) {
    myGenerator = generator;
    myManager = manager;
  }

  /**
   * Generates draft credentials to be confirmed for given user and writes them in temporary storage.
   * Returns secret key, recovery keys and UUID for confirmation
   *
   * @param user owner of new credentials
   * @return unconfirmed {@link TwoFactorCredentials}
   */
  @NotNull
  public TwoFactorCredentials generateAndSetDraftCredentials(@NotNull final SUser user) {
    final String generatedKey = myGenerator.generateSecretKey();
    final Set<String> generatedRecoveryKeys = myGenerator.generateRecoveryKeys();
    final UUID uuid = myManager.addDraftCredentials(user, generatedKey, generatedRecoveryKeys);
    return new TwoFactorCredentials(generatedKey, new TwoFactorRecoveryKeys(generatedRecoveryKeys), uuid);
  }

  /**
   * Attempts to confirm two-factor authentication credentials by UUID
   *
   * @param user     user who confirms
   * @param uuid     uuid for temporary credentials lookup
   * @param password 6-digit TOTP password
   * @throws TwoFactorConfirmationException if draft credentials by UUID not found (expired or incorrect UUID), or if provided password is incorrect
   */
  public void confirmCredentials(@NotNull final SUser user, @NotNull final UUID uuid, final int password) throws TwoFactorConfirmationException {
    myManager.confirmSecretKey(user, uuid, password);
  }

  /**
   * Disables two-factor authentication for given user
   *
   * @param user
   */
  public void disable2FA(@NotNull final SUser user) {
    myManager.disable2FA(user);
  }

  /**
   * Generates and writes new recovery keys for given user. Old keys are discarded
   *
   * @param user
   * @return set of new recovery keys
   */
  @NotNull
  public Set<String> generateAndSetRecoveryKeys(@NotNull final SUser user) {
    final Set<String> generatedKeys = myGenerator.generateRecoveryKeys();
    myManager.setRecoveryKeys(user, generatedKeys);
    return generatedKeys;
  }

  /**
   * Refreshes grace period for given user.
   *
   * @param user
   */
  public void refreshGracePeriod(@NotNull final SUser user) {
    myManager.refreshGracePeriod(user);
  }

  /**
   * Checks if user has enabled 2FA
   *
   * @param user
   * @return true if 2FA is enabled, false otherwise
   */
  public boolean hasEnabled2FA(@NotNull final User user) {
    return myManager.hasEnabled2FA(user);
  }

}
