/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.problem;

import java.util.Objects;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("PublicField")
@XmlType(name = "metadataEntry")
@XmlRootElement(name = "metadataEntry")
public class MetadataEntry {
  @XmlAttribute public String key;
  @XmlAttribute public String type;
  @XmlAttribute public String value;

  public MetadataEntry() {
  }

  public MetadataEntry(final @NotNull String key, @NotNull final String type, final @NotNull String value) {
    this.key = key;
    this.type = type;
    this.value = value;

    // todo: type=text should be considered a default value
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final MetadataEntry that = (MetadataEntry)o;
    return Objects.equals(key, that.key) &&
           Objects.equals(type, that.type) &&
           Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, type, value);
  }

  @Override
  public String toString() {
    return "MetadataEntry{" +
           "key='" + key + '\'' +
           ", type='" + type + '\'' +
           ", value='" + value + '\'' +
           '}';
  }
}
