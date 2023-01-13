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

package jetbrains.buildServer.server.rest.request;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.annotation.XmlElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.controllers.BranchBean;
import jetbrains.buildServer.controllers.BranchBeanFactory;
import jetbrains.buildServer.controllers.BuildTypeBranchBean;
import jetbrains.buildServer.controllers.buildType.BuildTypeChangeLogBeanProvider;
import jetbrains.buildServer.controllers.buildType.BuildTypePendingChangeLogBeanProvider;
import jetbrains.buildServer.controllers.buildType.tabs.*;
import jetbrains.buildServer.controllers.project.ProjectChangeLogBeanProvider;
import jetbrains.buildServer.controllers.viewLog.BuildChangeLogBeanProvider;
import jetbrains.buildServer.server.rest.data.BuildPromotionFinder;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.server.rest.data.change.SVcsModificationOrChangeDescriptor;
import jetbrains.buildServer.server.rest.data.changeLog.ChangeLogBeanCollector;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerDataImpl;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.change.Change;
import jetbrains.buildServer.server.rest.model.changeLog.ChangeLog;
import jetbrains.buildServer.server.rest.model.changeLog.ChangeLogPagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildPromotionManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

@Path(ChangeLogRequest.API_CHANGE_LOG_URL)
@Api(value = "ChangeLog", hidden = true)
public class ChangeLogRequest {
  public static final String API_CHANGE_LOG_URL = Constants.API_URL + "/changeLog";
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BeanContext myBeanContext;
  @Context @NotNull private BuildPromotionFinder myBuildPromotionFinder;
  @Context @NotNull private BuildTypeFinder myBuildTypeFinder;
  @Context @NotNull private ProjectFinder myProjectFinder;
  @Context @NotNull private ChangeLogBeanCollector myChangeLogBeanCollector;

  @GET
  @Path("/{changeLogLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get change log.", nickname="serveChangeLog", hidden = true)
  public ChangeLog serveChangeLog(@PathParam("changeLogLocator") String changeLogLocator,
                                  @QueryParam("fields") String fields,
                                  @Context UriInfo uriInfo) {
    ChangeLogBean bean = myChangeLogBeanCollector.getItem(Locator.locator(changeLogLocator));
    if(bean == null) {
      throw new NotFoundException("Couldn't get a change log by given locator.");
    }
    List<jetbrains.buildServer.controllers.buildType.tabs.ChangeLogRow> rows = bean.getVisibleRows();

    return new ChangeLog(
      rows,
      bean.getGraph(),
      new Fields(fields),
      new ChangeLogPagerData(uriInfo.getBaseUriBuilder().path(API_CHANGE_LOG_URL), Locator.locator(changeLogLocator), bean.getPager()),
      myBeanContext
    );
  }
}
