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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.*;
import jetbrains.buildServer.server.rest.model.buildType.VcsRootInstances;
import jetbrains.buildServer.server.rest.model.change.VcsRootInstance;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.serverSide.VcsAccessFactory;
import jetbrains.buildServer.serverSide.VcsWorkspaceAccess;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.RepositoryStateManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joda.time.format.ISODateTimeFormat;

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produces 404
*/

@Path(VcsRootInstanceRequest.API_VCS_ROOT_INSTANCES_URL)
@Api("VcsRootInstance")
public class VcsRootInstanceRequest {
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private BeanContext myBeanContext;
  @Context @NotNull private VcsRootInstanceFinder myVcsRootInstanceFinder;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;

  public static final String API_VCS_ROOT_INSTANCES_URL = Constants.API_URL + "/vcs-root-instances";
  public static final String FILES_LATEST = "/files/latest";

  public static String getHref() {
    return API_VCS_ROOT_INSTANCES_URL;
  }

  public static String getVcsRootInstanceHref(final jetbrains.buildServer.vcs.VcsRootInstance vcsRootInstance) {
    return API_VCS_ROOT_INSTANCES_URL + "/" + VcsRootInstanceFinder.getLocator(vcsRootInstance);
  }

  public static String getVcsRootInstancesHref(@NotNull final SVcsRoot vcsRoot) {
    return API_VCS_ROOT_INSTANCES_URL + "?locator=" + VcsRootInstanceFinder.getLocatorByVcsRoot(vcsRoot);
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public VcsRootInstances serveInstances(@QueryParam("locator") String vcsRootInstanceLocator,
                                         @QueryParam("fields") String fields,
                                         @Context UriInfo uriInfo,
                                         @Context HttpServletRequest request) {
    final PagedSearchResult<jetbrains.buildServer.vcs.VcsRootInstance> vcsRootInstances = myVcsRootInstanceFinder.getItems(vcsRootInstanceLocator);
    return new VcsRootInstances(CachingValue.simple(((Collection<jetbrains.buildServer.vcs.VcsRootInstance>)vcsRootInstances.myEntries)),
                                new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), vcsRootInstances, vcsRootInstanceLocator, "locator"),
                                new Fields(fields),
                                myBeanContext);
  }

  @GET
  @Path("/{vcsRootInstanceLocator}")
  @Produces({"application/xml", "application/json"})
  public VcsRootInstance serveInstance(@PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator, @QueryParam("fields") String fields) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
    return new VcsRootInstance(rootInstance, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{vcsRootInstanceLocator}/properties")
  @Produces({"application/xml", "application/json"})
  public Properties serveRootInstanceProperties(@PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator, @QueryParam("fields") String fields) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
    return new Properties(rootInstance.getProperties(), null, new Fields(fields), myBeanContext);
  }


  @GET
  @Path("/{vcsRootInstanceLocator}/{field}")
  @Produces("text/plain")
  public String serveInstanceField(@PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator,
                                   @PathParam("field") String fieldName) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
    return VcsRootInstance.getFieldValue(rootInstance, fieldName, myDataProvider);
  }

  @PUT
  @Path("/{vcsRootInstanceLocator}/{field}")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setInstanceField(@PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator,
                               @PathParam("field") String fieldName, String newValue) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
    myVcsRootInstanceFinder.checkPermission(Permission.EDIT_PROJECT, rootInstance);
    VcsRootInstance.setFieldValue(rootInstance, fieldName, newValue, myBeanContext);
    rootInstance.getParent().persist();
    return VcsRootInstance.getFieldValue(rootInstance, fieldName, myDataProvider);
  }

  @DELETE
  @Path("/{vcsRootInstanceLocator}/{field}")
  public void deleteInstanceField(@PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator, @PathParam("field") String fieldName) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
    myVcsRootInstanceFinder.checkPermission(Permission.EDIT_PROJECT, rootInstance);
    if (VcsRootInstance.LAST_VERSION_INTERNAL.equals(fieldName) || VcsRootInstance.LAST_VERSION.equals(fieldName)) {
      VcsRootInstance.setFieldValue(rootInstance, fieldName, "", myBeanContext);
    } else {
      throw new BadRequestException("Only \"" + VcsRootInstance.LAST_VERSION_INTERNAL + "\" field is supported for deletion.");
    }
  }

  @GET
  @Path("/{vcsRootInstanceLocator}/repositoryState")
  @Produces({"application/xml", "application/json"})
  public Entries getRepositoryState(@PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator, @QueryParam("fields") String fields) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
    final RepositoryState repositoryState = myDataProvider.getBean(RepositoryStateManager.class).getRepositoryState(rootInstance);
    return new Entries(repositoryState.getBranchRevisions(), new Fields(fields));
  }

  @DELETE
  @Path("/{vcsRootInstanceLocator}/repositoryState")
  public void deleteRepositoryState(@PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
    myVcsRootInstanceFinder.checkPermission(Permission.EDIT_PROJECT, rootInstance);
    myDataProvider.getBean(RepositoryStateManager.class).setRepositoryState(rootInstance, new SingleVersionRepositoryStateAdapter((String)null));
    Loggers.VCS.info("Repository state is reset via REST API call for " + rootInstance.describe(false) + " by " + myBeanContext.getSingletonService(PermissionChecker.class).getCurrentUserDescription());
  }

  @GET
  @Path("/{vcsRootInstanceLocator}/repositoryState/creationDate")
  @Consumes("text/plain")
  public String getRepositoryStateCreationDate(@PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
    final RepositoryState repositoryState = myDataProvider.getBean(RepositoryStateManager.class).getRepositoryState(rootInstance);
    return Util.formatTime(repositoryState.getCreateTimestamp());
  }

  @PUT
  @Path("/{vcsRootInstanceLocator}/repositoryState")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Entries setRepositoryState(@PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator, Entries branchesState, @QueryParam("fields") String fields) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
    myVcsRootInstanceFinder.checkPermission(Permission.EDIT_PROJECT, rootInstance);
    final RepositoryStateManager repositoryStateManager = myDataProvider.getBean(RepositoryStateManager.class);
    repositoryStateManager.setRepositoryState(rootInstance, RepositoryStateFactory.createRepositoryState(branchesState.getMap()));
    Loggers.VCS.info("Repository state is set to \"" + StringUtil.propertiesToString(branchesState.getMap(), StringUtil.STD_ESCAPER2) + "\" via REST API call for " + rootInstance.describe(false) + " by " + myBeanContext.getSingletonService(PermissionChecker.class).getCurrentUserDescription());
    final RepositoryState repositoryState = repositoryStateManager.getRepositoryState(rootInstance);
    return new Entries(repositoryState.getBranchRevisions(), new Fields(fields));
  }

  @POST
  @Path("/commitHookNotification")
  @Produces({"text/plain"})
  public Response scheduleCheckingForChanges(@QueryParam("locator") final String vcsRootInstancesLocator, @QueryParam("okOnNothingFound") final Boolean okOnNothingFound,
                                             @Context @NotNull final BeanContext beanContext) {
    if (StringUtil.isEmpty(vcsRootInstancesLocator)) {
      throw new BadRequestException("No 'locator' parameter provided, should be not empty VCS root instances locator");
    }
    Date requestStartTime = new Date();
    PagedSearchResult<jetbrains.buildServer.vcs.VcsRootInstance> vcsRootInstances = null;
    boolean nothingFound;
    try {
      vcsRootInstances = myVcsRootInstanceFinder.getItems(vcsRootInstancesLocator);
      nothingFound = vcsRootInstances.myEntries.isEmpty();
    } catch (NotFoundException e) {
      nothingFound = true;
    }
    if (nothingFound) {
      Response.ResponseBuilder responseBuilder;
      if (okOnNothingFound != null && okOnNothingFound) {
        responseBuilder = Response.status(Response.Status.OK);
      } else {
        responseBuilder = Response.status(Response.Status.NOT_FOUND);
      }
      return responseBuilder.entity("No VCS roots are found for locator '" + vcsRootInstancesLocator + "' with current user " +
                                                               myBeanContext.getSingletonService(PermissionChecker.class).getCurrentUserDescription() +
                                                               ". Check locator and permissions using '" +
                                                               API_VCS_ROOT_INSTANCES_URL + "?locator=" + Locator.HELP_DIMENSION + "' URL.").build();
    }

    myDataProvider.getChangesCheckingService().forceCheckingFor(vcsRootInstances.myEntries, OperationRequestor.COMMIT_HOOK);
    StringBuilder okMessage = new StringBuilder();
    okMessage.append("Scheduled checking for changes for");
    if (vcsRootInstances.isNextPageAvailable()) {
      okMessage.append(" first ").append(vcsRootInstances.myActualCount).append(" VCS roots.");
      if (vcsRootInstances.myCount != null && vcsRootInstances.myActualCount >= vcsRootInstances.myCount) {
        okMessage.append(" You can add '" + PagerData.COUNT + ":X' to cover more roots.");
      }
      if (vcsRootInstances.myLookupLimit != null && vcsRootInstances.myLookupLimitReached) {
        okMessage.append(" You can add '" + FinderImpl.DIMENSION_LOOKUP_LIMIT + ":X' to cover more roots.");
      }
    } else {
      okMessage.append(" ").append(vcsRootInstances.myEntries.size()).append(" VCS roots.");
    }
    okMessage.append(" (Server time: ").append(ISODateTimeFormat.basicDateTime().print(requestStartTime.getTime())).append(")"); //format supported by TimeWithPrecision, can later be used in filtering
    return Response.status(Response.Status.ACCEPTED).entity(okMessage.toString()).build();  //can also add "in XX projects"
  }


  /**
   * Was previously available as .../app/rest/debug/vcsCheckingForChangesQueue
   * @param vcsRootInstancesLocator locator for VCS root instances
   * @param requestor Marks the origin of the request. One of "commit_hook" or "user". Note that "commit_hook" has special meaning which increases background checking for changes interval for a VCS root
   * @return the set of VCS root instances scheduled for the checking for changes operation
   */
  @POST
  @Path("/checkingForChangesQueue")
  @Produces({"application/xml", "application/json"})
  public VcsRootInstances scheduleCheckingForChanges(@QueryParam("locator") final String vcsRootInstancesLocator,
                                                     @QueryParam("requestor") final String requestor,
                                                     @QueryParam("fields") final String fields,
                                                     @Context UriInfo uriInfo,
                                                     @Context HttpServletRequest request,
                                                     @Context @NotNull final BeanContext beanContext) {
    //todo: check whether permission checks are necessary
    final PagedSearchResult<jetbrains.buildServer.vcs.VcsRootInstance> vcsRootInstances = myVcsRootInstanceFinder.getItems(vcsRootInstancesLocator);
    myDataProvider.getChangesCheckingService().forceCheckingFor(vcsRootInstances.myEntries, getRequestor(requestor));
    PagerData pagerData = new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), vcsRootInstances, vcsRootInstancesLocator, "locator");

    //return 202 Accepted
    //return something which can be used to track progress (like get list of instances which has not yet compleed since the queuing
    //consider returning URL in href which will list the instances which has not yet been checked since the request
    return new VcsRootInstances(CachingValue.simple(vcsRootInstances.myEntries), pagerData, new Fields(fields), beanContext);
  }

  @NotNull
  private OperationRequestor getRequestor(@Nullable final String requestorText) {
    //TeamCity API: ideally, should be possible to pass custom value as requestor to allow debugging the origin of the request
    if (StringUtil.isEmpty(requestorText)) return OperationRequestor.USER;
    return TypedFinderBuilder.getEnumValue(requestorText, OperationRequestor.class);
  }

  public static final String WHERE_NOTE = "current sources of the VCS root";

  /**
   * Experimental support only
   */
  @Path("/{vcsRootInstanceLocator}" + FILES_LATEST)
  public FilesSubResource getVcsFilesSubResource(@PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
    myVcsRootInstanceFinder.checkPermission(Permission.VIEW_FILE_CONTENT, rootInstance);

    final String urlPrefix = getUrlPrefix(rootInstance);

    return new FilesSubResource(new FilesSubResource.Provider() {
      @Override
      @NotNull
      public Element getElement(@NotNull final String path) {
        return BuildArtifactsFinder.getItem(getVcsWorkspaceAccess(rootInstance).getVcsFilesBrowser(), path, WHERE_NOTE, myBeanContext.getServiceLocator());
      }

      @NotNull
      @Override
      public String getArchiveName(@NotNull final String path) {
        return rootInstance.getName().replaceAll(" ", "").replaceAll("::", "_").replaceAll("[^a-zA-Z0-9-#.]+", "_") + path.replaceAll("[^a-zA-Z0-9-#.]+", "_");
      }
    }, urlPrefix, myBeanContext, false);
  }

  @NotNull
  private String getUrlPrefix(final jetbrains.buildServer.vcs.VcsRootInstance rootInstance) {
    return Util.concatenatePath(myBeanContext.getApiUrlBuilder().getHref(rootInstance), FILES_LATEST);
  }

  @NotNull
  public VcsWorkspaceAccess getVcsWorkspaceAccess(@NotNull final jetbrains.buildServer.vcs.VcsRootInstance rootInstance) {
    final VcsRootInstanceEntry entry = new VcsRootInstanceEntry(rootInstance, CheckoutRules.DEFAULT);
    return myBeanContext.getSingletonService(VcsAccessFactory.class).createWorkspaceAccess(Collections.singletonList(entry));
  }
}