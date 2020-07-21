/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.BuildFinder;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.plugin.PluginInfo;
import jetbrains.buildServer.server.rest.util.BeanContext;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

/**
 * @author Yegor.Yarko
 *         Date: 22.07.2009
 */
@Path(Constants.API_URL)
@Api("Root")
public class RootApiRequest {
  public static final String API_VERSION = "/apiVersion";
  public static final String VERSION = "/version";
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private BuildFinder myBuildFinder;
  @Context @NotNull private BuildTypeFinder myBuildTypeFinder;
  @Context @NotNull private ProjectFinder myProjectFinder;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull public BeanContext myBeanContext;

  @GET
  @Produces("text/plain")
  public String serveRoot() {
    return "This is a root of TeamCity REST API.\n" +
           "Explore what's inside from '" + myApiUrlBuilder.transformRelativePath(ServerRequest.API_SERVER_URL) + "'.\n" +
           "Get WADL with the full list of supported requests via '" + myApiUrlBuilder.getGlobalWadlHref() + "'.\n" +
           "See also notes on the usage at " + myDataProvider.getHelpLink("REST API", null);
  }

  @GET
  @Path(VERSION)
  @Produces("text/plain")
  public String serveVersion() {
    return myDataProvider.getPluginInfo().getPluginXml().getInfo().getVersion();
  }

  @GET
  @Path(API_VERSION)
  @Produces("text/plain")
  public String serveApiVersion() {
    return myDataProvider.getPluginInfo().getParameterValue("api.version");
  }

  @GET
  @Path("/info")
  @Produces("application/xml")
  public PluginInfo servePluginInfo(@QueryParam("fields") String fields) {
    return new PluginInfo(myDataProvider.getPluginInfo(), new Fields(fields), myBeanContext);
  }

}
