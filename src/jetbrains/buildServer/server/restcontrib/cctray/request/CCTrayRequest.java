/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.server.restcontrib.cctray.model.Projects;
import jetbrains.buildServer.serverSide.SBuildType;

/**
 * @author Yegor.Yarko
 *         Date: 20.06.2010
 */
@Path(Constants.API_URL + "/cctray")
@Singleton
public class CCTrayRequest {
    @Context
    private DataProvider myDataProvider;
    @Context
    private ServiceLocator myServiceLocator;

    @GET
    @Path("/projects.xml")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Projects serveProjects() {
      return new Projects(myServiceLocator, getBuildTypes());
    }

    private List<SBuildType> getBuildTypes() {
      return myDataProvider.getServer().getProjectManager().getAllBuildTypes();
    }
}