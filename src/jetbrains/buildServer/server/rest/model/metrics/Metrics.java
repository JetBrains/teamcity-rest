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

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.metrics.MetricValue;
import jetbrains.buildServer.metrics.ServerMetricsReader;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@SuppressWarnings("PublicField")
@XmlRootElement(name = "metrics")
@ModelBaseType(ObjectType.LIST)
public class Metrics {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "metric")
  public List<Metric> metrics;

  public Metrics() {
  }

  public Metrics(@NotNull final Fields fields, @NotNull final ServerMetricsReader metricsReader) {

    final Boolean experimental = fields.isIncluded("experimental", false, false);
    final List<MetricValue> metricValues = metricsReader.queryBuilder().withExperimental(experimental == null || experimental).build();
    
    metrics = ValueWithDefault.decideDefault(fields.isIncluded("metric", true, true), () -> {
      return CollectionsUtil
        .convertCollection(metricValues, new Converter<Metric, MetricValue>() {
          public Metric createFrom(@NotNull final MetricValue source) {
            return new Metric(source,
                              fields.getNestedField("metric", Fields.SHORT, Fields.LONG)
            );
          }
        });
    });

    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), metricValues.size());
  }
}
