/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package jetbrains.buildServer.server.restcontrib.cctray.request;

import com.sun.jersey.spi.resource.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.finder.impl.BuildTypeFinder;
import jetbrains.buildServer.server.rest.data.util.LocatorUtil;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.server.restcontrib.cctray.model.Projects;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 20.06.2010
 */
@Path(Constants.API_URL + "/cctray")
@Singleton
public class CCTrayRequest {
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BuildTypeFinder myBuildTypeFinder;

  @GET
  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  public Projects serveProjectsConvenienceCopy(@QueryParam("locator") String buildTypeLocator) {
    return serveProjects(buildTypeLocator);
  }

  @GET
  @Path("/projects.xml")
  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  public Projects serveProjects(@QueryParam("locator") String buildTypeLocator) {
    String actualLocator = LocatorUtil.setDimension(buildTypeLocator, BuildTypeFinder.TEMPLATE_FLAG_DIMENSION_NAME, "false");
    actualLocator = Locator.setDimensionIfNotPresent(actualLocator, BuildTypeFinder.PAUSED, String.valueOf(TeamCityProperties.getBoolean("rest.cctray.includePausedBuildTypes")));
    return new Projects(myServiceLocator, myBuildTypeFinder.getBuildTypes(null, actualLocator));
  }
}