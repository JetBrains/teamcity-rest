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

package jetbrains.buildServer.server.rest.request;

import com.sun.jersey.spi.resource.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import jetbrains.buildServer.server.rest.DataProvider;
import jetbrains.buildServer.server.rest.data.change.VcsRoot;
import jetbrains.buildServer.server.rest.data.change.VcsRoots;

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produses 404
*/

@Path(VcsRootRequest.API_VCS_ROOTS_URL)
@Singleton
public class VcsRootRequest {
  private final DataProvider myDataProvider;
  public static final String API_VCS_ROOTS_URL = Constants.API_URL + "/vcs-roots";

  public VcsRootRequest(DataProvider myDataProvider) {
    this.myDataProvider = myDataProvider;
  }

  public static String getVcsRootHref(final jetbrains.buildServer.vcs.VcsRoot root) {
    return API_VCS_ROOTS_URL + "/id:" + root.getId() + ",ver:" + root.getRootVersion();
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public VcsRoots serveRoots() {
    return new VcsRoots(myDataProvider.getAllVcsRoots());
  }

  @GET
  @Path("/{vcsRootLocator}")
  @Produces({"application/xml", "application/json"})
  public VcsRoot serveRoot(@PathParam("vcsRootLocator") String vcsRootLocator) {
    return new VcsRoot(myDataProvider.getVcsRoot(vcsRootLocator));
  }
}