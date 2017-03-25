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

package jetbrains.buildServer.server.rest.model;

import com.intellij.util.containers.SortedList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.util.CaseInsensitiveStringComparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 13.07.2009
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "datas")
public class NamedDatas implements DefaultValueAware{
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "data")
  public List<NamedData> entries = new SortedList<NamedData>(new Comparator<NamedData>() {
    private final CaseInsensitiveStringComparator comp = new CaseInsensitiveStringComparator();

    public int compare(final NamedData o1, final NamedData o2) {
      return comp.compare(o1.id, o2.id);
    }
  });

  public NamedDatas() {
  }

  public NamedDatas(@NotNull final Map<String, Map<String, String>> properties, @NotNull final Fields fields) {
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), properties.size());
    entries = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("data"), new ValueWithDefault.Value<List<NamedData>>() {
      @Nullable
      @Override
      public List<NamedData> get() {
        ArrayList<NamedData> result = new ArrayList<>(properties.size());
        for (java.util.Map.Entry<String, Map<String, String>> prop : properties.entrySet()) {
          result.add(new NamedData(prop.getKey(), prop.getValue(), fields.getNestedField("data")));
        }
        return result;
      }
    });
  }

  public boolean isDefault() {
    return ValueWithDefault.isAllDefault(count, entries);
  }
}
