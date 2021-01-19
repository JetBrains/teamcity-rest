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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.metrics.MetricId;
import jetbrains.buildServer.metrics.MetricValue;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 16.11.2009
 */
@XmlRootElement(name = "metric")
@XmlType(propOrder = {"name", "description", "prometheusName", "metricValues", "metricTags"})
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_DESCRIPTION, value = "Represents the specific server metric." +
"\n\nRelated Help article: [Metrics](https://www.jetbrains.com/help/teamcity/teamcity-monitoring-and-diagnostics.html#Metrics)"))
public class Metric {
  private MetricValue myMetricValue;
  private Fields myFields;

  public Metric() {
  }

  public Metric(final MetricValue metricValue, @NotNull Fields fields) {
    myMetricValue = metricValue;
    myFields = fields;
  }

  @XmlAttribute
  public String getName() {
    return ValueWithDefault.decideIncludeByDefault(myFields.isIncluded("name"), metricId().getName());
  }

  @XmlAttribute
  public String getDescription() {
    return ValueWithDefault.decideIncludeByDefault(myFields.isIncluded("description"), metricId().getDescription());
  }

  @XmlAttribute
  public String getPrometheusName() {
    return ValueWithDefault.decideIncludeByDefault(myFields.isIncluded("prometheusName"), metricId().getPrometheusName());
  }

  @XmlElement
  public MetricTags getMetricTags() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("metricTags"), () -> {
      final Map<String, String> tags = metricId().getTags();
      final List<MetricTag> result = new ArrayList<>();
      for (String name : tags.keySet()) {
        result.add(new MetricTag(name, tags.get(name)));
      }
      return new MetricTags(result, myFields);
    });
  }

  @XmlElement
  public MetricValues getMetricValues() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("metricValues"), () -> {
      final List<jetbrains.buildServer.server.rest.model.metrics.MetricValue>result = new ArrayList<>();
      final Map<String, Double> values = myMetricValue.getValues();
      for (String key : values.keySet()) {
        result.add(new jetbrains.buildServer.server.rest.model.metrics.MetricValue(key, values.get(key)));
      }
      return new MetricValues(result, myFields);
    });
  }

  private MetricId metricId() {
    return myMetricValue.getMetricId();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Metric metric = (Metric)o;
    return Objects.equals(myMetricValue, metric.myMetricValue) &&
           Objects.equals(myFields, metric.myFields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myMetricValue, myFields);
  }
}
