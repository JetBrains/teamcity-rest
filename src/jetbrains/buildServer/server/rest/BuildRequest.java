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
import javax.ws.rs.*;
import jetbrains.buildServer.server.rest.data.build.Build;
import jetbrains.buildServer.server.rest.data.build.Builds;
import jetbrains.buildServer.serverSide.SBuild;

/**
 * User: Yegor Yarko
 * Date: 11.04.2009
 */
@Path("/httpAuth/api/builds")
@Singleton
public class BuildRequest {
  private final DataProvider myDataProvider;

  public BuildRequest(DataProvider myDataProvider) {
    this.myDataProvider = myDataProvider;
  }

  public static String getBuildHref(SBuild build) {
    return "/httpAuth/api/builds/id:" + build.getBuildId();
  }


  @GET
  @Produces({"application/xml", "application/json"})
  public Builds serveAllBuilds(@QueryParam("buildTypeId") String buildTypeId,
                               @QueryParam("status") String status,
                               @QueryParam("username") String username,
                               @QueryParam("includePersonal") boolean includePersonal,
                               @QueryParam("includeCanceled") boolean includeCanceled,
                               @QueryParam("onlyPinned") boolean onlyPinned,
                               @QueryParam("agentName") String agentName,
                               @QueryParam("start") Long start,
                               @QueryParam("finish") Long finish) {
    return new Builds(
      myDataProvider.getAllBuilds(buildTypeId, status, username, includePersonal, includeCanceled, onlyPinned, agentName, start, finish),
      myDataProvider);
  }

  @GET
  @Path("/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  public Build serveBuild(@PathParam("buildLocator") String buildLocator) {
    return new Build(myDataProvider.getBuild(null, buildLocator), myDataProvider);
  }

  @GET
  @Path("/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildFieldByBuildOnly(@PathParam("buildLocator") String buildLocator,
                                           @PathParam("field") String field) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);

    return myDataProvider.getFieldValue(build, field);
  }
}
