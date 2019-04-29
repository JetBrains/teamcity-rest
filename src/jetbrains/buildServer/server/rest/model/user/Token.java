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

import java.util.Date;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitrii Bogdanov
 */
@SuppressWarnings({"PublicField", "unused", "WeakerAccess"})
@XmlRootElement(name = "token")
@XmlType(name = "token", propOrder = {"name", "value", "creationTime"})
public class Token {
  @XmlAttribute
  public String name;
  @Nullable
  @XmlAttribute(required = false)
  public String value;
  @Nullable
  @XmlAttribute(required = false)
  public Date creationTime;

  public Token() {
  }

  public Token(@NotNull final String name, @Nullable final Date creationTime) {
    this(name, null, creationTime);
  }

  public Token(@NotNull final String name, @Nullable final String value, @Nullable Date creationTime) {
    this.name = name;
    this.value = value;
    this.creationTime = creationTime;
  }
}