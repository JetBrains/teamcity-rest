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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.util.CaseInsensitiveStringComparator;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 * Date: 13.07.2009
 */
@XmlRootElement(name = "datas")
@ModelBaseType(
  value = ObjectType.LIST,
  baseEntity = "MetaData"
)
public class NamedDatas implements DefaultValueAware {
  private Integer count;

  private List<NamedData> entries = Collections.emptyList();

  public NamedDatas() {
  }


  public NamedDatas(@NotNull final Map<String, Map<String, String>> properties, @NotNull final Fields fields) {
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), properties.size());

    entries = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("data"), () -> {
      Fields dataFields = fields.getNestedField("data");
      return properties.entrySet().stream()
                       .map(prop -> new NamedData(prop.getKey(), prop.getValue(), dataFields))
                       .sorted(Comparator.comparing(it -> it.id, CaseInsensitiveStringComparator.INSTANCE))
                       .collect(Collectors.toList());
    });
  }

  @XmlAttribute
  public Integer getCount() {
    return count;
  }

  public void setCount(Integer count) {
    this.count = count;
  }

  @XmlElement(name = "data")
  public List<NamedData> getEntries() {
    return entries;
  }

  public void setEntries(List<NamedData> entries) {
    this.entries = entries;
  }

  public boolean isDefault() {
    return ValueWithDefault.isAllDefault(count, entries);
  }
}