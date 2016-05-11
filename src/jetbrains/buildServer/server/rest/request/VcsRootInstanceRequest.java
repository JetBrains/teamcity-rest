/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.BuildArtifactsFinder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.VcsRootInstanceFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.*;
import jetbrains.buildServer.server.rest.model.buildType.VcsRootInstances;
import jetbrains.buildServer.server.rest.model.change.VcsRootInstance;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.serverSide.VcsAccessFactory;
import jetbrains.buildServer.serverSide.VcsWorkspaceAccess;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.RepositoryStateManager;
import org.jetbrains.annotations.NotNull;

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produces 404
*/

@Path(VcsRootInstanceRequest.API_VCS_ROOT_INSTANCES_URL)
@Api
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
    return new Properties(rootInstance.getProperties(), null, new Fields(fields), myBeanContext.getServiceLocator());
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
    VcsRootInstance.setFieldValue(rootInstance, fieldName, newValue, myDataProvider);
    rootInstance.getParent().persist();
    return VcsRootInstance.getFieldValue(rootInstance, fieldName, myDataProvider);
  }

  @DELETE
  @Path("/{vcsRootInstanceLocator}/{field}")
  public void deleteInstanceField(@PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator, @PathParam("field") String fieldName) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
    myVcsRootInstanceFinder.checkPermission(Permission.EDIT_PROJECT, rootInstance);
    if (VcsRootInstance.LAST_VERSION_INTERNAL.equals(fieldName) || VcsRootInstance.LAST_VERSION.equals(fieldName)) {
      VcsRootInstance.setFieldValue(rootInstance, fieldName, "", myDataProvider);
    } else {
      throw new BadRequestException("Only \"" + VcsRootInstance.LAST_VERSION_INTERNAL + "\" field is supported for deletion.");
    }
  }

  @GET
  @Path("/{vcsRootInstanceLocator}/repositoryState")
  @Produces({"application/xml", "application/json"})
  public Entries getRepositoryState(@PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
    final RepositoryState repositoryState = myDataProvider.getBean(RepositoryStateManager.class).getRepositoryState(rootInstance);
    return new Entries(repositoryState.getBranchRevisions());
  }

  @DELETE
  @Path("/{vcsRootInstanceLocator}/repositoryState")
  public void deleteRepositoryState(@PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
    myVcsRootInstanceFinder.checkPermission(Permission.EDIT_PROJECT, rootInstance);
    myDataProvider.getBean(RepositoryStateManager.class).setRepositoryState(rootInstance, new SingleVersionRepositoryStateAdapter((String)null));
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
  public Entries setRepositoryState(@PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator, Entries branchesState) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
    myVcsRootInstanceFinder.checkPermission(Permission.EDIT_PROJECT, rootInstance);
    final RepositoryStateManager repositoryStateManager = myDataProvider.getBean(RepositoryStateManager.class);
    repositoryStateManager.setRepositoryState(rootInstance, RepositoryStateFactory.createRepositoryState(branchesState.getMap()));
    final RepositoryState repositoryState = repositoryStateManager.getRepositoryState(rootInstance);
    return new Entries(repositoryState.getBranchRevisions());
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