/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.finder.syntax;

import jetbrains.buildServer.server.rest.data.locator.Dimension;
import jetbrains.buildServer.server.rest.data.locator.PlainValue;
import jetbrains.buildServer.server.rest.data.locator.Syntax;
import jetbrains.buildServer.server.rest.data.locator.definition.FinderLocatorDefinition;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;

@LocatorResource(value = LocatorName.DEPLOYMENT_DASHBOARD,
  baseEntity = "DeploymentDashboard",
  examples = {
    "`id:<dashboardId>` - find dashboard with ID `dashboardId`.",
    "`project:<projectLocator>` - find all deployment dashboards under project found by `projectLocator`."
  }
)
public class DeploymentDashboardDimensions implements FinderLocatorDefinition {
  public static final Dimension ID = Dimension
    .ofName("id")
    .description("Dashboard id.")
    .syntax(PlainValue.string())
    .build();
  public static final Dimension NAME = Dimension
    .ofName("name")
    .description("Dashboard name.")
    .syntax(PlainValue.string())
    .build();
  public static final Dimension PROJECT = Dimension
    .ofName("project")
    .description("Projects defining the deployment dashboards.")
    .syntax(Syntax.forLocator(LocatorName.PROJECT))
    .build();
  public static final Dimension AFFECTED_PROJECT = Dimension
    .ofName("affectedProject")
    .description("Projects where the deployment dashboards are accessible.")
    .syntax(Syntax.forLocator(LocatorName.PROJECT))
    .build();
}
