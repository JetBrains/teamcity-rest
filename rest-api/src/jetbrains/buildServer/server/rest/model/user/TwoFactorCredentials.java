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

package jetbrains.buildServer.server.rest.model.user;

import java.util.Set;
import java.util.UUID;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "two-factor-credentials")
@XmlType(name = "two-factor-credentials", propOrder = {"secretKey", "uuid", "recoveryKeys"})
@ModelDescription("Represents credentials for two-factor authentication: secret key and set of recovery keys")
public class TwoFactorCredentials {
  @XmlElement
  public String secretKey;

  @XmlElement
  public UUID uuid;

  private TwoFactorRecoveryKeys recoveryKeys;

  public TwoFactorCredentials() {

  }

  public TwoFactorCredentials(@NotNull String secretKey, @NotNull TwoFactorRecoveryKeys recoveryKeys, @NotNull UUID uuid) {
    this.secretKey = secretKey;
    this.recoveryKeys = recoveryKeys;
    this.uuid = uuid;
  }

  @XmlElement
  public Set<String> getRecoveryKeys() {
    return recoveryKeys.recoveryKeys;
  }
}