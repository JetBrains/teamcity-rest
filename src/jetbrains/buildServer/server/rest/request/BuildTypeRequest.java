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

package jetbrains.buildServer.server.rest.request;

import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptorFactory;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Property;
import jetbrains.buildServer.server.rest.model.build.Branch;
import jetbrains.buildServer.server.rest.model.build.*;
import jetbrains.buildServer.server.rest.model.buildType.*;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.dependency.Dependency;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;

/*
 * User: Yegor Yarko
 * Date: 22.03.2009
 */

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produces 404
*/

@Path(BuildTypeRequest.API_BUILD_TYPES_URL)
public class BuildTypeRequest {
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private BuildFinder myBuildFinder;
  @Context @NotNull private BuildTypeFinder myBuildTypeFinder;
  @Context @NotNull private VcsRootFinder myVcsRootFinder;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BeanFactory myFactory;

  public static final String API_BUILD_TYPES_URL = Constants.API_URL + "/buildTypes";

  public static String getBuildTypeHref(@NotNull final BuildTypeOrTemplate buildType) {
    return buildType.isBuildType() ? getBuildTypeHref(buildType.getBuildType()) : getBuildTypeHref(buildType.getTemplate());
  }

  public static String getBuildTypeHref(@NotNull SBuildType buildType) {
    return API_BUILD_TYPES_URL + "/id:" + buildType.getExternalId();
  }

  public static String getBuildTypeHref(@NotNull final BuildTypeTemplate template) {
    return API_BUILD_TYPES_URL + "/id:"+ template.getExternalId();
  }


  public static String getBuildsHref(final SBuildType buildType) {
    return getBuildTypeHref(buildType) + "/builds/";
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public BuildTypes serveBuildTypesXML() {
    return BuildTypes.createFromBuildTypes(myDataProvider.getServer().getProjectManager().getAllBuildTypes(), myDataProvider,
                                           myApiUrlBuilder);
  }

  /**
   * Serves build configuration or templates according to the locator.
   */
  @GET
  @Path("/{btLocator}")
  @Produces({"application/xml", "application/json"})
  public BuildType serveBuildTypeXML(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new BuildType(buildType, myDataProvider, myApiUrlBuilder);
  }

  @DELETE
  @Path("/{btLocator}")
  public void deleteBuildType(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    buildType.remove();
  }

  @GET
  @Path("/{btLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildTypeField(@PathParam("btLocator") String buildTypeLocator, @PathParam("field") String fieldName) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    return buildType.getFieldValue(fieldName);
  }

  @PUT
  @Path("/{btLocator}/{field}")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setBuildTypeField(@PathParam("btLocator") String buildTypeLocator, @PathParam("field") String fieldName, String newValue) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    buildType.setFieldValue(fieldName, newValue, myDataProvider);
    buildType.get().persist();
    return buildType.getFieldValue(fieldName);
  }

  @GET
  @Path("/{btLocator}/buildTags")
  @Produces({"application/xml", "application/json"})
  public Tags serveBuildTypeBuildsTags(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator);
    return new Tags(buildType.getTags());
  }

  @GET
  @Path("/{btLocator}/parameters")
  @Produces({"application/xml", "application/json"})
  public Properties serveBuildTypeParameters(@PathParam("btLocator") String buildTypeLocator,
                                             @QueryParam("locator") Locator locator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    if (locator == null){
      return new Properties(buildType.get().getParameters());
    }
    final Boolean own = locator.getSingleDimensionValueAsBoolean("own");
    if (own == null){
      locator.checkLocatorFullyProcessed();
      return new Properties(buildType.get().getParameters());
    }
    if (own){
      // todo (TeamCity) open API: how to get only own parameters?
      throw new BadRequestException("Sorry, getting only own parameters is not supported at the moment");
    }else{
      throw new BadRequestException("Sorry, getting only not own parameters is not supported at the moment");
    }
  }

  @PUT
  @Path("/{btLocator}/parameters")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Properties changeBuildTypeParameters(@PathParam("btLocator") String buildTypeLocator, Properties properties) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    BuildTypeUtil.removeAllParameters(buildType.get());
    if (properties.properties != null) {
      for (Property p : properties.properties) {
        BuildTypeUtil.changeParameter(p.name, p.value, buildType.get(), myServiceLocator);
      }
    }
    buildType.get().persist();
    return new Properties(buildType.get().getParameters());
  }

  @DELETE
  @Path("/{btLocator}/parameters")
  public void deleteAllBuildTypeParameters(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    BuildTypeUtil.removeAllParameters(buildType.get());
    buildType.get().persist();
  }

  @GET
  @Path("/{btLocator}/parameters/{name}")
  @Produces("text/plain")
  public String serveBuildTypeParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("name") String parameterName) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    return BuildTypeUtil.getParameter(parameterName, buildType.get(), true, false);
  }

  @PUT
  @Path("/{btLocator}/parameters/{name}")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String putBuildTypeParameter(@PathParam("btLocator") String buildTypeLocator,
                                    @PathParam("name") String parameterName,
                                    String newValue) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    BuildTypeUtil.changeParameter(parameterName, newValue, buildType.get(), myServiceLocator);
    buildType.get().persist();
    return BuildTypeUtil.getParameter(parameterName, buildType.get(), false, false);
  }

  @DELETE
  @Path("/{btLocator}/parameters/{name}")
  @Produces("text/plain")
  public void deleteBuildTypeParameter(@PathParam("btLocator") String buildTypeLocator,
                                       @PathParam("name") String parameterName) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    BuildTypeUtil.deleteParameter(parameterName, buildType.get());
    buildType.get().persist();
  }


  @GET
  @Path("/{btLocator}/settings")
  @Produces({"application/xml", "application/json"})
  public Properties serveBuildTypeSettings(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new Properties(BuildTypeUtil.getSettingsParameters(buildType));
  }

  @PUT
  @Path("/{btLocator}/settings")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Properties replaceBuildTypeSettings(@PathParam("btLocator") String buildTypeLocator, Properties suppliedEntities) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    //todo: TeamCity API: how to reset settings to defaults?
    if (suppliedEntities.properties != null) {
      for (Property property : suppliedEntities.properties) {
        setSetting(buildType, property.name, property.value);
      }
    }
    buildType.get().persist();
    return new Properties(BuildTypeUtil.getSettingsParameters(buildType));
  }

  @GET
  @Path("/{btLocator}/settings/{name}")
  @Produces("text/plain")
  public String serveBuildTypeSettings(@PathParam("btLocator") String buildTypeLocator, @PathParam("name") String parameterName) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Setting parameter name cannot be empty.");
    }

    return getSetting(buildType, parameterName);
  }

  private String getSetting(final BuildTypeOrTemplate buildType, final String parameterName) {
    Map<String, String> parameters = BuildTypeUtil.getSettingsParameters(buildType);
    if (parameters.containsKey(parameterName)) {
      return parameters.get(parameterName);
    }
    throw new NotFoundException("No setting parameter with name '" + parameterName + "' is found.");
  }

  @PUT
  @Path("/{btLocator}/settings/{name}")
  @Produces("text/plain")
  public String putBuildTypeSetting(@PathParam("btLocator") String buildTypeLocator,
                                  @PathParam("name") String parameterName,
                                  String newValue) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    setSetting(buildType, parameterName, newValue);
    buildType.get().persist();
    return getSetting(buildType, parameterName);
  }

  private void setSetting(final BuildTypeOrTemplate buildType, final String parameterName, final String newValue) {
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Settings parameter name cannot be empty.");
    }

    if (!BuildTypeUtil.getSettingsParameters(buildType).containsKey(parameterName)) {
      throw new BadRequestException("Setting parameter with name '" + parameterName + "' is not known.");
    }

    try {
      BuildTypeUtil.setSettingsParameter(buildType, parameterName, newValue);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(
        "Could not set setting parameter with name '" + parameterName + "' to value '" + newValue + "'. Error: " + e.getMessage());
    }
  }


  @GET
  @Path("/{btLocator}/template")
  @Produces({"application/xml", "application/json"})
  public BuildTypeRef serveBuildTypeTemplate(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator);
    final BuildTypeTemplate template = buildType.getTemplate();
    if (template == null) {
      throw new NotFoundException("No template associated."); //todo: how to report it duly?
    }
    return new BuildTypeRef(template, myDataProvider, myApiUrlBuilder);
  }

  @PUT
  @Path("/{btLocator}/template")
  @Consumes("text/plain")
  @Produces({"application/xml", "application/json"})
  public BuildTypeRef getTemplateAssociation(@PathParam("btLocator") String buildTypeLocator, String templateLocator) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator);
    BuildTypeTemplate template = myBuildTypeFinder.getBuildTemplate(null, templateLocator);
    buildType.attachToTemplate(template);
    buildType.persist();
    return new BuildTypeRef(template, myDataProvider, myApiUrlBuilder);
  }
//todo: allow also to post back the XML from GET request (http://devnet.jetbrains.net/message/5466528#5466528)

  @DELETE
  @Path("/{btLocator}/template")
  public void deleteTemplateAssociation(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator);
    buildType.detachFromTemplate();
    buildType.persist();
  }


  @GET
  @Path("/{btLocator}/vcs-root-entries")
  @Produces({"application/xml", "application/json"})
  public VcsRootEntries getVcsRootEntries(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new VcsRootEntries(buildType, myApiUrlBuilder);
  }

  @PUT
  @Path("/{btLocator}/vcs-root-entries")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public VcsRootEntries replaceVcsRootEntries(@PathParam("btLocator") String buildTypeLocator, VcsRootEntries suppliedEntities) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    for (jetbrains.buildServer.vcs.VcsRootEntry entry : buildType.get().getVcsRootEntries()) {
      buildType.get().removeVcsRoot((SVcsRoot)entry.getVcsRoot());
    }
    if (suppliedEntities.vcsRootAssignments != null) {
      for (VcsRootEntry entity : suppliedEntities.vcsRootAssignments) {
        addVcsRoot(buildType, entity);
      }
    }
    buildType.get().persist();
    // not handlingsetting errors... a bit complex here
    return new VcsRootEntries(buildType, myApiUrlBuilder);
  }

  @POST
  @Path("/{btLocator}/vcs-root-entries")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public VcsRootEntry addVcsRootEntry(@PathParam("btLocator") String buildTypeLocator, VcsRootEntry description) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    final SVcsRoot vcsRoot = addVcsRoot(buildType, description);
    buildType.get().persist();

    return new VcsRootEntry(vcsRoot, buildType, myApiUrlBuilder);
  }

  private SVcsRoot addVcsRoot(final BuildTypeOrTemplate buildType, final VcsRootEntry description) {
    if (description.vcsRootRef == null){
      throw new BadRequestException("Element vcs-root should be specified.");
    }
    final SVcsRoot vcsRoot = description.vcsRootRef.getVcsRoot(myVcsRootFinder);

    try {
      buildType.get().addVcsRoot(vcsRoot);
    } catch (InvalidVcsRootScopeException e) {
      throw new BadRequestException("Could not attach VCS root with id '" + vcsRoot.getExternalId() + "' because of scope issues. Error: " + e.getMessage());
    }
    buildType.get().setCheckoutRules(vcsRoot, new CheckoutRules(description.checkoutRules != null ? description.checkoutRules : ""));

    return vcsRoot;
  }

  @GET
  @Path("/{btLocator}/vcs-root-entries/{id}")
  @Produces({"application/xml", "application/json"})
  public VcsRootEntry getVcsRootEntry(@PathParam("btLocator") String buildTypeLocator, @PathParam("id") String vcsRootLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    final SVcsRoot vcsRoot = myVcsRootFinder.getVcsRoot(vcsRootLocator);

    if (!buildType.get().containsVcsRoot(vcsRoot.getId())) {
      throw new NotFoundException("VCS root with id '" + vcsRoot.getExternalId() + "' is not attached to the build type.");
    }
    return new VcsRootEntry(vcsRoot, buildType, myApiUrlBuilder);
  }

  @PUT
  @Path("/{btLocator}/vcs-root-entries/{id}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public VcsRootEntry updateVcsRootEntry(@PathParam("btLocator") String buildTypeLocator, @PathParam("id") String vcsRootLocator, VcsRootEntry entry) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    final SVcsRoot vcsRoot = myVcsRootFinder.getVcsRoot(vcsRootLocator);

    if (!buildType.get().containsVcsRoot(vcsRoot.getId())) {
      throw new NotFoundException("VCS root with id '" + vcsRoot.getExternalId() + "' is not attached to the build type.");
    }
    if (entry == null){
      throw new BadRequestException("No VCS root entry description is posted (Use GET request to get an example).");
    }
    if (entry.vcsRootRef == null){
      throw new BadRequestException("No VCS root is specified in the entry description.");
    }
    buildType.get().removeVcsRoot(vcsRoot);
    final SVcsRoot resultVcsRoot = addVcsRoot(buildType, entry);
    buildType.get().persist();
    //not handling setting errors...
    return new VcsRootEntry(resultVcsRoot, buildType, myApiUrlBuilder);
  }

  @GET
  @Path("/{btLocator}/vcs-root-entries/{id}/" + VcsRootEntry.CHECKOUT_RULES)
  @Produces({"text/plain"})
  public String getVcsRootEntryCheckoutRules(@PathParam("btLocator") String buildTypeLocator, @PathParam("id") String vcsRootLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    final SVcsRoot vcsRoot = myVcsRootFinder.getVcsRoot(vcsRootLocator);

    if (!buildType.get().containsVcsRoot(vcsRoot.getId())) {
      throw new NotFoundException("VCS root with id '" + vcsRoot.getExternalId() + "' is not attached to the build type.");
    }
    return buildType.get().getCheckoutRules(vcsRoot).getAsString();
  }

  @PUT
  @Path("/{btLocator}/vcs-root-entries/{id}/" + VcsRootEntry.CHECKOUT_RULES)
  @Consumes({"text/plain"})
  @Produces({"text/plain"})
  public String updateVcsRootEntryCheckoutRules(@PathParam("btLocator") String buildTypeLocator, @PathParam("id") String vcsRootLocator, String newCheckoutRules) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    final SVcsRoot vcsRoot = myVcsRootFinder.getVcsRoot(vcsRootLocator);

    if (!buildType.get().containsVcsRoot(vcsRoot.getId())) {
      throw new NotFoundException("VCS root with id '" + vcsRoot.getExternalId() + "' is not attached to the build type.");
    }
    buildType.get().setCheckoutRules(vcsRoot, new CheckoutRules(newCheckoutRules != null ? newCheckoutRules : ""));

    buildType.get().persist();
    //not handling setting errors...
    return buildType.get().getCheckoutRules(vcsRoot).getAsString();
  }

  @DELETE
  @Path("/{btLocator}/vcs-root-entries/{id}")
  public void deleteVcsRootEntry(@PathParam("btLocator") String buildTypeLocator, @PathParam("id") String vcsRootLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    final SVcsRoot vcsRoot = myVcsRootFinder.getVcsRoot(vcsRootLocator); //this assumes VCS root id are unique throughout the server
    if (!buildType.get().containsVcsRoot(vcsRoot.getId())) {
      throw new NotFoundException("VCS root with id '" + vcsRoot.getExternalId() + "' is not attached to the build type.");
    }
    buildType.get().removeVcsRoot(vcsRoot);
    buildType.get().persist();
  }


  @GET
  @Path("/{btLocator}/steps")
  @Produces({"application/xml", "application/json"})
  public PropEntitiesStep getSteps(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new PropEntitiesStep(buildType.get());
  }

  @PUT
  @Path("/{btLocator}/steps")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public PropEntitiesStep replaceSteps(@PathParam("btLocator") String buildTypeLocator, PropEntitiesStep suppliedEntities) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    final Collection<SBuildRunnerDescriptor> originals = buildType.get().getBuildRunners();
    removeSteps(buildType, originals);
    try {
      if (suppliedEntities.propEntities != null) {
        for (PropEntityStep entity : suppliedEntities.propEntities) {
          entity.addStep(buildType.get(), myServiceLocator.getSingletonService(BuildRunnerDescriptorFactory.class));
        }
      }
      buildType.get().persist();
    }catch (Exception e){
      //restore original settings
      removeSteps(buildType, buildType.get().getBuildRunners());
      for (SBuildRunnerDescriptor entry : originals) {
        buildType.get().addBuildRunner(entry);
      }
      buildType.get().persist();
      throw new BadRequestException("Error replacing items", e);
    }
    return new PropEntitiesStep(buildType.get());
  }

  private void removeSteps(final BuildTypeOrTemplate buildType, final Collection<SBuildRunnerDescriptor> runners) {
    for (SBuildRunnerDescriptor entry : runners) {
      buildType.get().removeBuildRunner(entry.getId());  //todo: (TeamCity API): why srting and not ojbect?
    }
  }

  @POST
  @Path("/{btLocator}/steps")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public PropEntityStep addStep(@PathParam("btLocator") String buildTypeLocator, PropEntityStep stepDescription) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    final SBuildRunnerDescriptor newRunner =
      stepDescription.addStep(buildType.get(), myServiceLocator.getSingletonService(BuildRunnerDescriptorFactory.class));
    buildType.get().persist();
    return new PropEntityStep(newRunner, buildType.get());
  }

  @GET
  @Path("/{btLocator}/steps/{stepId}")
  @Produces({"application/xml", "application/json"})
  public PropEntityStep getStep(@PathParam("btLocator") String buildTypeLocator, @PathParam("stepId") String stepId) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    SBuildRunnerDescriptor step = buildType.get().findBuildRunnerById(stepId);
    if (step == null) {
      throw new NotFoundException("No step with id '" + stepId + "' is found.");
    }
    return new PropEntityStep(step, buildType.get());
  }

  @DELETE
  @Path("/{btLocator}/steps/{stepId}")
  public void deleteStep(@PathParam("btLocator") String buildTypeLocator, @PathParam("stepId") String stepId) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    SBuildRunnerDescriptor step = buildType.get().findBuildRunnerById(stepId);
    if (step == null) {
      throw new NotFoundException("No step with id '" + stepId + "' is found.");
    }
    buildType.get().removeBuildRunner(stepId);
    buildType.get().persist();
  }

  @GET
  @Path("/{btLocator}/steps/{stepId}/parameters")
  @Produces({"application/xml", "application/json"})
  public Properties getStepParameters(@PathParam("btLocator") String buildTypeLocator,  @PathParam("stepId") String stepId) {
    SBuildRunnerDescriptor step = getBuildTypeStep(myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator
    ).get(), stepId);
    return new Properties(step.getParameters());
  }

  @PUT
  @Path("/{btLocator}/steps/{stepId}/parameters")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Properties replaceStepParameters(@PathParam("btLocator") String buildTypeLocator,
                                          @PathParam("stepId") String stepId,
                                          Properties properties) {
    final BuildTypeSettings buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator).get();
    SBuildRunnerDescriptor step = getBuildTypeStep(buildType, stepId);

    buildType.updateBuildRunner(step.getId(), step.getName(), step.getType(), properties.getMap());
    buildType.persist();
    return new Properties(step.getParameters());
  }

  @GET
  @Path("/{btLocator}/steps/{stepId}/parameters/{parameterName}")
  @Produces({"text/plain"})
  public String getStepParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("stepId") String stepId,
                                 @PathParam("parameterName") String parameterName) {
    SBuildRunnerDescriptor step = getBuildTypeStep(myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator
    ).get(), stepId);
    return getParameterValue(step, parameterName);
  }

  private static String getParameterValue(final ParametersDescriptor parametersHolder, final String parameterName) {
    Map<String, String> stepParameters = parametersHolder.getParameters();
    if (!stepParameters.containsKey(parameterName)) {
      throw new NotFoundException("No parameter with name '" + parameterName + "' is found in the step parameters.");
    }
    return stepParameters.get(parameterName);
  }

  private SBuildRunnerDescriptor getBuildTypeStep(final BuildTypeSettings buildType, final String stepId) {
    SBuildRunnerDescriptor step = buildType.findBuildRunnerById(stepId);
    if (step == null) {
      throw new NotFoundException("No step with id '" + stepId + "' is found in the build configuration.");
    }
    return step;
  }

  @PUT
  @Path("/{btLocator}/steps/{stepId}/parameters/{parameterName}")
  @Consumes({"text/plain"})
  @Produces({"text/plain"})
  public String addStepParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("stepId") String stepId,
                               @PathParam("parameterName") String parameterName, String newValue) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    SBuildRunnerDescriptor step = getBuildTypeStep(buildType.get(), stepId);
    Map<String, String> parameters = new HashMap<String, String>(step.getParameters());
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    parameters.put(parameterName, newValue);
    buildType.get().updateBuildRunner(step.getId(), step.getName(), step.getType(), parameters);
    buildType.get().persist();
    return getParameterValue(step, parameterName);
  }

  @GET
  @Path("/{btLocator}/steps/{stepId}/{fieldName}")
  @Produces({"text/plain"})
  public String getStepSetting(@PathParam("btLocator") String buildTypeLocator, @PathParam("stepId") String stepId,
                               @PathParam("fieldName") String name) {
    final BuildTypeSettings buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator).get();
    final SBuildRunnerDescriptor step = getBuildTypeStep(buildType, stepId);
    return PropEntityStep.getSetting(buildType, step, name);
  }

  @PUT
  @Path("/{btLocator}/steps/{stepId}/{fieldName}")
  @Consumes({"text/plain"})
  @Produces({"text/plain"})
  public String changeStepSetting(@PathParam("btLocator") String buildTypeLocator, @PathParam("stepId") String stepId,
                                @PathParam("fieldName") String name, String newValue) {
    final BuildTypeSettings buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator).get();
    final SBuildRunnerDescriptor step = getBuildTypeStep(buildType, stepId);
    PropEntityStep.setSetting(buildType, step, name, newValue);
    buildType.persist();
    return PropEntityStep.getSetting(buildType, step, name);
  }


  @GET
  @Path("/{btLocator}/features")
  @Produces({"application/xml", "application/json"})
  public PropEntitiesFeature getFeatures(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new PropEntitiesFeature(buildType.get());
  }

  @PUT
  @Path("/{btLocator}/features")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public PropEntitiesFeature replaceFeatures(@PathParam("btLocator") String buildTypeLocator, PropEntitiesFeature suppliedEntities) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);

    final Collection<SBuildFeatureDescriptor> originals = buildType.get().getBuildFeatures();
    removeFeatures(buildType, originals);
    try {
      if (suppliedEntities.propEntities != null) {
        for (PropEntityFeature entity : suppliedEntities.propEntities) {
          entity.addFeature(buildType.get(), myServiceLocator.getSingletonService(BuildFeatureDescriptorFactory.class));
        }
      }
      buildType.get().persist();
    }catch (Exception e){
      //restore original settings
      removeFeatures(buildType, buildType.get().getBuildFeatures());
      for (SBuildFeatureDescriptor entry : originals) {
        buildType.get().addBuildFeature(entry);
      }
      buildType.get().persist();
      throw new BadRequestException("Error replacing items", e);
    }
    return new PropEntitiesFeature(buildType.get());
  }

  private void removeFeatures(final BuildTypeOrTemplate buildType, final Collection<SBuildFeatureDescriptor> features) {
    for (SBuildFeatureDescriptor entry : features) {
      buildType.get().removeBuildFeature(entry.getId());  //todo: (TeamCity API): why srting and not ojbect?
    }
  }

  @POST
  @Path("/{btLocator}/features")
  @Produces({"application/xml", "application/json"})
  public PropEntityFeature addFeature(@PathParam("btLocator") String buildTypeLocator, PropEntityFeature featureDescription) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    final SBuildFeatureDescriptor newFeature =
      featureDescription.addFeature(buildType.get(), myServiceLocator.getSingletonService(BuildFeatureDescriptorFactory.class));
    buildType.get().persist();
    return new PropEntityFeature(newFeature, buildType.get());
  }

  @GET
  @Path("/{btLocator}/features/{featureId}")
  @Produces({"application/xml", "application/json"})
  public PropEntityFeature getFeature(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String featureId) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType.get(), featureId);
    return new PropEntityFeature(feature, buildType.get());
  }

  @DELETE
  @Path("/{btLocator}/features/{featureId}")
  public void deleteFeature(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String id) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType.get(), id);
    buildType.get().removeBuildFeature(feature.getId());
    buildType.get().persist();
  }

  @GET
  @Path("/{btLocator}/features/{featureId}/parameters")
  @Produces({"application/xml", "application/json"})
  public Properties getFeatureParameters(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String featureId) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType.get(), featureId);
    return new Properties(feature.getParameters());
  }

  @PUT
  @Path("/{btLocator}/features/{featureId}/parameters")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Properties replaceFeatureParameters(@PathParam("btLocator") String buildTypeLocator,
                                             @PathParam("featureId") String featureId,
                                             Properties properties) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType.get(), featureId);

    buildType.get().updateBuildFeature(feature.getId(), feature.getType(), properties.getMap());
    buildType.get().persist();
    return new Properties(feature.getParameters());
  }

  @GET
  @Path("/{btLocator}/features/{featureId}/parameters/{parameterName}")
  @Produces({"text/plain"})
  public String getFeatureParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String featureId,
                                    @PathParam("parameterName") String parameterName) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType.get(), featureId);
    return feature.getParameters().get(parameterName);
  }

  @PUT
  @Path("/{btLocator}/features/{featureId}/parameters/{parameterName}")
  @Produces({"text/plain"})
  public String addFeatureParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String featureId,
                                  @PathParam("parameterName") String parameterName, String newValue) {

    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType.get(), featureId);
    Map<String, String> parameters = new HashMap<String, String>();
    parameters.putAll(feature.getParameters());
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    parameters.put(parameterName, newValue);
    buildType.get().updateBuildFeature(feature.getId(), feature.getType(), parameters);
    buildType.get().persist();
    return feature.getParameters().get(parameterName);
  }


  @GET
  @Path("/{btLocator}/features/{featureId}/{name}")
  @Produces({"text/plain"})
  public String getFeatureSetting(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String featureId,
                                  @PathParam("name") String name) {
    final BuildTypeSettings buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator).get();
    final SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType, featureId);
    return PropEntityStep.getSetting(buildType, feature, name);
  }

  @PUT
  @Path("/{btLocator}/features/{featureId}/{name}")
  @Consumes({"text/plain"})
  @Produces({"text/plain"})
  public String changeFeatureSetting(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String featureId,
                                   @PathParam("name") String name, String newValue) {
    final BuildTypeSettings buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator).get();
    final SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType, featureId);
    PropEntityStep.setSetting(buildType, feature, name, newValue);
    buildType.persist();
    return PropEntityStep.getSetting(buildType, feature, name);
  }


  @GET
  @Path("/{btLocator}/artifact-dependencies")
  @Produces({"application/xml", "application/json"})
  public PropEntitiesArtifactDep getArtifactDeps(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new PropEntitiesArtifactDep(buildType.get(), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  /**
   * Replaces the dependencies to those sent in the request.
   */
  @PUT
  @Path("/{btLocator}/artifact-dependencies")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public PropEntitiesArtifactDep replaceArtifactDeps(@PathParam("btLocator") String buildTypeLocator, PropEntitiesArtifactDep deps) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);

    final List<SArtifactDependency> originalDependencies = buildType.get().getArtifactDependencies();
    try {
      if (deps.propEntities != null){
        final List<SArtifactDependency> dependencyObjects =
          CollectionsUtil.convertCollection(deps.propEntities, new Converter<SArtifactDependency, PropEntityArtifactDep>() {
            public SArtifactDependency createFrom(@NotNull final PropEntityArtifactDep source) {
              return source.createDependency(new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
            }
          });
        buildType.get().setArtifactDependencies(dependencyObjects);
      }else{
        buildType.get().setArtifactDependencies(Collections.EMPTY_LIST);
      }
      buildType.get().persist();
    } catch (Exception e) {
      //restore previous state
      buildType.get().setArtifactDependencies(originalDependencies);
      buildType.get().persist();
      throw new BadRequestException("Error setting artifact dependencies", e);
    }
    return new PropEntitiesArtifactDep(buildType.get(), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  @POST
  @Path("/{btLocator}/artifact-dependencies")
  @Produces({"application/xml", "application/json"})
  public PropEntityArtifactDep addArtifactDep(@PathParam("btLocator") String buildTypeLocator, PropEntityArtifactDep description) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);

    final List<SArtifactDependency> dependencies = buildType.get().getArtifactDependencies();
    dependencies.add(description.createDependency(new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder)));
    int orderNum = dependencies.size() - 1;
    buildType.get().setArtifactDependencies(dependencies);
    buildType.get().persist();
    //todo: might not be a good way to get just added dependency
    return new PropEntityArtifactDep(buildType.get().getArtifactDependencies().get(orderNum), orderNum,
                                     new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  @GET
  @Path("/{btLocator}/artifact-dependencies/{artifactDepLocator}")
  @Produces({"application/xml", "application/json"})
  public PropEntityArtifactDep getArtifactDep(@PathParam("btLocator") String buildTypeLocator,
                                              @PathParam("artifactDepLocator") String artifactDepLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    final SArtifactDependency artifactDependency = DataProvider.getArtifactDep(buildType.get(), artifactDepLocator);
    return new PropEntityArtifactDep(artifactDependency, buildType.get(), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  @DELETE
  @Path("/{btLocator}/artifact-dependencies/{artifactDepLocator}")
  public void deleteArtifactDep(@PathParam("btLocator") String buildTypeLocator,
                                @PathParam("artifactDepLocator") String artifactDepLocator) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    final SArtifactDependency artifactDependency = DataProvider.getArtifactDep(buildType.get(), artifactDepLocator);
    final List<SArtifactDependency> dependencies = buildType.get().getArtifactDependencies();
    if (!dependencies.remove(artifactDependency)) {
      throw new NotFoundException("Specified artifact dependency is not found in the build type.");
    }
    buildType.get().setArtifactDependencies(dependencies);
    buildType.get().persist();
  }


  @GET
  @Path("/{btLocator}/snapshot-dependencies")
  @Produces({"application/xml", "application/json"})
  public PropEntitiesSnapshotDep getSnapshotDeps(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new PropEntitiesSnapshotDep(buildType.get(), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  /**
   * Replaces snapshot dependency with those sent in request.
   */
  @PUT
  @Path("/{btLocator}/snapshot-dependencies")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public PropEntitiesSnapshotDep replaceSnapshotDeps(@PathParam("btLocator") String buildTypeLocator, PropEntitiesSnapshotDep suppliedEntities) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);

    final List<Dependency> originalDependencies = buildType.get().getDependencies();
    removeDependencies(buildType, originalDependencies);

    try {
      if (suppliedEntities.propEntities != null) {
        for (PropEntitySnapshotDep entity : suppliedEntities.propEntities) {
          entity.addSnapshotDependency(buildType.get(), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
        }
      }
      buildType.get().persist();
    } catch (Exception e) {
      //restore original settings
      removeDependencies(buildType, buildType.get().getDependencies());
      for (Dependency dependency : originalDependencies) {
        buildType.get().addDependency(dependency);
      }
      buildType.get().persist();
      throw new BadRequestException("Error setting snapshot dependencies", e);
    }
    return new PropEntitiesSnapshotDep(buildType.get(), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  private void removeDependencies(final BuildTypeOrTemplate buildType, final List<Dependency> dependencies) {
    for (Dependency originalDependency : dependencies) {
      buildType.get().removeDependency(originalDependency);
    }
  }

  /**
   * Creates new snapshot dependency. 'id' attribute is ignored in the submitted descriptor.
   * Reports error if new dependency cannot be created (e.g. another dependency on the specified build configuration already exists).
   */
  @POST
  @Path("/{btLocator}/snapshot-dependencies")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public PropEntitySnapshotDep addSnapshotDep(@PathParam("btLocator") String buildTypeLocator, PropEntitySnapshotDep description) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);

    Dependency createdDependency =
      description.addSnapshotDependency(buildType.get(), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
    buildType.get().persist();
    return new PropEntitySnapshotDep(createdDependency, new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  @GET
  @Path("/{btLocator}/snapshot-dependencies/{snapshotDepLocator}")
  @Produces({"application/xml", "application/json"})
  public PropEntitySnapshotDep getSnapshotDep(@PathParam("btLocator") String buildTypeLocator,
                                              @PathParam("snapshotDepLocator") String snapshotDepLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    final Dependency dependency = PropEntitySnapshotDep.getSnapshotDep(buildType.get(), snapshotDepLocator, myBuildTypeFinder);
    return new PropEntitySnapshotDep(dependency, new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  @DELETE
  @Path("/{btLocator}/snapshot-dependencies/{snapshotDepLocator}")
  public void deleteSnapshotDep(@PathParam("btLocator") String buildTypeLocator,
                                @PathParam("snapshotDepLocator") String snapshotDepLocator) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    final Dependency dependency = PropEntitySnapshotDep.getSnapshotDep(buildType.get(), snapshotDepLocator, myBuildTypeFinder);
    buildType.get().removeDependency(dependency);
    buildType.get().persist();
  }


  @GET
  @Path("/{btLocator}/triggers")
  @Produces({"application/xml", "application/json"})
  public PropEntitiesTrigger getTriggers(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new PropEntitiesTrigger(buildType.get());
  }

  /**
   * Replaces trigger with those sent inthe request.
   */
  @PUT
  @Path("/{btLocator}/triggers")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public PropEntitiesTrigger replaceTriggers(@PathParam("btLocator") String buildTypeLocator, PropEntitiesTrigger suppliedEntities) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);

    final Collection<BuildTriggerDescriptor> originals = buildType.get().getBuildTriggersCollection();
    removeTriggers(buildType, originals);
    try {
      if (suppliedEntities.propEntities != null) {
        for (PropEntityTrigger entity : suppliedEntities.propEntities) {
          entity.addTrigger(buildType.get(), myServiceLocator.getSingletonService(BuildTriggerDescriptorFactory.class));
        }
      }
      buildType.get().persist();
    } catch (Exception e) {
      //restore original settings
      removeTriggers(buildType, buildType.get().getBuildTriggersCollection());
      for (BuildTriggerDescriptor entry : originals) {
        buildType.get().addBuildTrigger(entry);
      }
      buildType.get().persist();
      throw new BadRequestException("Error setting triggers", e);
    }
    return new PropEntitiesTrigger(buildType.get());
  }

  private void removeTriggers(final BuildTypeOrTemplate buildType, final Collection<BuildTriggerDescriptor> triggers) {
    for (BuildTriggerDescriptor entry : triggers) {
      buildType.get().removeBuildTrigger(entry);
    }
  }

  /**
   * Creates new trigger. 'id' attribute is ignored in the submitted descriptor.
   * Reports error if new trigger cannot be created (e.g. only single trigger of the type is allowed for a build configuration).
   */
  @POST
  @Path("/{btLocator}/triggers")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public PropEntityTrigger addTrigger(@PathParam("btLocator") String buildTypeLocator, PropEntityTrigger description) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);

    final BuildTriggerDescriptor justAdded = description.addTrigger(buildType.get(), myServiceLocator
      .getSingletonService(BuildTriggerDescriptorFactory.class));

    buildType.get().persist();

    return new PropEntityTrigger(justAdded, buildType.get());
  }

  @GET
  @Path("/{btLocator}/triggers/{triggerLocator}")
  @Produces({"application/xml", "application/json"})
  public PropEntityTrigger getTrigger(@PathParam("btLocator") String buildTypeLocator,
                                      @PathParam("triggerLocator") String triggerLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    final BuildTriggerDescriptor trigger = DataProvider.getTrigger(buildType.get(), triggerLocator);
    return new PropEntityTrigger(trigger, buildType.get());
  }

  @DELETE
  @Path("/{btLocator}/triggers/{triggerLocator}")
  public void deleteTrigger(@PathParam("btLocator") String buildTypeLocator, @PathParam("triggerLocator") String triggerLocator) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    final BuildTriggerDescriptor trigger = DataProvider.getTrigger(buildType.get(), triggerLocator);
    if (!buildType.get().removeBuildTrigger(trigger)) {
      throw new OperationException("Build trigger removal failed");
    }
    buildType.get().persist();
  }

  @GET
  @Path("/{btLocator}/triggers/{triggerLocator}/{fieldName}")
  @Produces({"text/plain"})
  public String getTriggerSetting(@PathParam("btLocator") String buildTypeLocator, @PathParam("triggerLocator") String triggerLocator,
                                  @PathParam("fieldName") String name) {
    final BuildTypeSettings buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator).get();
    final BuildTriggerDescriptor trigger = DataProvider.getTrigger(buildType, triggerLocator);
    return PropEntityStep.getSetting(buildType, trigger, name);
  }

  @PUT
  @Path("/{btLocator}/triggers/{triggerLocator}/{fieldName}")
  @Consumes({"text/plain"})
  @Produces({"text/plain"})
  public String changeTriggerSetting(@PathParam("btLocator") String buildTypeLocator, @PathParam("triggerLocator") String triggerLocator,
                                   @PathParam("fieldName") String name, String newValue) {
    final BuildTypeSettings buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator).get();
    final BuildTriggerDescriptor trigger = DataProvider.getTrigger(buildType, triggerLocator);
    PropEntityStep.setSetting(buildType, trigger, name, newValue);
    buildType.persist();
    return PropEntityStep.getSetting(buildType, trigger, name);
  }


  @GET
  @Path("/{btLocator}/agent-requirements")
  @Produces({"application/xml", "application/json"})
  public PropEntitiesAgentRequirement getAgentRequirements(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new PropEntitiesAgentRequirement(buildType.get());
  }

  /**
   * Replaces agent requirements with those sent in the request.
   */
  @PUT
  @Path("/{btLocator}/agent-requirements")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public PropEntitiesAgentRequirement replaceAgentRequirements(@PathParam("btLocator") String buildTypeLocator,
                                                               PropEntitiesAgentRequirement suppliedEntities) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);

    final List<Requirement> originals = buildType.get().getRequirements();
    removeRequirements(buildType, originals);
    try {
      if (suppliedEntities.propEntities != null) {
        for (PropEntityAgentRequirement entity : suppliedEntities.propEntities) {
          entity.addRequirement(buildType);
        }
      }
      buildType.get().persist();
    } catch (Exception e) {
      //restore original settings
      removeRequirements(buildType, buildType.get().getRequirements());
      for (Requirement entry : originals) {
        buildType.get().addRequirement(entry);
      }
      buildType.get().persist();
      throw new BadRequestException("Error replacing items", e);
    }
    return new PropEntitiesAgentRequirement(buildType.get());
  }

  private void removeRequirements(final BuildTypeOrTemplate buildType, final List<Requirement> requirements) {
    for (Requirement entry : requirements) {
      buildType.get().removeRequirement(entry.getPropertyName());  //todo: (TeamCity API): why srtring and not Requirement?
    }
  }

  /**
   * Creates new agent requirement. 'id' attribute is ignored in the submitted descriptor.
   * Reports error if new requirement cannot be created (e.g. another requirement is present for the parameter).
   */
  @POST
  @Path("/{btLocator}/agent-requirements")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public PropEntityAgentRequirement addAgentRequirement(@PathParam("btLocator") String buildTypeLocator,
                                                        PropEntityAgentRequirement description) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);

    final Requirement result = description.addRequirement(buildType);
    buildType.get().persist();
    return new PropEntityAgentRequirement(result);
  }

  @GET
  @Path("/{btLocator}/agent-requirements/{agentRequirementLocator}")
  @Produces({"application/xml", "application/json"})
  public PropEntityAgentRequirement getAgentRequirement(@PathParam("btLocator") String buildTypeLocator,
                                                        @PathParam("agentRequirementLocator") String agentRequirementLocator) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    final Requirement requirement = DataProvider.getAgentRequirement(buildType.get(), agentRequirementLocator);
    return new PropEntityAgentRequirement(requirement);
  }

  @DELETE
  @Path("/{btLocator}/agent-requirements/{agentRequirementLocator}")
  public void deleteAgentRequirement(@PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("agentRequirementLocator") String agentRequirementLocator) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    final Requirement requirement = DataProvider.getAgentRequirement(buildType.get(), agentRequirementLocator);
    buildType.get().removeRequirement(requirement.getPropertyName());
    buildType.get().persist();
  }

  @GET
  @Path("/{btLocator}/investigations")
  @Produces({"application/xml", "application/json"})
  public Investigations getInvestigations(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator);
    return new Investigations(buildType, myDataProvider, myApiUrlBuilder);
  }

  /**
   * Experimental support only!
   */
  @GET
  @Path("/{btLocator}/vcs-root-instances")
  @Produces({"application/xml", "application/json"})
  public VcsRootInstances getCurrentVcsInstances(@PathParam("btLocator") String buildTypeLocator) {
      SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator);
    return new VcsRootInstances(buildType.getVcsRootInstances(), null, myApiUrlBuilder);
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
  public Builds serveBuilds(@PathParam("btLocator") String buildTypeLocator,
                            @QueryParam("status") String status,
                            @QueryParam("triggeredByUser") String userLocator,
                            @QueryParam("includePersonal") boolean includePersonal,
                            @QueryParam("includeCanceled") boolean includeCanceled,
                            @QueryParam("onlyPinned") boolean onlyPinned,
                            @QueryParam("tag") List<String> tags,
                            @QueryParam("agentName") String agentName,
                            @QueryParam("sinceBuild") String sinceBuildLocator,
                            @QueryParam("sinceDate") String sinceDate,
                            @QueryParam("start") Long start,
                            @QueryParam("count") Integer count,
                            @QueryParam("locator") String locator,
                            @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator);

    return myBuildFinder.getBuildsForRequest(buildType, status, userLocator, includePersonal, includeCanceled, onlyPinned, tags, agentName,
                                           sinceBuildLocator, sinceDate, start, count, locator, "locator", uriInfo, request, myApiUrlBuilder
    );
  }

  @GET
  @Path("/{btLocator}/builds/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  public Build serveBuildWithProject(@PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("buildLocator") String buildLocator) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator);
    SBuild build = myBuildFinder.getBuild(buildType, buildLocator);
    return new Build(build, myDataProvider, myApiUrlBuilder, myServiceLocator, myFactory);
  }


  @GET
  @Path("/{btLocator}/builds/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildField(@PathParam("btLocator") String buildTypeLocator,
                                @PathParam("buildLocator") String buildLocator,
                                @PathParam("field") String field) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator);
    SBuild build = myBuildFinder.getBuild(buildType, buildLocator);

    return Build.getFieldValue(build, field);
  }

  @GET
  @Path("/{btLocator}/branches")
  @Produces({"application/xml", "application/json"})
  public Branches serveBranches(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator);
    //todo: support branches filters
    return new Branches(CollectionsUtil
                          .convertCollection(((BuildTypeImpl)buildType).getBranches(BranchesPolicy.ACTIVE_HISTORY_AND_ACTIVE_VCS_BRANCHES, false),
                                             new Converter<jetbrains.buildServer.server.rest.model.build.Branch, BranchEx>() {
                                               public Branch createFrom(@NotNull final BranchEx source) {
                                                 return new Branch(source);
                                               }
                                             }));
  }

  /**
   * Gets VCS labeling settings
   * Experimental support only
   */
  @GET
  @Path("/{btLocator}/vcsLabeling")
  @Produces({"application/xml", "application/json"})
  public VCSLabelingOptions getVCSLabelingOptions(@PathParam("btLocator") String buildTypeLocator) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new VCSLabelingOptions(buildType, myApiUrlBuilder);
  }

  /**
   * Sets VCS labeling settings
   * Experimental support only
   */
  @PUT
  @Path("/{btLocator}/vcsLabeling")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public VCSLabelingOptions setVCSLabelingOptions(@PathParam("btLocator") String buildTypeLocator, VCSLabelingOptions options) {
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    options.applyTo(buildType, new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
    buildType.get().persist();
    return new VCSLabelingOptions(buildType, myApiUrlBuilder);
  }

  /**
   * Experimental support only.
   * Use this to get an example of the bean to be posted to the /buildTypes request to create a new build type
   * @param projectLocator
   * @return
   */
  @GET
  @Path("/{btLocator}/newBuildTypeDescription")
  @Produces({"application/xml", "application/json"})
  public NewBuildTypeDescription getExampleNewProjectDescription(@PathParam("btLocator") String buildTypeLocator){
    final BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(null, buildTypeLocator);
    final BuildTypeRef buildTypeRef = buildType.isBuildType()
                                         ? new BuildTypeRef(buildType.getBuildType(), myDataProvider, myApiUrlBuilder)
                                         : new BuildTypeRef(buildType.getTemplate(), myDataProvider, myApiUrlBuilder);
    return new NewBuildTypeDescription(buildType.getName(), buildType.getId(), buildTypeRef, true);
  }
}
