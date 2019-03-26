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

package jetbrains.buildServer.server.rest.request;

import io.swagger.annotations.Api;
import java.lang.management.MemoryUsage;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.controllers.admin.ServerData;
import jetbrains.buildServer.diagnostic.MemoryUsageMonitor;
import jetbrains.buildServer.diagnostic.StatisticDataProvider;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.diagnostic.Axis;
import jetbrains.buildServer.server.rest.model.diagnostic.Graphic;
import jetbrains.buildServer.server.rest.model.diagnostic.Graphics;
import jetbrains.buildServer.server.rest.model.diagnostic.NodeUrls;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.TeamCityNode;
import jetbrains.buildServer.serverSide.TeamCityNodes;
import jetbrains.buildServer.util.DiagnosticUtil;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.diagnostic.MemoryUsageMonitor.*;

/**
 * REST Request for administrator's diagnostic page.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
@Path(DiagnosticRequest.API_RESOURCE_USAGE_URL)
@Api("Diagnostic")
public class DiagnosticRequest {

  public static final String API_RESOURCE_USAGE_URL = Constants.API_URL + "/diagnostic";

  private static final String CPU = "cpu";
  private static final String MEMORY = "memory";
  private static final Map<String, String> CPU_NAMES;

  @Context private ServiceLocator myServiceLocator;
  private Collection<StatisticDataProvider> myStatisticDataProviders;

  static {
    CPU_NAMES = new HashMap<>();
    CPU_NAMES.put(GC_USAGE_KEY, "Garbage collection");
    CPU_NAMES.put(JAVA_PROCESS_CPU_USAGE_KEY, "TeamCity process CPU usage");
    CPU_NAMES.put(SYSTEM_CPU_USAGE_KEY, "Overall CPU usage");
  }

  @GET
  @Path("/nodeUrls")
  public NodeUrls nodeUrls() {
    final TeamCityNodes teamCityNodes = getTeamCityNodes();
    return new NodeUrls(teamCityNodes.getOnlineNodes().stream().map(TeamCityNode::getUrl).collect(Collectors.toList()));
  }

  @GET
  @Path("/graphics")
  @Produces({"application/xml", "application/json"})
  public Graphics graphic(@QueryParam("fields") String fields) {
    final Collection<StatisticDataProvider> providers = getStatisticDataProviders();

    final Fields fs = new Fields(fields);
    final List<Graphic> graphics = Stream
      .of(CPU, MEMORY)
      .map(type -> ValueWithDefault.decideDefault(fs.isIncluded(type, true), calculateGraphic(type, providers)))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    return new Graphics(graphics);
  }

  private Graphic calculateGraphic(@NotNull final String type, final Collection<StatisticDataProvider> providers) {
    final StatisticDataProvider provider = providers.stream().filter(p -> p.getType().equals(type)).findFirst().orElse(null);
    if (provider == null) {
      return null;
    }

    final Map<String, Collection<StatisticDataProvider.Data>> data = provider.getData();
    final Axis abscissa = data
      .values().stream()
      .findFirst()
      .map(v -> v.stream().map(a -> (double)a.getTimestamp()).collect(Collectors.toList()))
      .map(a -> new Axis("time", null, a, null))
      .orElse(null);
    if (abscissa == null) {
      return null;
    }

    final List<Axis> ordinates = data
      .entrySet()
      .stream()
      .map(e -> new Axis(type.equals(CPU) ? "percent" : "absolute", e.getKey(), e.getValue().stream().map(a -> (double)a.getValue()).collect(Collectors.toList()), null))
      .collect(Collectors.toList());

    return new Graphic(type, abscissa, fixAxis(ordinates, type));
  }

  private List<Axis> fixAxis(@NotNull final List<Axis> axises, @NotNull final String type) {
    if (type.equals(CPU)) {
      final Axis stamps = axises.stream().filter(a -> STAMP_DURATION_KEY.equals(a.name)).findFirst().orElse(null);
      if (stamps == null) {
        return axises;
      }
      final List<Double> sDots = stamps.dots;
      return axises.stream().filter(a -> !STAMP_DURATION_KEY.equals(a.name)).peek(a -> {
        if (GC_USAGE_KEY.equals(a.name) || JAVA_PROCESS_CPU_USAGE_KEY.equals(a.name)) {
          for (int i = 0; i < a.dots.size() && i < sDots.size(); i++) {
            a.dots.set(i, a.dots.get(i) / sDots.get(i));
          }
        }
        a.name = CPU_NAMES.get(a.name);
      }).collect(Collectors.toList());

    } else if (type.equals(MEMORY)) {
      final ServerData data = getServerData();
      if (data == null) {
        return axises;
      }
      return axises.stream().peek(a -> {
        if (DiagnosticUtil.TC_TOTAL_MEMORY_USAGE_KEY.equals(a.name)) {
          final MemoryUsage memory = data.getHeapMemoryUsage();
          a.name = "Total heap";
          a.max = (double)memory.getMax();

        } else if ("Perm".equals(a.name)) {
          a.name = "Classes";
          final MemoryUsage memory = data.getPermGenPoolMemoryUsage();
          a.max = memory != null ? (double)memory.getMax() : null;

        } else if (DiagnosticUtil.isPermGenPool(a.name)) {
          a.name = "Classes";
          final MemoryUsage memory = data.getMemUsagesMap().get(a.name);
          a.max = memory != null ? (double)memory.getMax() : null;

        } else {
          final MemoryUsage memory = data.getMemUsagesMap().get(a.name);
          a.max = memory != null ? (double)memory.getMax() : null;
        }
      }).collect(Collectors.toList());
    }
    return axises;
  }

  private TeamCityNodes getTeamCityNodes() {
    return myServiceLocator.getSingletonService(TeamCityNodes.class);
  }

  private Collection<StatisticDataProvider> getStatisticDataProviders() {
    if (myStatisticDataProviders == null) {
      myStatisticDataProviders = myServiceLocator.getServices(StatisticDataProvider.class);
    }
    return myStatisticDataProviders;
  }

  private ServerData getServerData() {
    final MemoryUsageMonitor memoryUsageMonitor = myServiceLocator.getSingletonService(MemoryUsageMonitor.class);
    if (memoryUsageMonitor == null) {
      return null;
    }
    return new ServerData(memoryUsageMonitor, null);
  }
}
