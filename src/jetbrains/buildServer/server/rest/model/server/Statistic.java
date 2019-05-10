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

import java.util.*;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.controllers.admin.ServerData;
import jetbrains.buildServer.diagnostic.StatisticDataProvider;
import jetbrains.buildServer.server.rest.request.ServerRequest;
import jetbrains.buildServer.util.DiagnosticUtil;

/**
 * Entity for server statistic.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "statistic")
public class Statistic {

  @XmlElement(name = "id")
  public String id;
  @XmlElement(name = "name")
  public String name;
  @XmlElement(name = "data")
  public List<StatisticData> data;
  @XmlElement(name = "data-type")
  public String dataType;
  @XmlElement(name = "max-data-value")
  public Integer maxDataValue;

  public Statistic() {
  }

  public Statistic(
    final String type,
    final String id,
    final Collection<StatisticDataProvider.Data> values,
    final ServerData serverData
  ) {
    this.id = id;
    if (ServerRequest.STATISTIC_MEMORY.equals(type)) {
      calculateMemory(values, serverData);
    } else if (ServerRequest.STATISTIC_CPU.equals(type)) {
      calculateCpu(values);
    }
  }

  private void calculateMemory(final Collection<StatisticDataProvider.Data> values, final ServerData serverData) {
    if (DiagnosticUtil.TC_TOTAL_MEMORY_USAGE_KEY.equals(id)) {
      name = "Total heap";
      maxDataValue = serverData != null ? (int) serverData.getHeapMemoryUsage().getMax() : null;
    } else if ("Perm".equals(id)) {
      name = "Classes";
      maxDataValue = serverData != null && serverData.getPermGenPoolMemoryUsage() != null ? (int) serverData.getPermGenPoolMemoryUsage().getMax() : null;
    } else {
      if (DiagnosticUtil.isPermGenPool(id)) {
        name = "Classes";
      } else {
        name = id;
      }
      maxDataValue = serverData != null && serverData.getMemUsagesMap().get(id) != null ? (int) serverData.getMemUsagesMap().get(id).getMax() : null;
    }

    dataType = "absolute";
    data = values.stream()
       .map(d -> new StatisticData(d.getTimestamp(), (long)d.getValue()))
       .collect(Collectors.toList());
  }

  private void calculateCpu(final Collection<StatisticDataProvider.Data> values) {
    name = ServerRequest.CPU_NAMES.get(id);
    maxDataValue = 100;
    dataType = "percent";
    data = values.stream()
       .map(d -> new StatisticData(d.getTimestamp(), (long)(100 * d.getValue())))
       .collect(Collectors.toList());
  }
}
