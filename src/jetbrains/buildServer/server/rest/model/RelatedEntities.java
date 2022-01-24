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

package jetbrains.buildServer.server.rest.model;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Yegor.Yarko
 * Date: 20/04/2019
 */
@XmlRootElement(name = "relatedEntities")
@XmlType(name = "relatedEntities")
@ModelBaseType(
    value = ObjectType.LIST,
    baseEntity = "RelatedEntity"
)
public class RelatedEntities {
  @XmlElement(name = "entity")
  public List<RelatedEntity> entities;

  @XmlAttribute
  public Integer count;

  public RelatedEntities() {
  }

  public RelatedEntities(final @NotNull List<RelatedEntity.Entity> items, @NotNull final Fields fields, @NotNull final BeanContext context) {
    entities = ValueWithDefault.decideDefault(fields.isIncluded("entity", false, true),
                                                 () -> items.stream().map(i -> new RelatedEntity(i, fields.getNestedField("entity", Fields.NONE, Fields.LONG), context)).collect(Collectors.toList()));

    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), () -> items.size());
  }
}
