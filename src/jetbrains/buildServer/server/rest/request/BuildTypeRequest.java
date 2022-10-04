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

import com.intellij.openapi.diagnostic.Logger;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.parameters.ProcessingResult;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationFinder;
import jetbrains.buildServer.server.rest.data.parameters.ParametersPersistableEntity;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.*;
import jetbrains.buildServer.server.rest.model.build.Branches;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.build.Tags;
import jetbrains.buildServer.server.rest.model.buildType.*;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.dependency.Dependency;
import jetbrains.buildServer.serverSide.identifiers.BuildTypeIdentifiersManager;
import jetbrains.buildServer.serverSide.impl.VcsLabelingBuildFeature;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.server.rest.data.ProjectFinder.BUILD_TYPE;

/*
 * User: Yegor Yarko
 * Date: 22.03.2009
 */

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produces 404
*/

@Path(BuildTypeRequest.API_BUILD_TYPES_URL)
@Api("BuildType")
public class BuildTypeRequest {
  private static final Logger LOG = Logger.getInstance(BuildTypeRequest.class.getName());

  private static final Pattern WHITESPACE_PATTERN = Pattern.compile(" ");
  private static final Pattern PROJECT_PATH_SEPARATOR_PATTERN = Pattern.compile("::");
  private static final Pattern NON_ALPHA_NUM_PATTERN = Pattern.compile("[^a-zA-Z0-9-#.]+");

  @Context @NotNull private BuildFinder myBuildFinder;
  @Context @NotNull private BuildTypeFinder myBuildTypeFinder;
  @Context @NotNull private VcsRootFinder myVcsRootFinder;
  @Context @NotNull private InvestigationFinder myInvestigationFinder;
  @Context @NotNull private BranchFinder myBranchFinder;

  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BeanFactory myFactory;
  @Context @NotNull private BeanContext myBeanContext;
  @Context @NotNull public PermissionChecker myPermissionChecker;

  public static final String API_BUILD_TYPES_URL = Constants.API_URL + "/buildTypes";
  public static final String VCS_FILES_LATEST = "/vcs/files/latest";
  public static final String PARAMETERS = "/parameters";

  public void setInTests(@NotNull BuildTypeFinder buildTypeFinder, @NotNull BranchFinder branchFinder, @NotNull BeanContext beanContext){
    myBuildTypeFinder = buildTypeFinder;
    myBranchFinder = branchFinder;
    myBeanContext = beanContext;
  }

  public static String getBuildTypeHref(@NotNull final BuildTypeOrTemplate buildType) {
    return buildType.isBuildType() ? getBuildTypeHref(buildType.getBuildType()) : getBuildTypeHref(buildType.getTemplate());
  }

  @NotNull
  public static String getHref() {
    return API_BUILD_TYPES_URL;
  }

  @NotNull
  public static String getBuildTypeHref(@NotNull SBuildType buildType) {
    return API_BUILD_TYPES_URL + "/" + BuildTypeFinder.getLocator(buildType);
  }

  public static String getBuildTypeHref(@NotNull final BuildTypeTemplate template) {
    return API_BUILD_TYPES_URL + "/" + BuildTypeFinder.getLocator(template);
  }


  public static String getBuildsHref(final SBuildType buildType) {
    return getBuildTypeHref(buildType) + "/builds/";
  }

  public static String getBuildsHref(final SBuildType buildType, @NotNull String locator) {
    return getBuildTypeHref(buildType) + "/builds" + "?locator=" + locator; //todo: URL-escape
  }

  @NotNull
  public static String getBranchesHref(@NotNull final SBuildType buildType, @Nullable final String branchesLocator) {
    return getBuildTypeHref(buildType) + "/branches" + (branchesLocator == null ? "" : "?locator=" + branchesLocator);
  }

  public static String getParametersHref(final BuildTypeOrTemplate buildType) {
    return getBuildTypeHref(buildType) + PARAMETERS;
  }

  /**
   * Lists build types registered on the server. Build templates are not included by default
   */
  @GET
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all build configurations.",nickname="getAllBuildTypes")
  public BuildTypes getBuildTypes(@ApiParam(format = LocatorName.BUILD_TYPE) @QueryParam("locator") String locator,
                                  @QueryParam("fields") String fields,
                                  @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    //do not return templates unless specifically requested
    String actualLocator;
    try {
      actualLocator = Locator.setDimensionIfNotPresent(locator, BuildTypeFinder.TEMPLATE_FLAG_DIMENSION_NAME, "false");
    } catch (IllegalArgumentException e) {
      //cannot set the dimension, continue as is
      actualLocator = locator;
    } catch (LocatorProcessException e) {
      //cannot set the dimension, continue as is
      actualLocator = locator;
    }
    final PagedSearchResult<BuildTypeOrTemplate> result = myBuildTypeFinder.getItems(actualLocator);

    final PagerData pager = new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), result, locator, "locator");
    return new BuildTypes(result.myEntries, pager, new Fields(fields), myBeanContext);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Create a new build configuration.",nickname="createBuildType")
  public BuildType addBuildType(BuildType buildType, @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate newBuildType = buildType.createNewBuildTypeFromPosted(myServiceLocator);
    newBuildType.persist("A new build configuration is created");
    return new BuildType(newBuildType,  new Fields(fields), myBeanContext);
  }

  /**
   * Serves build configuration or templates according to the locator.
   */
  @GET
  @Path("/{btLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get build configuration matching the locator.",nickname="getBuildType")
  public BuildType serveBuildTypeXML(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                     @QueryParam("fields") String fields) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, false);
    return new BuildType(buildType,  new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{btLocator}")
  @ApiOperation(value="Delete build configuration matching the locator.",nickname="deleteBuildType")
  public void deleteBuildType(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, false);
    AuthorityHolder authorityHolder = myBeanContext.getSingletonService(SecurityContext.class).getAuthorityHolder();
    buildType.remove((SUser)authorityHolder.getAssociatedUser(), buildType.isBuildType() ? "Build configuration removed" : "Template removed");
  }

  @GET
  @Path("/{btLocator}/{field}")
  @Produces("text/plain")
  @ApiOperation(value="Get a field of the matching build configuration.",nickname="getBuildTypeField")
  public String serveBuildTypeField(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                    @PathParam("field") String fieldName) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, false);
    return buildType.getFieldValue(fieldName, myBeanContext);
  }

  @PUT
  @Path("/{btLocator}/{field}")
  @Consumes("text/plain")
  @Produces("text/plain")
  @ApiOperation(value="Update a field of the matching build configuration.",nickname="setBuildTypeField")
  public String setBuildTypeField(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                  @PathParam("field") String fieldName,
                                  String newValue) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, false); //todo: support multiple locator here to pause many in one request
    buildType.setFieldValueAndPersist(fieldName, newValue, myServiceLocator);
    return buildType.getFieldValue(fieldName, myBeanContext);
  }

  @GET
  @Path("/{btLocator}/buildTags")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get tags of builds of the matching build configuration.",nickname="getBuildTypeBuildTags")
  public Tags serveBuildTypeBuildsTags(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                       @QueryParam("field") String field) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, false);

    return new Tags(CollectionsUtil.convertCollection(buildType.getTags(), new Converter<TagData, String>() {
      public TagData createFrom(@NotNull final String source) {
        return TagData.createPublicTag(source);
      }
    }), new Fields(field), myBeanContext);
  }

  /**
   * Experimental support only
   */
  @GET
  @Path("/{btLocator}/aliases")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get external IDs of the matching build configuration.",nickname="getAliases")
  public Items getAliases(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                          @QueryParam("field") String field) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, true);
    return new Items(myBeanContext.getSingletonService(BuildTypeIdentifiersManager.class).getAllExternalIds(buildType.getInternalId()));
  }

  @Path("/{btLocator}" + PARAMETERS)
  public TypedParametersSubResource getParametersSubResource(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator){
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    return new TypedParametersSubResource(myBeanContext, BuildType.createEntity(buildType), getParametersHref(buildType));
  }

  @ApiOperation(value="getSettingsSubResource", hidden=true)
  @Path("/{btLocator}/settings")
  public ParametersSubResource getSettingsSubResource(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    return new ParametersSubResource(myBeanContext, new BuildTypeSettingsEntityWithParams(buildType), getHref() + "/settings");
  }

  @GET
  @Path("/{btLocator}/templates")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all build templates of the matching build configuration.",nickname="getAllBuildTemplates")
  public BuildTypes getTemplates(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                 @QueryParam("fields") String fields) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, true);
    return BuildType.getTemplates(buildType, new Fields(fields), myBeanContext);
  }

  @PUT
  @Path("/{btLocator}/templates")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update all templates of the matching build configuration.",nickname="setBuildTypeTemplates")
  public BuildTypes setTemplates(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                 BuildTypes templates,
                                 @QueryParam("optimizeSettings") Boolean optimizeSettings,
                                 @QueryParam("fields") String fields) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, true);
    if (templates == null) {
      throw new BadRequestException("Nothing is posted as payload while list of templates is expected");
    }
    BuildTypeOrTemplate.setTemplates(buildType, templates.getFromPosted(myBuildTypeFinder), optimizeSettings != null ? optimizeSettings : false);
    buildType.schedulePersisting("Build configuration templates changed");
    return BuildType.getTemplates(buildType, new Fields(fields), myBeanContext);
  }

  @POST
  @Path("/{btLocator}/templates")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Add a build template to the matching build configuration.",nickname="addBuildTemplate")
  public BuildType addTemplate(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                               BuildType template,
                               @QueryParam("optimizeSettings") Boolean optimizeSettings,
                               @QueryParam("fields") String fields) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, true);
    if (template == null) {
      throw new BadRequestException("Nothing is posted as payload while a template is expected");
    }
    BuildTypeOrTemplate posted = template.getBuildTypeFromPosted(myBuildTypeFinder);
    BuildTypeTemplate result = posted.getTemplate();
    if (result == null) {
      throw new BadRequestException("Found build type when template is expected: " + LogUtil.describe(posted.getBuildType()));
    }
    try {
      buildType.addTemplate(result, optimizeSettings != null ? optimizeSettings : false);
    } catch (CannotAttachToTemplateException e) {
      throw new BadRequestException(e.getMessage());
    }
    buildType.schedulePersisting("Build configuration attached to a template");
    return new BuildType(getTemplateById(buildType, result.getExternalId(), true), new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{btLocator}/templates")
  @ApiOperation(value="Detach all templates from the matching build configuration.",nickname="removeAllTemplates")
  public void removeAllTemplates(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                 @QueryParam("inlineSettings") Boolean inlineSettings) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, true);
    buildType.removeTemplates(buildType.getOwnTemplates(), inlineSettings != null ? inlineSettings : false);
    buildType.schedulePersisting("Build configuration detached from all of the templates");
  }

  @GET
  @Path("/{btLocator}/templates/{templateLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get a template of the matching build configuration.",nickname="getBuildTemplate")
  public BuildType getTemplate(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                               @ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("templateLocator") String templateLocator,
                               @QueryParam("fields") String fields) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, true);
    BuildTypeTemplate template = myBuildTypeFinder.getBuildTemplate(null, templateLocator, true);
    return new BuildType(getTemplateById(buildType, template.getExternalId(), false), new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{btLocator}/templates/{templateLocator}")
  @ApiOperation(value="Detach a template from the matching build configuration.",nickname="removeTemplate")
  public void removeTemplate(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                             @ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("templateLocator") String templateLocator,
                             @QueryParam("inlineSettings") Boolean inlineSettings) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, true);
    BuildTypeTemplate template = myBuildTypeFinder.getBuildTemplate(null, templateLocator, true);
    BuildTypeTemplate foundTemplate = getTemplateById(buildType, template.getExternalId(), true).getTemplate();
    buildType.removeTemplates(Collections.singleton(foundTemplate), inlineSettings != null ? inlineSettings : false);
    buildType.schedulePersisting("Build configuration detached from a template");
  }

  @NotNull
  private BuildTypeOrTemplate getTemplateById(@NotNull final SBuildType buildType, @NotNull final String templateExternalId, final boolean onlyOwn) {
    if (onlyOwn) {
      return new BuildTypeOrTemplate(buildType.getOwnTemplates().stream().filter(t -> t.getExternalId().equals(templateExternalId)).findFirst()
                                              .orElseThrow(() -> new NotFoundException(
                                                "Build type " + LogUtil.describe(buildType) + " does not have own template with id \"" + templateExternalId + "\"")));
    }
    Set<String> ownTemplatesIds = buildType.getOwnTemplates().stream().map(t -> t.getInternalId()).collect(Collectors.toSet());
    BuildTypeOrTemplate result = new BuildTypeOrTemplate(buildType.getTemplates().stream().filter(t -> t.getExternalId().equals(templateExternalId)).findFirst()
                                                                  .orElseThrow(() -> new NotFoundException(
                                                                    "Build type " + LogUtil.describe(buildType) + " does not have template with id \"" + templateExternalId +
                                                                    "\"")));
    result.markInherited(!ownTemplatesIds.contains(result.getInternalId()));
    return result;
  }

  /**
   * @Deprecated Use .../templates instead
   */
  @GET
  @Path("/{btLocator}/template")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(hidden = true, value = "Use .../templates instead")
  public BuildType serveBuildTypeTemplate(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                          @QueryParam("fields") String fields) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, true);
    final BuildTypeTemplate template;
    try {
      template = buildType.getTemplate();
    } catch (BuildTypeTemplateNotFoundException e) {
      throw new AuthorizationFailedException("The template is not accessible. Cross-hierarchy template use?");
    }
    if (template == null) {
      throw new NotFoundException("No template associated."); //todo: how to report it duly?
    }
    return new BuildType(new BuildTypeOrTemplate(template),  new Fields(fields), myBeanContext);
  }

  /**
   * @Deprecated Use .../templates instead
   */
  @PUT
  @Path("/{btLocator}/template")
  @Consumes("text/plain")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(hidden = true, value = "Use .../templates instead")
  public BuildType getTemplateAssociation(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                          String templateLocator,
                                          @QueryParam("fields") String fields,
                                          @QueryParam("inlineSettings") Boolean inlineSettings,
                                          @QueryParam("optimizeSettings") Boolean optimizeSettings) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, true);
    BuildTypeTemplate template = myBuildTypeFinder.getBuildTemplate(null, templateLocator, true);
    try {
      if (inlineSettings != null && inlineSettings) {
        buildType.removeTemplates(buildType.getOwnTemplates(), true);
      }
      buildType.setTemplates(Collections.singletonList(template), optimizeSettings != null ? optimizeSettings : true);   //using "true" as default here to replicate pre-2017.2 behavior
    } catch (CannotAttachToTemplateException e) {
      throw new BadRequestException(e.getMessage());
    }
    buildType.schedulePersisting("Build configuration templates changed");
    return new BuildType(new BuildTypeOrTemplate(template),  new Fields(fields), myBeanContext);
  }
//todo: allow also to post back the XML from GET request (http://devnet.jetbrains.net/message/5466528#5466528)

  /**
   * @Deprecated Use .../templates instead
   */
  @DELETE
  @Path("/{btLocator}/template")
  @ApiOperation(hidden = true, value = "Use .../templates instead")
  public void deleteTemplateAssociation(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                        @QueryParam("inlineSettings") Boolean inlineSettings) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, true);
    buildType.removeTemplates(buildType.getOwnTemplates(), inlineSettings != null ? inlineSettings : true); //using "true" as default here to replicate pre-2017.2 behavior
    buildType.schedulePersisting("Build configuration detached from all of the templates");
  }


  @GET
  @Path("/{btLocator}/vcs-root-entries")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all VCS roots of the matching build configuration.",nickname="getAllVcsRoots")
  public VcsRootEntries getVcsRootEntries(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                          @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    return new VcsRootEntries(buildType, new Fields(fields), myBeanContext);
  }

  @PUT
  @Path("/{btLocator}/vcs-root-entries")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update all VCS roots of the matching build configuration.",nickname="replaceAllVcsRoots")
  public VcsRootEntries replaceVcsRootEntries(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                              VcsRootEntries suppliedEntities,
                                              @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    suppliedEntities.setToBuildType(buildType.get(), myServiceLocator);
    buildType.persist("Attached VCS roots changed");
    return new VcsRootEntries(buildType, new Fields(fields), myBeanContext);
  }

  @POST
  @Path("/{btLocator}/vcs-root-entries")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Add a VCS root to the matching build.",nickname="addVcsRootToBuildType")
  public VcsRootEntry addVcsRootEntry(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                      VcsRootEntry description,
                                      @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final SVcsRoot vcsRoot = description.addTo(buildType.get(), myVcsRootFinder);
    buildType.persist("New VCS root attached");

    return new VcsRootEntry(vcsRoot, buildType, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{btLocator}/vcs-root-entries/{vcsRootLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get a VCS root of the matching build configuration.",nickname="getVcsRoot")
  public VcsRootEntry getVcsRootEntry(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                      @ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator,
                                      @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);

    if (!buildType.get().containsVcsRoot(vcsRoot.getId())) {
      throw new NotFoundException("VCS root with id '" + vcsRoot.getExternalId() + "' is not attached to the build type.");
    }
    return new VcsRootEntry(vcsRoot, buildType, new Fields(fields), myBeanContext);
  }

  @PUT
  @Path("/{btLocator}/vcs-root-entries/{vcsRootLocator}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update a VCS root of the matching build configuration.",nickname="updateBuildTypeVcsRoot")
  public VcsRootEntry updateVcsRootEntry(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                         @ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator,
                                         VcsRootEntry entry,
                                         @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);

    final SVcsRoot resultVcsRoot = entry.replaceIn(buildType.get(), vcsRoot, myVcsRootFinder);
    buildType.persist("VCS root replaced");
    return new VcsRootEntry(resultVcsRoot, buildType, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{btLocator}/vcs-root-entries/{vcsRootLocator}/" + VcsRootEntry.CHECKOUT_RULES)
  @Produces({"text/plain"})
  @ApiOperation(value="Get checkout rules of a VCS root of the matching build configuration.",nickname="getVcsRootCheckoutRules")
  public String getVcsRootEntryCheckoutRules(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                             @ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);

    if (!buildType.get().containsVcsRoot(vcsRoot.getId())) {
      throw new NotFoundException("VCS root with id '" + vcsRoot.getExternalId() + "' is not attached to the build type.");
    }
    return buildType.get().getCheckoutRules(vcsRoot).getAsString();
  }

  @PUT
  @Path("/{btLocator}/vcs-root-entries/{vcsRootLocator}/" + VcsRootEntry.CHECKOUT_RULES)
  @Consumes({"text/plain"})
  @Produces({"text/plain"})
  @ApiOperation(value="Update checkout rules of a VCS root of the matching build configuration.",nickname="updateBuildTypeVcsRootCheckoutRules")
  public String updateVcsRootEntryCheckoutRules(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                                @ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator,
                                                String newCheckoutRules) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);

    if (!buildType.get().containsVcsRoot(vcsRoot.getId())) {
      throw new NotFoundException("VCS root with id '" + vcsRoot.getExternalId() + "' is not attached to the build type.");
    }
    buildType.get().setCheckoutRules(vcsRoot, new CheckoutRules(newCheckoutRules != null ? newCheckoutRules : ""));

    buildType.persist("VCS root checkout rules changed");
    //not handling setting errors...
    return buildType.get().getCheckoutRules(vcsRoot).getAsString();
  }

  @DELETE
  @Path("/{btLocator}/vcs-root-entries/{vcsRootLocator}")
  @ApiOperation(value="Remove a VCS root of the matching build configuration.",nickname="deleteVcsRoot")
  public void deleteVcsRootEntry(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                 @ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true); //todo: extract builType/VCS root id retrieving logic into single method
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator); //this assumes VCS root id are unique throughout the server
    if (!buildType.get().containsVcsRoot(vcsRoot.getId())) {
      throw new NotFoundException("VCS root with id '" + vcsRoot.getExternalId() + "' is not attached to the build type.");
    }
    buildType.get().removeVcsRoot(vcsRoot);
    buildType.persist("VCS root detached");
  }


  @GET
  @Path("/{btLocator}/steps")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all build steps of the matching build configuration.",nickname="getAllBuildSteps")
  public PropEntitiesStep getSteps(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                   @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    return new PropEntitiesStep(buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  @PUT
  @Path("/{btLocator}/steps")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update all build steps of the matching build configuration.",nickname="replaceAllBuildSteps")
  public PropEntitiesStep replaceSteps(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                       @QueryParam("fields") String fields,
                                       PropEntitiesStep suppliedEntities) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    suppliedEntities.setToBuildType(buildType.getSettingsEx(), myServiceLocator);
    buildType.persist("Build steps replaced");
    return new PropEntitiesStep(buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  @POST
  @Path("/{btLocator}/steps")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Add a build step to the matching build configuration.",nickname="addBuildStepToBuildType")
  public PropEntityStep addStep(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                @QueryParam("fields") String fields,
                                PropEntityStep stepDescription) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final SBuildRunnerDescriptor newRunner = stepDescription.addTo(buildType.getSettingsEx(), myServiceLocator);
    buildType.persist("New build step added");
    return new PropEntityStep(newRunner, buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{btLocator}/steps/{stepId}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get a build step of the matching build configuration.",nickname="getBuildStep")
  public PropEntityStep getStep(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                @PathParam("stepId") String stepId,
                                @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    return new PropEntityStep(getStep(buildType.get(), stepId), buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{btLocator}/steps/{stepId}")
  @ApiOperation(value="Delete a build step of the matching build configuration.",nickname="deleteBuildStep")
  public void deleteStep(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                         @PathParam("stepId") String stepId) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    PropEntityStep.removeFrom(buildType.get(), getStep(buildType.get(), stepId));
    buildType.persist("Build step removed");
  }

  @PUT
  @Path("/{btLocator}/steps/{stepId}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Replace a build step of the matching build configuration.",nickname="replaceBuildStep")
  public PropEntityStep replaceStep(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                    @PathParam("stepId") String stepId,
                                    @QueryParam("fields") String fields,
                                    PropEntityStep stepDescription) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final SBuildRunnerDescriptor newRunner = stepDescription.replaceIn(buildType.getSettingsEx(), getStep(buildType.get(), stepId), myServiceLocator);
    buildType.persist("Build step replaced");
    return new PropEntityStep(newRunner, buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{btLocator}/steps/{stepId}/parameters")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all parameters of a build step of the matching build configuration.",nickname="getAllBuildStepParameters")
  public Properties getStepParameters(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                      @PathParam("stepId") String stepId,
                                      @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    SBuildRunnerDescriptor step = getStep(buildType.get(), stepId);
    return new Properties(step.getParameters(), null, new Fields(fields), myBeanContext);
  }

  @PUT
  @Path("/{btLocator}/steps/{stepId}/parameters")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update a parameter of a build step of the matching build configuration.",nickname="deleteBuildStepParameters")
  public Properties replaceStepParameters(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                          @PathParam("stepId") String stepId,
                                          Properties properties,
                                          @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    SBuildRunnerDescriptor step = getStep(buildType.get(), stepId);

    buildType.get().updateBuildRunner(step.getId(), step.getName(), step.getType(), properties.getMap());
    buildType.persist("Build step parameters changed");
    return new Properties(getStep(buildType.get(), stepId).getParameters(), null, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{btLocator}/steps/{stepId}/parameters/{parameterName}")
  @Produces({"text/plain"})
  @ApiOperation(value="Get a parameter of a build step of the matching build configuration.",nickname="getBuildStepParameter")
  public String getStepParameter(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                 @PathParam("stepId") String stepId,
                                 @PathParam("parameterName") String parameterName) {
    SBuildRunnerDescriptor step = getStep(myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true).get(), stepId);
    return BuildTypeUtil.getParameter(parameterName, step.getParameters(), true, false, myServiceLocator);
  }

  @PUT
  @Path("/{btLocator}/steps/{stepId}/parameters/{parameterName}")
  @Consumes({"text/plain"})
  @Produces({"text/plain"})
  @ApiOperation(value="Add a parameter to a build step of the matching build configuration.",nickname="addParameterToBuildStep")
  public String addStepParameter(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                 @PathParam("stepId") String stepId,
                                 @PathParam("parameterName") String parameterName,
                                 String newValue) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    SBuildRunnerDescriptor step = getStep(buildType.get(), stepId);
    Map<String, String> parameters = new HashMap<String, String>(step.getParameters());
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    parameters.put(parameterName, newValue);
    buildType.get().updateBuildRunner(step.getId(), step.getName(), step.getType(), parameters);
    buildType.persist("New build step parameter added");
    return BuildTypeUtil.getParameter(parameterName, getStep(buildType.get(), stepId).getParameters(), false, false, myServiceLocator);
  }

  @GET
  @Path("/{btLocator}/steps/{stepId}/{fieldName}")
  @Produces({"text/plain"})
  @ApiOperation(value="Get the setting of a build step of the matching build configuration.",nickname="getBuildStepSetting")
  public String getStepSetting(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                               @PathParam("stepId") String stepId,
                               @PathParam("fieldName") String name) {
    final BuildTypeSettings buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true).get();
    final SBuildRunnerDescriptor step = getStep(buildType, stepId);
    return PropEntityStep.getSetting(buildType, step, name);
  }

  @PUT
  @Path("/{btLocator}/steps/{stepId}/{fieldName}")
  @Consumes({"text/plain"})
  @Produces({"text/plain"})
  @ApiOperation(value="Update a parameter of a build step of the matching build configuration.",nickname="setBuildStepParameter")
  public String changeStepSetting(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                  @PathParam("stepId") String stepId,
                                  @PathParam("fieldName") String name,
                                  String newValue) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final SBuildRunnerDescriptor step = getStep(buildType.get(), stepId);
    PropEntityStep.setSetting(buildType.get(), step, name, newValue);
    buildType.persist("Build step settings changed");
    return PropEntityStep.getSetting(buildType.get(), getStep(buildType.get(), stepId), name);
  }

  @NotNull
  private SBuildRunnerDescriptor getStep(@NotNull final BuildTypeSettings buildType, @NotNull final String stepId) {
    SBuildRunnerDescriptor step = buildType.findBuildRunnerById(stepId);
    if (step == null) {
      throw new NotFoundException("No step with id '" + stepId + "' is found  in the build configuration.");
    }
    return step;
  }


  @GET
  @Path("/{btLocator}/features")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all build features of the matching build configuration.",nickname="getAllBuildFeatures")
  public PropEntitiesFeature getFeatures(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                         @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    return new PropEntitiesFeature(buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  @PUT
  @Path("/{btLocator}/features")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update all build features of the matching build configuration.",nickname="replaceAllBuildFeatures")
  public PropEntitiesFeature replaceFeatures(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                             @QueryParam("fields") String fields,
                                             PropEntitiesFeature suppliedEntities) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    suppliedEntities.setToBuildType(buildType.getSettingsEx(), myServiceLocator);
    buildType.persist("Build features replaced");
    return new PropEntitiesFeature(buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  @POST
  @Path("/{btLocator}/features")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Add build feature to the matching build configuration.",nickname="addBuildFeatureToBuildType")
  public PropEntityFeature addFeature(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                      @QueryParam("fields") String fields,
                                      PropEntityFeature featureDescription) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final SBuildFeatureDescriptor newFeature = featureDescription.addTo(buildType.getSettingsEx(), myServiceLocator);
    buildType.persist("Build feature added");
    return new PropEntityFeature(newFeature, buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{btLocator}/features/{featureId}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get a build feature of the matching build configuration.",nickname="getBuildFeature")
  public PropEntityFeature getFeature(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                      @PathParam("featureId") String featureId,
                                      @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType.get(), featureId);
    return new PropEntityFeature(feature, buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{btLocator}/features/{featureId}")
  @ApiOperation(value="Remove a build feature of the matching build configuration.",nickname="deleteFeature")
  public void deleteFeature(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                            @PathParam("featureId") String id) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType.get(), id);
    PropEntityFeature.removeFrom(buildType.get(), feature);
    buildType.persist("Build feature removed");
  }

  @PUT
  @Path("/{btLocator}/features/{featureId}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update a build feature of the matching build configuration.",nickname="replaceBuildFeature")
  public PropEntityFeature replaceFeature(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                          @PathParam("featureId") String id,
                                          @QueryParam("fields") String fields,
                                          PropEntityFeature featureDescription) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType.get(), id);

    final SBuildFeatureDescriptor newFeature = featureDescription.replaceIn(buildType.getSettingsEx(), feature, myServiceLocator);

    buildType.persist("Build feature replaced");
    return new PropEntityFeature(newFeature, buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{btLocator}/features/{featureId}/parameters")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all parameters of a build feature of the matching build configuration.",nickname="getAllBuildFeatureParameters")
  public Properties getFeatureParameters(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                         @PathParam("featureId") String featureId,
                                         @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType.get(), featureId);
    return new Properties(feature.getParameters(), null, new Fields(fields), myBeanContext);
  }

  @PUT
  @Path("/{btLocator}/features/{featureId}/parameters")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update a parameter of a build feature of the matching build configuration.",nickname="replaceBuildFeatureParameters")
  public Properties replaceFeatureParameters(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                             @PathParam("featureId") String featureId,
                                             Properties properties,
                                             @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType.get(), featureId);

    buildType.get().updateBuildFeature(feature.getId(), feature.getType(), properties.getMap());
    buildType.persist("Build feature parameters replaced");
    return new Properties(BuildTypeUtil.getBuildTypeFeature(buildType.get(), featureId).getParameters(), null, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{btLocator}/features/{featureId}/parameters/{parameterName}")
  @Produces({"text/plain"})
  @ApiOperation(value="Get a parameter of a build feature of the matching build configuration.",nickname="getBuildFeatureParameter")
  public String getFeatureParameter(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                    @PathParam("featureId") String featureId,
                                    @PathParam("parameterName") String parameterName) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType.get(), featureId);
    return BuildTypeUtil.getParameter(parameterName, feature.getParameters(), true, false, myServiceLocator);
  }

  @PUT
  @Path("/{btLocator}/features/{featureId}/parameters/{parameterName}")
  @Produces({"text/plain"})
  @Consumes({"text/plain"})
  @ApiOperation(value="Update build feature parameter for the matching build configuration.",nickname="addParameterToBuildFeature")
  public String addFeatureParameter(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                    @PathParam("featureId") String featureId,
                                    @PathParam("parameterName") String parameterName,
                                    String newValue) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType.get(), featureId);
    Map<String, String> parameters = new HashMap<String, String>();
    parameters.putAll(feature.getParameters());
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    parameters.put(parameterName, newValue);
    buildType.get().updateBuildFeature(feature.getId(), feature.getType(), parameters);
    buildType.persist("Build feature parameter added");
    return BuildTypeUtil.getParameter(parameterName, BuildTypeUtil.getBuildTypeFeature(buildType.get(), featureId).getParameters(), false, false, myServiceLocator);
  }


  @GET
  @Path("/{btLocator}/features/{featureId}/{name}")
  @Produces({"text/plain"})
  @ApiOperation(value="Get the setting of a build feature of the matching build configuration.",nickname="getBuildFeatureSetting")
  public String getFeatureSetting(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                  @PathParam("featureId") String featureId,
                                  @PathParam("name") String name) {
    final BuildTypeSettings buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true).get();
    final SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType, featureId);
    return PropEntityStep.getSetting(buildType, feature.getId(), name);
  }

  @PUT
  @Path("/{btLocator}/features/{featureId}/{name}")
  @Consumes({"text/plain"})
  @Produces({"text/plain"})
  @ApiOperation(value="Update a parameter of a build feature of the matching build configuration.",nickname="setBuildFeatureParameter")
  public String changeFeatureSetting(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("featureId") String featureId,
                                     @PathParam("name") String name,
                                     String newValue) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType.get(), featureId);
    PropEntityStep.setSetting(buildType.get(), feature.getId(), name, newValue);
    buildType.persist("Build feature changed");
    return PropEntityStep.getSetting(buildType.get(), BuildTypeUtil.getBuildTypeFeature(buildType.get(), featureId).getId(), name);
  }


  @GET
  @Path("/{btLocator}/artifact-dependencies")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all artifact dependencies of the matching build configuration.",nickname="getAllArtifactDependencies")
  public PropEntitiesArtifactDep getArtifactDeps(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                                 @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    return new PropEntitiesArtifactDep(buildType.getSettingsEx(), new Fields(fields), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  /**
   * Replaces the dependencies to those sent in the request.
   */
  @PUT
  @Path("/{btLocator}/artifact-dependencies")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update all artifact dependencies of the matching build configuration.",nickname="replaceAllArtifactDependencies")
  public PropEntitiesArtifactDep replaceArtifactDeps(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                                     @QueryParam("fields") String fields,
                                                     PropEntitiesArtifactDep deps) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    deps.setToBuildType(buildType.getSettingsEx(), myServiceLocator);
    buildType.persist("Artifact dependencies replaced");
    return new PropEntitiesArtifactDep(buildType.getSettingsEx(), new Fields(fields), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  @POST
  @Path("/{btLocator}/artifact-dependencies")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Add an artifact dependency to the matching build configuration.",nickname="addArtifactDependencyToBuildType")
  public PropEntityArtifactDep addArtifactDep(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                              @QueryParam("fields") String fields,
                                              PropEntityArtifactDep description) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final SArtifactDependency result = description.addTo(buildType.getSettingsEx(), myServiceLocator);
    buildType.persist("Artifact dependency added");
    return new PropEntityArtifactDep(result, buildType.getSettingsEx(), new Fields(fields), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  @GET
  @Path("/{btLocator}/artifact-dependencies/{artifactDepLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get an artifact dependency of the matching build configuration.",nickname="getArtifactDependency")
  public PropEntityArtifactDep getArtifactDep(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                              @PathParam("artifactDepLocator") String artifactDepLocator,
                                              @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final SArtifactDependency artifactDependency = getArtifactDependency(buildType, artifactDepLocator);
    return new PropEntityArtifactDep(artifactDependency, buildType.getSettingsEx(), new Fields(fields), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  @DELETE
  @Path("/{btLocator}/artifact-dependencies/{artifactDepLocator}")
  @ApiOperation(value="Remove an artifact dependency from the matching build configuration.",nickname="deleteArtifactDependency")
  public void deleteArtifactDep(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                @PathParam("artifactDepLocator") String artifactDepLocator) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    PropEntityArtifactDep.removeFrom(buildType.get(), getArtifactDependency(buildType, artifactDepLocator));
    buildType.persist("Artifact dependency removed");
  }

  @PUT
  @Path("/{btLocator}/artifact-dependencies/{artifactDepLocator}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update an artifact dependency of the matching build configuration.",nickname="replaceArtifactDependency")
  public PropEntityArtifactDep replaceArtifactDep(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                                  @PathParam("artifactDepLocator") String artifactDepLocator,
                                                  @QueryParam("fields") String fields,
                                                  PropEntityArtifactDep description) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final SArtifactDependency newDependency = description.replaceIn(buildType.getSettingsEx(), getArtifactDependency(buildType, artifactDepLocator), myServiceLocator);
    buildType.persist("Artifact dependency replaced");
    return new PropEntityArtifactDep(newDependency, buildType.getSettingsEx(), new Fields(fields), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }


  public static SArtifactDependency getArtifactDependency(@NotNull final BuildTypeOrTemplate buildType, @NotNull final String artifactDepLocator) {
    if (StringUtil.isEmpty(artifactDepLocator)) {
      throw new BadRequestException("Empty artifact dependency locator is not supported.");
    }

    final Locator locator = new Locator(artifactDepLocator, "id", Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);

    final String artifactDepId;
    if (locator.isSingleValue()) {
      artifactDepId = locator.getSingleValue();
    } else {
      artifactDepId = locator.getSingleDimensionValue("id");
    }
    locator.checkLocatorFullyProcessed();

    if (StringUtil.isEmpty(artifactDepId)) {
      throw new BadRequestException("Cannot find id in artifact dependency locator '" + artifactDepLocator + "'");
    }

    for (SArtifactDependency dep : buildType.get().getArtifactDependencies()) {
      if (artifactDepId.equals(dep.getId())) {
        return dep;
      }
    }

    //may be it is a number: use obsolete pre-TeamCity 10 logic

    try {
      final Integer orderNumber = Integer.parseInt(artifactDepId);
      try {
        SArtifactDependency result = buildType.get().getArtifactDependencies().get(orderNumber);
        LOG.debug(
          "Found artifact dependency by order number " + orderNumber + " instead of id. This behavior is obsolete, use id (" + result.getId() + ") instead of order number.");
        return result;
      } catch (IndexOutOfBoundsException e) {
        throw new NotFoundException("Could not find artifact dependency by id '" + artifactDepId + "' in " + buildType.getText() + " with id '" + buildType.getId() + "'");
      }
    } catch (NumberFormatException e) {
      //not a number either: report error:
      throw new NotFoundException("Could not find artifact dependency by id '" + artifactDepId + "' in " + buildType.getText() + " with id '" + buildType.getId() + "'");
    }
  }

  //todo: list and allow changing parameters of the dependency
  // like for features, steps (otherwise there is no way to update dependency in a template not resetting enabled/disabled in usages)
  // Note: on adding editing should test editing of a dependnecy when inherited form a tempalte

  @GET
  @Path("/{btLocator}/artifact-dependencies/{artifactDepLocator}/{fieldName}")
  @Produces({"text/plain"})
  @ApiOperation(value="Get a parameter of an artifact dependency of the matching build configuration.",nickname="getArtifactDependencyParameter")
  public String getArtifactDepSetting(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                      @PathParam("artifactDepLocator") String artifactDepLocator,
                                      @PathParam("fieldName") String name) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final SArtifactDependency dep = getArtifactDependency(buildType, artifactDepLocator);
    return PropEntityArtifactDep.getSetting(buildType.get(), dep.getId(), name);
  }

  @PUT
  @Path("/{btLocator}/artifact-dependencies/{artifactDepLocator}/{fieldName}")
  @Consumes({"text/plain"})
  @Produces({"text/plain"})
  @ApiOperation(value="Update a parameter of an artifact dependency of the matching build configuration.",nickname="setArtifactDependencyParameter")
  public String changeArtifactDepSetting(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                         @PathParam("artifactDepLocator") String artifactDepLocator,
                                         @PathParam("fieldName") String name, String newValue) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final SArtifactDependency dep = getArtifactDependency(buildType, artifactDepLocator);
    PropEntityArtifactDep.setSetting(buildType.get(), dep.getId(), name, newValue);
    buildType.persist("Artifact dependency settings changed");
    return PropEntityArtifactDep.getSetting(buildType.get(), dep.getId(), name);
  }

  @GET
  @Path("/{btLocator}/snapshot-dependencies")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all snapshot dependencies of the matching build configuration.",nickname="getAllSnapshotDependencies")
  public PropEntitiesSnapshotDep getSnapshotDeps(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                                 @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    return new PropEntitiesSnapshotDep(buildType.getSettingsEx(), new Fields(fields), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  /**
   * Replaces snapshot dependency with those sent in request.
   */
  @PUT
  @Path("/{btLocator}/snapshot-dependencies")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update all snapshot dependencies of the matching build configuration.",nickname="replaceAllSnapshotDependencies")
  public PropEntitiesSnapshotDep replaceSnapshotDeps(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                                     @QueryParam("fields") String fields,
                                                     PropEntitiesSnapshotDep suppliedEntities) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    suppliedEntities.setToBuildType(buildType.getSettingsEx(), myServiceLocator);
    buildType.persist("Snapshot dependency replaced");
    return new PropEntitiesSnapshotDep(buildType.getSettingsEx(), new Fields(fields), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  /**
   * Creates new snapshot dependency. 'id' attribute is ignored in the submitted descriptor.
   * Reports error if new dependency cannot be created (e.g. another dependency on the specified build configuration already exists).
   */
  @POST
  @Path("/{btLocator}/snapshot-dependencies")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Add a snapshot dependency to the matching build configuration.",nickname="addSnapshotDependencyToBuildType")
  public PropEntitySnapshotDep addSnapshotDep(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                              @QueryParam("fields") String fields,
                                              PropEntitySnapshotDep description) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);

    Dependency createdDependency = description.addTo(buildType.getSettingsEx(), myServiceLocator);
    buildType.persist("Snapshot dependency added");
    return new PropEntitySnapshotDep(createdDependency, buildType.getSettingsEx(), new Fields(fields), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  @GET
  @Path("/{btLocator}/snapshot-dependencies/{snapshotDepLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get a snapshot dependency of the matching build configuration.",nickname="getSnapshotDependency")
  public PropEntitySnapshotDep getSnapshotDep(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                              @PathParam("snapshotDepLocator") String snapshotDepLocator,
                                              @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final Dependency dependency = PropEntitySnapshotDep.getSnapshotDep(buildType.get(), snapshotDepLocator, myBuildTypeFinder);
    return new PropEntitySnapshotDep(dependency, buildType.getSettingsEx(), new Fields(fields), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  @DELETE
  @Path("/{btLocator}/snapshot-dependencies/{snapshotDepLocator}")
  @ApiOperation(value="Delete a snapshot dependency of the matching build configuration.",nickname="deleteSnapshotDependency")
  public void deleteSnapshotDep(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                @PathParam("snapshotDepLocator") String snapshotDepLocator) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final Dependency dependency = PropEntitySnapshotDep.getSnapshotDep(buildType.get(), snapshotDepLocator, myBuildTypeFinder);
    PropEntitySnapshotDep.removeFrom(buildType.get(), dependency);
    buildType.persist("Snapshot dependency removed");
  }

  @PUT
  @Path("/{btLocator}/snapshot-dependencies/{snapshotDepLocator}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update a snapshot dependency of the matching build configuration.",nickname="replaceSnapshotDependency")
  public PropEntitySnapshotDep replaceSnapshotDep(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                                  @PathParam("snapshotDepLocator") String snapshotDepLocator,
                                                  @QueryParam("fields") String fields,
                                                  PropEntitySnapshotDep description) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);

    final Dependency dependency = PropEntitySnapshotDep.getSnapshotDep(buildType.get(), snapshotDepLocator, myBuildTypeFinder);
    Dependency createdDependency = description.replaceIn(buildType.getSettingsEx(), dependency, myServiceLocator);
    buildType.persist("Snapshot dependency replaced");
    return new PropEntitySnapshotDep(createdDependency, buildType.getSettingsEx(), new Fields(fields), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }


  @GET
  @Path("/{btLocator}/triggers")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all triggers of the matching build configuration.",nickname="getAllTriggers")
  public PropEntitiesTrigger getTriggers(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                         @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    return new PropEntitiesTrigger(buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  /**
   * Replaces trigger with those sent inthe request.
   */
  @PUT
  @Path("/{btLocator}/triggers")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update all triggers of the matching build configuration.",nickname="replaceAllTriggers")
  public PropEntitiesTrigger replaceTriggers(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                             @QueryParam("fields") String fields,
                                             PropEntitiesTrigger suppliedEntities) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    suppliedEntities.setToBuildType(buildType.getSettingsEx(), myServiceLocator);
    buildType.persist("Build triggers replaced");
    return new PropEntitiesTrigger(buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  /**
   * Creates new trigger. 'id' attribute is ignored in the submitted descriptor.
   * Reports error if new trigger cannot be created (e.g. only single trigger of the type is allowed for a build configuration).
   */
  @POST
  @Path("/{btLocator}/triggers")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Add a trigger to the matching build configuration.",nickname="addTriggerToBuildType")
  public PropEntityTrigger addTrigger(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                      @QueryParam("fields") String fields,
                                      PropEntityTrigger description) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);

    final BuildTriggerDescriptor justAdded = description.addTo(buildType.getSettingsEx(), myServiceLocator);

    buildType.persist("Build trigger added");

    return new PropEntityTrigger(justAdded, buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{btLocator}/triggers/{triggerLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get a trigger of the matching build configuration.",nickname="getTrigger")
  public PropEntityTrigger getTrigger(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                      @PathParam("triggerLocator") String triggerLocator,
                                      @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final BuildTriggerDescriptor trigger = DataProvider.getTrigger(buildType.get(), triggerLocator);
    return new PropEntityTrigger(trigger, buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{btLocator}/triggers/{triggerLocator}")
  @ApiOperation(value="Delete a trigger of the matching build configuration.",nickname="deleteTrigger")
  public void deleteTrigger(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                            @PathParam("triggerLocator") String triggerLocator) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final BuildTriggerDescriptor trigger = DataProvider.getTrigger(buildType.get(), triggerLocator);
    PropEntityTrigger.removeFrom(buildType.get(), trigger);
    buildType.persist("Build trigger removed");
  }

  @PUT
  @Path("/{btLocator}/triggers/{triggerLocator}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update a trigger of the matching build configuration.",nickname="replaceTrigger")
  public PropEntityTrigger replaceTrigger(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                          @PathParam("triggerLocator") String triggerLocator,
                                          @QueryParam("fields") String fields,
                                          PropEntityTrigger description) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final BuildTriggerDescriptor trigger = DataProvider.getTrigger(buildType.get(), triggerLocator);

    final BuildTriggerDescriptor justAdded = description.replaceIn(buildType.getSettingsEx(), trigger, myServiceLocator);
    buildType.persist("Build trigger replaced");
    return new PropEntityTrigger(justAdded, buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{btLocator}/triggers/{triggerLocator}/{fieldName}")
  @Produces({"text/plain"})
  @ApiOperation(value="Get a parameter of a trigger of the matching build configuration.",nickname="getTriggerParameter")
  public String getTriggerSetting(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                  @PathParam("triggerLocator") String triggerLocator,
                                  @PathParam("fieldName") String name) {
    final BuildTypeSettings buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true).get();
    final BuildTriggerDescriptor trigger = DataProvider.getTrigger(buildType, triggerLocator);
    return PropEntityStep.getSetting(buildType, trigger.getId(), name);
  }

  @PUT
  @Path("/{btLocator}/triggers/{triggerLocator}/{fieldName}")
  @Consumes({"text/plain"})
  @Produces({"text/plain"})
  @ApiOperation(value="Update a parameter of a trigger of the matching build configuration.",nickname="setTriggerParameter")
  public String changeTriggerSetting(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("triggerLocator") String triggerLocator,
                                     @PathParam("fieldName") String name,
                                     String newValue) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final BuildTriggerDescriptor trigger = DataProvider.getTrigger(buildType.get(), triggerLocator);
    PropEntityStep.setSetting(buildType.get(), trigger.getId(), name, newValue);
    buildType.persist("Build trigger settings changed");
    return PropEntityStep.getSetting(buildType.get(), DataProvider.getTrigger(buildType.get(), triggerLocator).getId(), name);
  }


  @GET
  @Path("/{btLocator}/agent-requirements")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all agent requirements of the matching build configuration.",nickname="getAllAgentRequirements")
  public PropEntitiesAgentRequirement getAgentRequirements(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                                           @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    return new PropEntitiesAgentRequirement(buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  /**
   * Replaces agent requirements with those sent in the request.
   */
  @PUT
  @Path("/{btLocator}/agent-requirements")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update all agent requirements of the matching build configuration.",nickname="replaceAllAgentRequirements")
  public PropEntitiesAgentRequirement replaceAgentRequirements(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                                               @QueryParam("fields") String fields,
                                                               PropEntitiesAgentRequirement suppliedEntities) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    suppliedEntities.setToBuildType(buildType.getSettingsEx(), myServiceLocator);
    buildType.persist("Agent requirements replaced");
    return new PropEntitiesAgentRequirement(buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  /**
   * Creates new agent requirement. 'id' attribute is ignored in the submitted descriptor.
   * Reports error if new requirement cannot be created (e.g. another requirement is present for the parameter).
   */
  @POST
  @Path("/{btLocator}/agent-requirements")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Add an agent requirement to the matching build configuration.",nickname="addAgentRequirementToBuildType")
  public PropEntityAgentRequirement addAgentRequirement(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                                        @QueryParam("fields") String fields,
                                                        PropEntityAgentRequirement description) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);

    final Requirement result = description.addTo(buildType.getSettingsEx(), myServiceLocator);
    buildType.persist("Agent requirement added");
    return new PropEntityAgentRequirement(result, buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{btLocator}/agent-requirements/{agentRequirementLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get an agent requirement of the matching build configuration.",nickname="getAgentRequirement")
  public PropEntityAgentRequirement getAgentRequirement(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                                        @PathParam("agentRequirementLocator") String agentRequirementLocator,
                                                        @QueryParam("fields") String fields) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final Requirement requirement = getAgentRequirement(buildType, agentRequirementLocator);
    return new PropEntityAgentRequirement(requirement, buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{btLocator}/agent-requirements/{agentRequirementLocator}")
  @ApiOperation(value="Remove an agent requirement of the matching build configuration.",nickname="deleteAgentRequirement")
  public void deleteAgentRequirement(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("agentRequirementLocator") String agentRequirementLocator) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final Requirement requirement = getAgentRequirement(buildType, agentRequirementLocator);
    PropEntityAgentRequirement.removeFrom(buildType.get(), requirement);
    buildType.persist("Agent requirement removed");
  }

  @PUT
  @Path("/{btLocator}/agent-requirements/{agentRequirementLocator}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update an agent requirement of the matching build configuration.",nickname="replaceAgentRequirement")
  public PropEntityAgentRequirement replaceAgentRequirement(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                                            @PathParam("agentRequirementLocator") String agentRequirementLocator,
                                                            @QueryParam("fields") String fields,
                                                            PropEntityAgentRequirement description) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);

    final Requirement requirement = getAgentRequirement(buildType, agentRequirementLocator);
    final Requirement result = description.replaceIn(buildType.getSettingsEx(), requirement, myServiceLocator);
    return new PropEntityAgentRequirement(result, buildType.getSettingsEx(), new Fields(fields), myBeanContext);
  }


  @GET
  @Path("/{btLocator}/agent-requirements/{agentRequirementLocator}/{fieldName}")
  @Produces({"text/plain"})
  @ApiOperation(value="Get a setting of an agent requirement of the matching build configuration.",nickname="getAgentRequirementParameter")
  public String getRequirementSetting(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                      @PathParam("agentRequirementLocator") String agentRequirementLocator,
                                      @PathParam("fieldName") String name) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final Requirement requirement = getAgentRequirement(buildType, agentRequirementLocator);
    String id = requirement.getId();
    if (id == null) {
      throw new BadRequestException("Could not get field of a requirement which does not have id");
    }
    return PropEntityStep.getSetting(buildType.get(), id, name);
  }

  @PUT
  @Path("/{btLocator}/agent-requirements/{agentRequirementLocator}/{fieldName}")
  @Consumes({"text/plain"})
  @Produces({"text/plain"})
  @ApiOperation(value="Update a parameter of an agent requirement of the matching build configuration.",nickname="setAgentRequirementParameter")
  public String changeRequirementSetting(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                         @PathParam("agentRequirementLocator") String agentRequirementLocator,
                                         @PathParam("fieldName") String name,
                                         String newValue) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    final Requirement requirement = getAgentRequirement(buildType, agentRequirementLocator);
    String id = requirement.getId();
    if (id == null) {
      throw new BadRequestException("Could not get field of a requirement which does not have id");
    }
    PropEntityStep.setSetting(buildType.get(), id, name, newValue);
    buildType.persist("Agent requirement settings changed");
    return PropEntityStep.getSetting(buildType.get(), id, name);
  }

  public static Requirement getAgentRequirement(@NotNull final BuildTypeOrTemplate buildType,
                                                @Nullable final String agentRequirementLocator) {
    if (StringUtil.isEmpty(agentRequirementLocator)) {
      throw new BadRequestException("Empty agent requirement locator is not supported.");
    }

    final Locator locator = new Locator(agentRequirementLocator);

    final String requirementId;
    if (locator.isSingleValue()) {
      requirementId = locator.getSingleValue();
    } else {
      requirementId = locator.getSingleDimensionValue("id");
    }
    locator.checkLocatorFullyProcessed();

    if (StringUtil.isEmpty(requirementId)) {
      throw new BadRequestException("Cannot find id in agent requirement locator '" + agentRequirementLocator + "'");
    }

    for (Requirement requirement : buildType.get().getRequirements()) {
      String id = requirement.getId();
      if (requirementId.equals(id != null ? id : requirement.getPropertyName())) {
        return requirement;
      }
    }

    //may be it is a property name: use obsolete pre-TeamCity 10 logic
    for (Requirement requirement : buildType.get().getRequirements()) {
      if (requirementId.equals(requirement.getPropertyName())) {
        LOG.debug("Found agent requirement by parameter name '" + requirementId + "' instead of id." +
                  (requirement.getId() != null ? " This behavior is obsolete, use id (" + requirement.getId() + ") instead of parameter name." : ""));
        return requirement;
      }
    }

    throw new NotFoundException("Could not find agent requirement by id '" + requirementId + "' in " + buildType.getText() + " with id '" + buildType.getId() + "'");
  }

  @GET
  @Path("/{btLocator}/investigations")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all investigations of the matching build configuration.",nickname="getAllInvestigations")
  public Investigations getInvestigations(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                          @QueryParam("fields") String fields) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, false);
    return new Investigations(myInvestigationFinder.getInvestigationWrappersForBuildType(buildType),
                              new PagerData(InvestigationRequest.getHref(buildType)), new Fields(fields), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  /**
   * Experimental support only!
   */
  @Deprecated
  @GET
  @Path("/{btLocator}/vcs-root-instances")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="getCurrentVcsInstancesObsolete",hidden = true)
  public VcsRootInstances getCurrentVcsInstancesObsolete(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") final String buildTypeLocator,
                                                         @QueryParam("fields") final String fields) {
    return getCurrentVcsInstances(buildTypeLocator, fields);
  }

  /**
   * Experimental support only!
   */
  @GET
  @Path("/{btLocator}/vcsRootInstances")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all VCS root instances of the matching build configuration.",nickname="getVcsRootInstances")
  public VcsRootInstances getCurrentVcsInstances(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") final String buildTypeLocator,
                                                 @QueryParam("fields") final String fields) {
    return new VcsRootInstances(new CachingValue<Collection<VcsRootInstance>>() {
      @NotNull
      @Override
      protected Collection<VcsRootInstance> doGet() {
        final SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, true);
        return buildType.getVcsRootInstances();
      }
    }, null, new Fields(fields), myBeanContext);
  }

  /**
   * Serves builds matching supplied condition.
   *
   * @param locator           Build locator to filter builds
   * @param buildTypeLocator  Deprecated, use "locator" parameter instead
   * @param status            Deprecated, use "locator" parameter instead
   * @param userLocator       Deprecated, use "locator" parameter instead
   * @param includePersonal   Deprecated, use "locator" parameter instead
   * @param includeCanceled   Deprecated, use "locator" parameter instead
   * @param onlyPinned        Deprecated, use "locator" parameter instead
   * @param tags              Deprecated, use "locator" parameter instead
   * @param agentName         Deprecated, use "locator" parameter instead
   * @param sinceBuildLocator Deprecated, use "locator" parameter instead
   * @param sinceDate         Deprecated, use "locator" parameter instead
   * @param start             Deprecated, use "locator" parameter instead
   * @param count             Deprecated, use "locator" parameter instead, defaults to 100
   * @return
   */
  @GET
  @Path("/{btLocator}/builds")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get builds of the matching build configuration.",nickname="getBuildTypeBuilds")
  public Builds serveBuilds(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                            @ApiParam(hidden = true) @QueryParam("status") String status,
                            @ApiParam(hidden = true) @QueryParam("triggeredByUser") String userLocator,
                            @ApiParam(hidden = true) @QueryParam("includePersonal") boolean includePersonal,
                            @ApiParam(hidden = true) @QueryParam("includeCanceled") boolean includeCanceled,
                            @ApiParam(hidden = true) @QueryParam("onlyPinned") boolean onlyPinned,
                            @ApiParam(hidden = true) @QueryParam("tag") List<String> tags,
                            @ApiParam(hidden = true) @QueryParam("agentName") String agentName,
                            @ApiParam(hidden = true) @QueryParam("sinceBuild") String sinceBuildLocator,
                            @ApiParam(hidden = true) @QueryParam("sinceDate") String sinceDate,
                            @ApiParam(hidden = true) @QueryParam("start") Long start,
                            @ApiParam(hidden = true) @QueryParam("count") Integer count,
                            @ApiParam(hidden = true) @QueryParam("locator") String locator,
                            @QueryParam("fields") String fields,
                            @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, false);

    return myBuildFinder.getBuildsForRequest(buildType, status, userLocator, includePersonal, includeCanceled, onlyPinned, tags, agentName,
                                           sinceBuildLocator, sinceDate, start, count, locator, "locator", uriInfo, request,  new Fields(fields), myBeanContext
    );
  }

  @GET
  @Path("/{btLocator}/builds/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="serveBuildWithProject",hidden=true)
  public Build serveBuildWithProject(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                     @ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                     @QueryParam("fields") String fields) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, false);
    BuildPromotion build = myBuildFinder.getBuildPromotion(buildType, buildLocator);
    return new Build(build,  new Fields(fields), myBeanContext);
  }


  @GET
  @Path("/{btLocator}/builds/{buildLocator}/{field}")
  @Produces("text/plain")
  @ApiOperation(value="serveBuildField",hidden=true)
  public String serveBuildField(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                @ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                @PathParam("field") String field) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, false);
    BuildPromotion build = myBuildFinder.getBuildPromotion(buildType, buildLocator);

    return Build.getFieldValue(build, field, new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  /**
   * Lists branches of the build type.
   * @param buildTypeLocator
   * @param branchesLocator experimental use only!
   * @return
   */
  @GET
  @Path("/{btLocator}/branches")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all branches of the matching build configuration.",nickname="getAllBranches")
  public Branches serveBranches(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                @QueryParam("locator") String branchesLocator,
                                @QueryParam("fields") String fieldsSpec) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, false);
    Fields fields = new Fields(fieldsSpec);

    return new Branches(myBranchFinder.getItems(buildType, branchesLocator).myEntries,
                        new PagerData(BuildTypeRequest.getBranchesHref(buildType, branchesLocator)), fields, myBeanContext);
  }

  /**
   * Checks if there are any branches satisfying given locator.
   * @param buildTypeLocator
   * @param branchLocator
   * @return
   */
  @GET
  @Path("/{btLocator}/branches/{branchLocator}/exists")
  @Produces("text/plain")
  @ApiOperation(value="Check if exists branch satisfying given locator in the matching build configuration.", nickname="checkIfBranchExists", hidden = true)
  public String checkIfBranchExists(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                    @ApiParam(format = LocatorName.BRANCH) @PathParam("branchLocator") String branchLocator) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, false);
    String fullLocator = Locator.setDimensionIfNotPresent(branchLocator, BUILD_TYPE, myBuildTypeFinder.getCanonicalLocator(new BuildTypeOrTemplate(buildType)));

    return Boolean.toString(myBranchFinder.itemsExist(new Locator(fullLocator)));
  }

  /**
   * Gets VCS labeling settings
   * Experimental support only
   * @deprecated VCS labeling configuration is moved to build features settings.
   */
  @GET
  @ApiOperation(value = "getVCSLabelingOptions", hidden = true)
  @Path("/{btLocator}/vcsLabeling")
  @Produces({"application/xml", "application/json"})
  public VCSLabelingOptions getVCSLabelingOptions(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator) {
    throw new BadRequestException("VCS labeling configuration is moved to build features settings." +
                                  " List build features of type '" + VcsLabelingBuildFeature.VCS_LABELING_TYPE + "' to get VCS labeling settings.");
  }

  public static final String WHERE_NOTE = "current sources of the build configuration";

  /**
   * Experimental support only
   */
  @Path("/{btLocator}" + VCS_FILES_LATEST)
  public FilesSubResource getVcsFilesSubResource(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                                 @QueryParam("resolveParameters") final Boolean resolveParameters) {
    final BuildTypeEx buildType = (BuildTypeEx)myBuildTypeFinder.getBuildType(null, buildTypeLocator, false);
    myPermissionChecker.checkProjectPermission(Permission.VIEW_FILE_CONTENT, buildType.getProjectId());

    final String urlPrefix = getUrlPrefix(buildType);

    return new FilesSubResource(new FilesSubResource.Provider() {
      @Override
      @NotNull
      public Element getElement(@NotNull final String path, @NotNull Purpose purpose) {
        return BuildArtifactsFinder.getItem(buildType.getVcsFilesBrowser(), path, WHERE_NOTE, myBeanContext.getServiceLocator());
      }

      @NotNull
      @Override
      public String preprocess(@Nullable final String path) {
        return getResolvedIfNecessary(buildType, path, resolveParameters);
      }

      @NotNull
      @Override
      public String getArchiveName(@NotNull final String path) {
        return replaceNonAlphaNum(replaceProjectSeparator(removeWhitespace(buildType.getFullName()))) + replaceNonAlphaNum(path) + "_sources";
      }
    }, urlPrefix, myBeanContext, false);
  }

  private String replaceProjectSeparator(final String string) {
    return PROJECT_PATH_SEPARATOR_PATTERN.matcher(string).replaceAll("_");
  }

  private String removeWhitespace(final String string) {
    return WHITESPACE_PATTERN.matcher(string).replaceAll("");
  }

  private String replaceNonAlphaNum(final @NotNull String path) {
    return NON_ALPHA_NUM_PATTERN.matcher(path).replaceAll("_");
  }


  @NotNull
  private String getUrlPrefix(final SBuildType buildType) {
    return Util.concatenatePath(myBeanContext.getApiUrlBuilder().getHref(new BuildTypeOrTemplate(buildType)), VCS_FILES_LATEST);
  }

  @NotNull
  private String getResolvedIfNecessary(@NotNull final BuildTypeEx buildType, @Nullable final String value, @Nullable final Boolean resolveSupported) {
    if (resolveSupported == null || !resolveSupported || StringUtil.isEmpty(value)) {
      return value == null ? "" : value;
    }
    myPermissionChecker.checkProjectPermission(Permission.VIEW_BUILD_RUNTIME_DATA, buildType.getProjectId());
    final ProcessingResult resolveResult = buildType.getValueResolver().resolve(value);
    return resolveResult.getResult();
  }

  /**
   * Sets VCS labeling settings
   * Experimental support only
   * @deprecated VCS labeling configuration is moved to build features settings.
   */
  @PUT
  @ApiOperation(value = "setVCSLabelingOptions", hidden = true)
  @Path("/{btLocator}/vcsLabeling")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public VCSLabelingOptions setVCSLabelingOptions(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                                  VCSLabelingOptions options) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, true);
    options.applyTo(buildType, new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
    buildType.persist("VCS labeling settings changed");
    return new VCSLabelingOptions();
  }

  /**
   * For compatibility with experimental feature of 8.0
   */
  @GET
  @ApiOperation(value = "getExampleNewProjectDescriptionCompatibilityVersion", hidden = true)
  @Path("/{btLocator}/newBuildTypeDescription")
  @Produces({"application/xml", "application/json"})
  public NewBuildTypeDescription getExampleNewProjectDescriptionCompatibilityVersion(@PathParam("btLocator") String buildTypeLocator){
    return getExampleNewProjectDescription(buildTypeLocator);
  }

  /**
   * Experimental support only.
   * Use this to get an example of the bean to be posted to the /buildTypes request to create a new build type
   * @param projectLocator
   * @return
   */
  @GET
  @ApiOperation(value = "getExampleNewProjectDescription", hidden = true)
  @Path("/{btLocator}/example/newBuildTypeDescription")
  @Produces({"application/xml", "application/json"})
  public NewBuildTypeDescription getExampleNewProjectDescription(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator){
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, false);
    final BuildType buildTypeRef = new BuildType(buildType, Fields.SHORT, myBeanContext);
    return new NewBuildTypeDescription(buildType.getName(), buildType.getId(), buildTypeRef, true, myBeanContext);
  }

  /**
   * Experimental support only
   */
  @GET
  @Path("/{btLocator}/settingsFile")
  @Produces({"text/plain"})
  @ApiOperation(value="Get the settings file of the matching build configuration.",nickname="getBuildTypeSettingsFile")
  public String getSettingsFile(@ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator) {
    myPermissionChecker.checkGlobalPermission(Permission.VIEW_SERVER_SETTINGS);
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator, false);
    return buildType.getIdentity().getConfigurationFile().getAbsolutePath();
  }

  ///**
  // * Experimental support only
  // */
  //@GET
  //@Path("/{btLocator}/difference")
  //@Produces({"text/plain"})
  //public String getDiff(@PathParam("btLocator") String buildTypeLocator1, @QueryParam("another") String buildTypeLocator2, @QueryParam("checkInherited") boolean checkInherited) {
  //  final BuildTypeOrTemplate buildType1 = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator1, true);
  //  final BuildTypeOrTemplate buildType2 = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator2, true);
  //  return BuildTypeUtil.compareBuildTypes(buildType1.getSettingsEx(), buildType2.getSettingsEx(), checkInherited, true);
  //}

  public void initForTests(@NotNull final BeanContext beanContext) {
    myBeanContext = beanContext;
    myServiceLocator = beanContext.getServiceLocator();
    myBuildTypeFinder = myBeanContext.getSingletonService(BuildTypeFinder.class);
    myApiUrlBuilder = beanContext.getApiUrlBuilder();
    myVcsRootFinder = myBeanContext.getSingletonService(VcsRootFinder.class);
  }

  public static class BuildTypeSettingsEntityWithParams implements ParametersPersistableEntity {
    @NotNull private final BuildTypeOrTemplate myBuildType;

    public BuildTypeSettingsEntityWithParams(@NotNull final BuildTypeOrTemplate buildType) {
      myBuildType = buildType;
    }

    @Override
    public void persist(@NotNull String description) {
      myBuildType.persist(description);
    }

    @Nullable
    @Override
    public Collection<Parameter> getOwnParametersCollection() {
      return Properties.convertToSimpleParameters(BuildTypeUtil.getSettingsParameters(myBuildType, null, true, false));
    }

    @Nullable
    @Override
    public Parameter getOwnParameter(@NotNull final String paramName) {
      String value = BuildTypeUtil.getSettingsParameters(myBuildType, null, true, false).get(paramName);
      if (value != null) {
        return new SimpleParameter(paramName, value);
      }
      return null;
    }

    @Override
    public void addParameter(@NotNull final Parameter param) {
      if (param.getControlDescription() != null) throw new BadRequestException("Type is not supported for settings.");
      try {
        BuildTypeUtil.setSettingsParameter(myBuildType, param.getName(), param.getValue());
      } catch (IllegalArgumentException e) {
        throw new BadRequestException(
          "Could not set setting parameter with name '" + param.getName() + "' to value '" + param.getValue() + "'. Error: " + e.getMessage());
      }
    }

    @Override
    public void removeParameter(@NotNull final String paramName) {
      BuildTypeUtil.resetSettingsParameter(myBuildType, paramName);
    }

    @Nullable
    @Override
    public Parameter getParameter(@NotNull final String paramName) {
      String value = BuildTypeUtil.getSettingsParameters(myBuildType, null, null, null).get(paramName);
      if (value != null) {
        return new SimpleParameter(paramName, value);
      }
      return null;
    }

    @NotNull
    @Override
    public Collection<Parameter> getParametersCollection(@Nullable final Locator locator) {
      return Properties.convertToSimpleParameters(BuildTypeUtil.getSettingsParameters(myBuildType, locator, null, false));
    }
  }
}
