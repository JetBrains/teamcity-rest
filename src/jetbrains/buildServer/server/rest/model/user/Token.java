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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.serverSide.auth.AuthenticationToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.server.rest.model.user.Token.TYPE;

/**
 * @author Dmitrii Bogdanov
 */
@SuppressWarnings({"PublicField", "unused", "WeakerAccess"})
@XmlRootElement(name = TYPE)
@XmlType(name = TYPE, propOrder = {"name", "creationTime", "value"})
public class Token {
  static final String TYPE = "token";
  @XmlAttribute
  public String name;
  @Nullable
  @XmlAttribute(required = false)
  public String creationTime;
  @Nullable
  @XmlAttribute(required = false)
  public String value;

  public Token() {
  }

  public Token(@NotNull final AuthenticationToken t, @NotNull final Fields fields) {
    this(t, null, fields);
  }

  public Token(@NotNull final AuthenticationToken t, @Nullable String tokenValue, @NotNull final Fields fields) {
    if (fields.isIncluded("name", true, true)) {
      name = t.getName();
    }
    if (fields.isIncluded("creationTime", false, true)) {
      creationTime = Util.formatTime(t.getCreationTime());
    }
    if (fields.isIncluded("value", true, true)) {
      value = tokenValue;
    }
  }
}
