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

package jetbrains.buildServer.server.rest.model.server;

import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.controllers.admin.ServerData;
import jetbrains.buildServer.diagnostic.MemoryUsageMonitor;
import jetbrains.buildServer.diagnostic.StatisticDataProvider;
import jetbrains.buildServer.server.rest.request.ServerRequest;

/**
 * Entity for server statistics.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "statistics")
public class Statistics {
  @XmlAttribute(name = "type")
  public String type;
  @XmlElement(name = "statistic")
  public List<Statistic> statistic;
  @XmlAttribute(name = "count")
  public Integer count;

  public Statistics() {
  }

  public Statistics(final StatisticDataProvider provider, final ServerData serverData) {
    type = provider.getType();
    final String type = provider.getType();
    List<Statistic> preStatistic = provider.getData().entrySet().stream()
        .map(e -> new Statistic(type, e.getKey(), e.getValue(), serverData))
        .collect(Collectors.toList());
    statistic = normalizeStatistics(preStatistic);
    count = statistic.size();
  }

  private List<Statistic> normalizeStatistics(List<Statistic> preStatistic) {
    if (ServerRequest.STATISTIC_MEMORY.equals(type)) {
      return preStatistic;
    }
    final Statistic divide = preStatistic.stream()
        .filter(s -> MemoryUsageMonitor.STAMP_DURATION_KEY.equals(s.id)).findFirst().orElse(null);

    for (Statistic s : preStatistic) {
      if (!MemoryUsageMonitor.GC_USAGE_KEY.equals(s.id) && !MemoryUsageMonitor.JAVA_PROCESS_CPU_USAGE_KEY.equals(s.id)) {
        continue;
      }
      final List<StatisticData> data = s.data;
      if (divide != null) {
        final List<StatisticData> div = divide.data;
        for (int i = 0; i < data.size() && i < div.size(); i++) {
          final StatisticData iish = data.get(i);
          iish.value = (long)(((double)iish.value / div.get(i).value) * 100);
        }
      }
    }

    return preStatistic.stream().filter(s -> !MemoryUsageMonitor.STAMP_DURATION_KEY.equals(s.id)).collect(Collectors.toList());
  }
}
