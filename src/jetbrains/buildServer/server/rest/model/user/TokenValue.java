/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import javax.xml.bind.annotation.*;
import jetbrains.buildServer.serverSide.auth.AuthenticationToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitrii Bogdanov
 */
@SuppressWarnings({"unused", "PublicField"})
@XmlRootElement(name = "tokenValue")
@XmlType(name = "tokenValue", propOrder = {"value"})
public class TokenValue extends Token {
  @Nullable
  @XmlAttribute(required = false)
  public String value;

  public TokenValue() {
    super();
  }

  public TokenValue(@NotNull final AuthenticationToken t) {
    super(t);
    value = t.getValue();
  }
}
