/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest;

import com.sun.jersey.spi.resource.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;

/**
 * @author Yegor.Yarko
 *         Date: 22.07.2009
 */
@Path("/httpAuth/api")
@Singleton
public class RootApiRequest {

  private final DataProvider myDataProvider;

  public RootApiRequest(DataProvider myDataProvider) {
    this.myDataProvider = myDataProvider;
  }

  @GET
  @Path("/version")
  @Produces("text/plain")
  public String serveApiVersion() {
    final String svn_revision = "$Revision$";
    return "rev." + svn_revision.substring("$Revision: ".length(), svn_revision.length() - 2);
  }

  @GET
  @Path("/{projectLocator}/{btLocator}/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildFieldShort(@PathParam("projectLocator") String projectLocator,
                                     @PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("buildLocator") String buildLocator,
                                     @PathParam("field") String field) {
    SProject project = myDataProvider.getProject(projectLocator);
    SBuildType buildType = myDataProvider.getBuildType(project, buildTypeLocator);
    SBuild build = myDataProvider.getBuild(buildType, buildLocator);

    return myDataProvider.getFieldValue(build, field);
  }
}
