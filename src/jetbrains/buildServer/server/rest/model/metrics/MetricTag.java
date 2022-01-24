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

package jetbrains.buildServer.server.rest.model.metrics;

import java.util.Objects;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "metricTag")
@XmlType(name = "metricTag", propOrder = {"name", "value"})
@ModelDescription(
    value = "Represents a metric tag.",
    externalArticleLink = "https://www.jetbrains.com/help/teamcity/teamcity-monitoring-and-diagnostics.html#Metrics",
    externalArticleName = "Metrics"
)
public class MetricTag {
  @XmlAttribute
  public String name;
  @XmlAttribute
  public String value;

  public MetricTag() {
  }

  public MetricTag(@NotNull String name, @NotNull String value) {
    this.name = name;
    this.value = value;
  }

  @Override
  public String toString() {
    return "Tag{" +
           "name='" + name + '\'' +
           ", value='" + value + '\'' +
           '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final MetricTag tag = (MetricTag)o;
    return Objects.equals(name, tag.name) &&
           Objects.equals(value, tag.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, value);
  }
}

