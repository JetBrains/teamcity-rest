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
import java.util.Map;
import jetbrains.buildServer.metrics.MetricValueKey;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;


@XmlRootElement(name = "metricValues")
@ModelBaseType(ObjectType.LIST)
public class MetricValues implements DefaultValueAware {
  @XmlAttribute public Integer count;

  @XmlElement(name = "metricValue")
  public List<MetricValue> values;

  public MetricValues() {
  }

  public MetricValues(@NotNull Map<MetricValueKey, Double> valuesMap, final @NotNull Fields fields) {
    values = ValueWithDefault.decideDefault(
      fields.isIncluded("metricValues", true),
      () -> {
        Fields nestedFields = fields.getNestedField("metricValues");
        final List<jetbrains.buildServer.server.rest.model.metrics.MetricValue> result = new ArrayList<>();
        for (MetricValueKey key : valuesMap.keySet()) {
          result.add(new jetbrains.buildServer.server.rest.model.metrics.MetricValue(key, valuesMap.get(key), nestedFields));
        }
        return result;
      }
    );

    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), values.size());
  }

  public boolean isDefault() {
    return ValueWithDefault.isAllDefault(count, values);
  }
}