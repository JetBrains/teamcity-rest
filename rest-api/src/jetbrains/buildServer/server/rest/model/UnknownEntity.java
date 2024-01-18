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

package jetbrains.buildServer.server.rest.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 * Date: 20/04/2019
 */
@XmlRootElement(name = "unknown")
public class UnknownEntity {
  @XmlAttribute(name = "internalId")
  private String internalId;

  @XmlAttribute(name = "type")
  private String type;

  @SuppressWarnings("unused")
  public UnknownEntity() {
  }

  public UnknownEntity(@Nullable final String internalId, @Nullable final String type, @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    this.internalId = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("internalId"), internalId);
    this.type = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("type"), type);
  }
}