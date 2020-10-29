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

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.auth.AuthenticationToken;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitrii Bogdanov
 */
@SuppressWarnings({"PublicField", "unused"})
@XmlRootElement(name = Tokens.TYPE)
@XmlType(name = Tokens.TYPE)
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_BASE_TYPE, value = ObjectType.LIST))
public class Tokens {
  static final String TYPE = "tokens";
  @XmlAttribute
  public Integer count;
  @XmlElement(name = Token.TYPE)
  public List<Token> tokens;

  public Tokens() {
  }

  public Tokens(@NotNull final List<AuthenticationToken> tokenNames, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    if (fields.isIncluded(Token.TYPE, false, true)) {
      tokens = tokenNames.stream().map(token -> new Token(token, fields.getNestedField(Token.TYPE), beanContext)).collect(Collectors.toList());
    }
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), tokenNames.size());
  }
}
