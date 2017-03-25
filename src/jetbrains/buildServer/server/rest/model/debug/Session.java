/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.debug;

import java.util.Date;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.user.User;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 24/12/2015
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "session")
@XmlType(name = "session")
public class Session {
  @XmlAttribute
  public String id;

  @XmlAttribute
  public String creationDate;

  @XmlAttribute
  public String lastAccessedDate;

  @XmlElement
  public User user;

  public Session() {
  }

  public Session(@NotNull final String id, @Nullable final Long userId, @Nullable final Date creationDate, @Nullable final Date lastAccessedDate,
                 @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    this.id = ValueWithDefault.decideDefault(fields.isIncluded("id", false), id);
    this.creationDate = ValueWithDefault.decideDefault(fields.isIncluded("creationDate", false), Util.formatTime(creationDate));
    this.lastAccessedDate = ValueWithDefault.decideDefault(fields.isIncluded("lastAccessedDate", false), Util.formatTime(lastAccessedDate));
    this.user = userId == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("user", false), new User(userId, fields.getNestedField("user"), beanContext));
  }
}