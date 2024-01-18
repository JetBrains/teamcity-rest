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

package jetbrains.buildServer.server.rest.model.build;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.data.finder.impl.UserFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.TagData;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 13.07.2009
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "tags")
@ModelBaseType(ObjectType.LIST)
public class Tags implements DefaultValueAware {
  @XmlAttribute public Integer count;

  @XmlElement(name = "tag")
  public List<Tag> tags;

  public Tags() {
  }

  public Tags(final @NotNull List <TagData> tagData, final @NotNull Fields fields, final @NotNull BeanContext beanContext) {
    tags = ValueWithDefault.decideDefault(fields.isIncluded("tag", false), new ValueWithDefault.Value<List<Tag>>() {
      @Nullable
      public List<Tag> get() {
        return CollectionsUtil.convertCollection(tagData, new Converter<Tag, TagData>() {
              public Tag createFrom(@NotNull final TagData source) {
                return new Tag(source.getLabel(), source.getOwner(), fields.getNestedField("tag", Fields.NONE, Fields.LONG), beanContext);
              }
            });
      }
    });

    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), tagData.size());
  }

  public boolean isDefault() {
    return ValueWithDefault.isAllDefault(count, tags);
  }

  @NotNull
  public List<TagData> getFromPosted(final @NotNull UserFinder userFinder) {
    if (tags == null) {
      return new ArrayList<TagData>();
    }
    final ArrayList<TagData> result = new ArrayList<TagData>(tags.size());
    for (Tag item : tags) {
      result.add(item.getFromPosted(userFinder));
    }
    return result;
  }
}