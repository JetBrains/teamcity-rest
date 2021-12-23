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

package jetbrains.buildServer.server.rest.model.metrics;

import java.util.Objects;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.metrics.MetricValueKey;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "metricValue")
@XmlType(name = "metricValue", propOrder = {"name", "value", "tags"})
@ModelDescription(
    value = "Represents a metric value.",
    externalArticleLink = "https://www.jetbrains.com/help/teamcity/teamcity-monitoring-and-diagnostics.html#Metrics",
    externalArticleName = "Metrics"
)
public class MetricValue {
  private Fields myFields;
  private double myValue;
  private MetricValueKey myName;

  public MetricValue() {
  }

  public MetricValue(@NotNull MetricValueKey name, double value, @NotNull Fields fields) {
    myName = name;
    myValue = value;
    myFields = fields;
  }

  @XmlAttribute(name = "name")
  public String getName() {
    return myName.getName();
  }

  @XmlAttribute(name = "value")
  public Double getValue() {
    return myValue;
  }

  @XmlElement(name = "tags")
  public MetricTags getTags() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("tags", false),
      () -> new MetricTags(myName.getAdditionalTags(), myFields.getNestedField("tags"))
    );
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("MetricValue{")
           .append("name='").append(myName).append("', ")
           .append("value='").append(myValue).append("', ")
           .append("tags=(");

    myName.getAdditionalTags().entrySet().forEach(e -> {
      builder.append(String.format("'%s'='%s',", e.getKey(), e.getValue()));
    });

    return builder.append(") }").toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final MetricValue tag = (MetricValue)o;
    return Objects.equals(myName, tag.myName) &&
           Objects.equals(myValue, tag.myValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myName, myValue);
  }
}

