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
import java.util.List;
import javax.ws.rs.*;
import jetbrains.buildServer.server.rest.DataProvider;
import jetbrains.buildServer.server.rest.data.PagerData;
import jetbrains.buildServer.server.rest.data.change.Change;
import jetbrains.buildServer.server.rest.data.change.Changes;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.VcsModification;

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produses 404
*/

@Path(ChangeRequest.API_CHANGES_URL)
@Singleton
public class ChangeRequest {
  public static final String API_CHANGES_URL = Constants.API_URL + "/changes";
  private final DataProvider myDataProvider;

  public ChangeRequest(DataProvider myDataProvider) {
    this.myDataProvider = myDataProvider;
  }

  public static String getChangeHref(VcsModification modification) {
    return API_CHANGES_URL + "/id:" + modification.getId();
  }

  public static String getBuildChangesHref(SBuild build) {
    return API_CHANGES_URL + "?build=id:" + build.getBuildId();
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public Changes serveChanges(@QueryParam("build") String buildLocator,
                              @QueryParam("start") @DefaultValue(value = "0") Long start,
                              @QueryParam("count") @DefaultValue(value = Constants.DEFAULT_PAGE_ITEMS_COUNT) Integer count) {
    List<SVcsModification> buildModifications;
    //todo investigate how to get current URL
    String requestUrlForPager;
    if (!StringUtil.isEmpty(buildLocator)) {
      final SBuild build = myDataProvider.getBuild(null, buildLocator);
      buildModifications = myDataProvider.getBuildModifications(build, start, count);
      requestUrlForPager = API_CHANGES_URL + "?build=" + buildLocator;
    } else {
      buildModifications = myDataProvider.getAllModifications(start, count);
      requestUrlForPager = API_CHANGES_URL;
    }
    return new Changes(buildModifications,
                       new PagerData(requestUrlForPager, start, count, buildModifications.size()));
  }

  @GET
  @Path("/{changeLocator}")
  @Produces({"application/xml", "application/json"})
  public Change serveChange(@PathParam("changeLocator") String changeLocator) {
    return new Change(myDataProvider.getChange(changeLocator));
  }
}