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

import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.auth.AuthenticationToken;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitrii Bogdanov
 */
@SuppressWarnings({"PublicField", "WeakerAccess", "unused"})
@XmlRootElement(name = "tokens")
@XmlType(name = "tokens")
public class Tokens {
  @XmlAttribute
  public Integer count;
  @XmlElement(name = "token")
  public List<Token> tokens;

  public Tokens() {
  }

  public Tokens(@NotNull final List<AuthenticationToken> tokenNames, @NotNull final Fields fields) {
    if (fields.isIncluded("token", false, true)) {
      tokens = tokenNames.stream().map(Token::new).collect(Collectors.toList());
    }
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), tokenNames.size());
  }
}
