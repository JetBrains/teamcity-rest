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

import com.intellij.util.containers.SortedList;
import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.util.CaseInsensitiveStringComparator;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * @author Yegor.Yarko
 *         Date: 13.07.2009
 */
@XmlRootElement(name = "entries")
@ModelBaseType(ObjectType.LIST)
public class Entries implements DefaultValueAware{
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "entry")
  public List<Entry> entries;

  public Entries() {
  }

  public Entries(@NotNull final java.util.Map<String, String> propertiesP, final @NotNull Fields fields) {
    this.count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), propertiesP.size());
    Fields entryFields = fields.getNestedField("entry");
    if (!entryFields.isNone()) {
      entries = new SortedList<Entry>(new Comparator<Entry>() {
        private final CaseInsensitiveStringComparator comp = new CaseInsensitiveStringComparator();

        public int compare(final Entry o1, final Entry o2) {
          return comp.compare(o1.name, o2.name);
        }
      });
      for (java.util.Map.Entry<String, String> prop : propertiesP.entrySet()) {
        entries.add(new Entry(prop.getKey(), prop.getValue(), entryFields));
      }
    }
  }

  @NotNull
  public java.util.Map<String, String> getMap() {
    if (entries == null) return new HashMap<>();
    final HashMap<String, String> result = new HashMap<String, String>(entries.size());
    for (Entry Entry : entries) {
      result.put(Entry.name, Entry.value);
    }
    return result;
  }

  public boolean isDefault() {
    return ValueWithDefault.isAllDefault(count, entries);
  }
}
