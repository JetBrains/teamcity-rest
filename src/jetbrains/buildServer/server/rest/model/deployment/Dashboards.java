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

package jetbrains.buildServer.server.rest.model.deployment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.finder.impl.AgentPoolFinder;
import jetbrains.buildServer.server.rest.data.finder.impl.DeploymentDashboardFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentDashboard;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "deploymentDashboards")
@XmlType(name = "deploymentDashboards")
@ModelBaseType(ObjectType.PAGINATED)
public class Dashboards {
  @XmlAttribute
  public Integer count = 0;

  @XmlAttribute
  @Nullable
  public String href;

  @XmlAttribute(required = false)
  @Nullable
  public String nextHref;

  @XmlAttribute(required = false)
  @Nullable
  public String prevHref;

  @XmlElement(name = "deploymentDashboard")
  public List<Dashboard> items;

  public Dashboards() {
  }

  public Dashboards(
    @NotNull Collection<DeploymentDashboard> dashboards,
    @Nullable final PagerData pagerData,
    @NotNull final Fields fields,
    @NotNull final BeanContext beanContext
  ) {
    if (fields.isIncluded("deploymentDashboard", false, true)) {
      items = ValueWithDefault.decideDefault(
        fields.isIncluded("deploymentDashboard"),
        resolveItems(dashboards, fields, beanContext)
      );
    } else {
      items = null;
    }

    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), dashboards.size());

    if (pagerData != null) {
      href = ValueWithDefault.decideDefault(
        fields.isIncluded("href", true),
        resolveHref(pagerData, beanContext)
      );
      nextHref = ValueWithDefault
        .decideDefault(
          fields.isIncluded("nextHref"),
          resolveNextHref(pagerData, beanContext)
        );
      prevHref = ValueWithDefault
        .decideDefault(
          fields.isIncluded("prevHref"),
          resolvePrefHref(pagerData, beanContext)
        );
    }
  }

  @NotNull
  private static ArrayList<Dashboard> resolveItems(Collection<DeploymentDashboard> dashboards, Fields fields, BeanContext beanContext) {
    ArrayList<Dashboard> list = new ArrayList<>(dashboards.size());
    Fields dashboardFields = fields.getNestedField("deploymentDashboard");

    for (DeploymentDashboard dashboard : dashboards) {
      list.add(
        new Dashboard(dashboard, dashboardFields, beanContext)
      );
    }

    return list;
  }

  @Nullable
  private static String resolveHref(PagerData pagerData, BeanContext beanContext) {
    return beanContext
      .getApiUrlBuilder()
      .transformRelativePath(
        pagerData.getHref()
      );
  }

  @Nullable
  private static String resolveNextHref(PagerData pagerData, BeanContext beanContext) {
    return pagerData.getNextHref() != null ?
           beanContext
             .getApiUrlBuilder()
             .transformRelativePath(
               pagerData.getNextHref()
             ) : null;
  }

  @Nullable
  private static String resolvePrefHref(PagerData pagerData, BeanContext beanContext) {
    return pagerData.getPrevHref() != null ?
           beanContext
             .getApiUrlBuilder()
             .transformRelativePath(
               pagerData.getPrevHref()
             ) : null;
  }

  @NotNull
  public List<DeploymentDashboard> getDashboardsFromPosted(@NotNull final DeploymentDashboardFinder deploymentDashboardFinder) {
    if (items == null) {
      throw new BadRequestException("List of agent pools should be supplied");
    }
    final ArrayList<DeploymentDashboard> result = new ArrayList<>(items.size());
    for (Dashboard dashboard : items) {
      result.add(
        dashboard.getDashboardFromPosted(deploymentDashboardFinder)
      );
    }
    return result;
  }
}
