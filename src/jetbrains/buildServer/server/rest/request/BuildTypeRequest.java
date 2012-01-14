/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptorFactory;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.BuildsFilter;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.build.Tags;
import jetbrains.buildServer.server.rest.model.buildType.*;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.dependency.Dependency;
import jetbrains.buildServer.serverSide.impl.dependency.DependencyFactoryImpl;
import jetbrains.buildServer.serverSide.parameters.ParameterFactory;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;

/**
 * User: Yegor Yarko
 * Date: 22.03.2009
 */

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produses 404
*/

@Path(BuildTypeRequest.API_BUILD_TYPES_URL)
public class BuildTypeRequest {
  @Context private DataProvider myDataProvider;
  @Context private ApiUrlBuilder myApiUrlBuilder;
  @Context private ServiceLocator myServiceLocator;
  @Context private BeanFactory myFactory;

  public static final String API_BUILD_TYPES_URL = Constants.API_URL + "/buildTypes";

  public static String getBuildTypeHref(@NotNull final BuildTypeOrTemplate buildType) {
    return buildType.isBuildType() ? getBuildTypeHref(buildType.getBuildType()) : getBuildTypeHref(buildType.getTemplate());
  }

  public static String getBuildTypeHref(@NotNull SBuildType buildType) {
    return API_BUILD_TYPES_URL + "/id:" + buildType.getBuildTypeId();
  }

  public static String getBuildTypeHref(@NotNull final BuildTypeTemplate template) {
    return API_BUILD_TYPES_URL + "/id:("+ DataProvider.TEMPLATE_ID_PREFIX + template.getId() + ")";
  }


  public static String getBuildsHref(final SBuildType buildType) {
    return getBuildTypeHref(buildType) + "/builds/";
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public BuildTypes serveBuildTypesXML() {
    return new BuildTypes(myDataProvider.getServer().getProjectManager().getAllBuildTypes(), myDataProvider, myApiUrlBuilder);
  }

  /**
   * Serves build configuraiton or templates according to the locator.
   */
  @GET
  @Path("/{btLocator}")
  @Produces({"application/xml", "application/json"})
  public BuildType serveBuildTypeXML(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new BuildType(buildType, myDataProvider, myApiUrlBuilder);
  }

  @DELETE
  @Path("/{btLocator}")
  public void deleteBuildType(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    final SProject project = buildType.getProject();
    project.removeBuildType(buildType.getBuildTypeId());
    project.persist();
  }

  @GET
  @Path("/{btLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildTypeField(@PathParam("btLocator") String buildTypeLocator, @PathParam("field") String fieldName) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return myDataProvider.getFieldValue(buildType, fieldName);
  }

  @PUT
  @Path("/{btLocator}/{field}")
  public void setBuildTypeField(@PathParam("btLocator") String buildTypeLocator, @PathParam("field") String fieldName, String newValue) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    myDataProvider.setFieldValue(buildType, fieldName, newValue);
    buildType.persist();
  }

  @GET
  @Path("/{btLocator}/buildTags")
  @Produces({"application/xml", "application/json"})
  public Tags serveBuildTypeBuildsTags(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return new Tags(buildType.getTags());
  }

  @GET
  @Path("/{btLocator}/parameters")
  @Produces({"application/xml", "application/json"})
  public Properties serveBuildTypeParameters(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);

    return new Properties(buildType.getParameters());
  }

  @GET
  @Path("/{btLocator}/parameters/{name}")
  @Produces("text/plain")
  public String serveBuildTypeParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("name") String parameterName) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }

    Map<String,String> parameters = buildType.getParameters();
    if (parameters.containsKey(parameterName)) {
      //TODO: need to process spec type to filter secure fields, may be include display value
      //TODO: might support type spec here
      return parameters.get(parameterName);
    }
    throw new NotFoundException("No parameter with name '" + parameterName + "' is found.");
  }

  @PUT
  @Path("/{btLocator}/parameters/{name}")
  @Produces("text/plain")
  public void putBuildTypeParameter(@PathParam("btLocator") String buildTypeLocator,
                                    @PathParam("name") String parameterName,
                                    String newValue) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }

    //TODO: support type spec here
    buildType.addParameter(getParameterFactory().createSimpleParameter(parameterName, newValue));
    buildType.persist();
  }

  @NotNull
  private ParameterFactory getParameterFactory() {
    return myServiceLocator.getSingletonService(ParameterFactory.class);
  }

  @DELETE
  @Path("/{btLocator}/parameters/{name}")
  @Produces("text/plain")
  public void deleteBuildTypeParameter(@PathParam("btLocator") String buildTypeLocator,
                                       @PathParam("name") String parameterName) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    buildType.removeParameter(parameterName);
    buildType.persist();
  }



  @GET
  @Path("/{btLocator}/settings")
  @Produces({"application/xml", "application/json"})
  public Properties serveBuildTypeSettings(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return new Properties(BuildTypeUtil.getSettingsParameters(new BuildTypeOrTemplate(buildType)));
  }

  @GET
  @Path("/{btLocator}/settings/{name}")
  @Produces("text/plain")
  public String serveBuildTypeSettings(@PathParam("btLocator") String buildTypeLocator, @PathParam("name") String parameterName) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Setting parameter name cannot be empty.");
    }

    Map<String,String> parameters = BuildTypeUtil.getSettingsParameters(new BuildTypeOrTemplate(buildType));
    if (parameters.containsKey(parameterName)) {
      return parameters.get(parameterName);
    }
    throw new NotFoundException("No setting parameter with name '" + parameterName + "' is found.");
  }

  @PUT
  @Path("/{btLocator}/settings/{name}")
  @Produces("text/plain")
  public void putBuildTypeSetting(@PathParam("btLocator") String buildTypeLocator,
                                    @PathParam("name") String parameterName,
                                    String newValue) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Settings parameter name cannot be empty.");
    }

    if (!BuildTypeUtil.getSettingsParameters(new BuildTypeOrTemplate(buildType)).containsKey(parameterName)){
      throw new BadRequestException("Setting parameter with name '" + parameterName + "' is not known.");
    }

    try {
      BuildTypeUtil.setSettingsParameter(buildType, parameterName, newValue);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(
        "Could not set setting parameter with name '" + parameterName + "' to value '" + newValue + "'. Error: " + e.getMessage());
    }
    buildType.persist();
  }


  @GET
  @Path("/{btLocator}/vcs-roots")
  @Produces({"application/xml", "application/json"})
  public VcsRootEntries getVcsRootEntries(@PathParam("btLocator") String buildTypeLocator){
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return new VcsRootEntries(buildType.getVcsRootEntries(), myApiUrlBuilder);
  }

  @POST
  @Path("/{btLocator}/vcs-roots")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public void addVcsRootEntry(@PathParam("btLocator") String buildTypeLocator, VcsRootEntryDescription description){
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    final SVcsRoot vcsRoot = BuildTypeUtil.getVcsRoot(description, myDataProvider);

    try {
      buildType.addVcsRoot(vcsRoot);
    } catch (InvalidVcsRootScopeException e) {
      throw new BadRequestException("Could not attach VCS root with id '" + vcsRoot.getId() + "' because of scope issues. Error: " + e.getMessage());
    }
    if (!StringUtil.isEmpty(description.checkoutRules)) {
      buildType.setCheckoutRules(vcsRoot, new CheckoutRules(description.checkoutRules));
    }
    buildType.persist();
  }

  @GET
  @Path("/{btLocator}/vcs-roots/{vcsRootLocator}")
  @Produces({"application/xml", "application/json"})
  public VcsRootEntry getVcsRootEntry(@PathParam("btLocator") String buildTypeLocator, @PathParam("vcsRootLocator") String vcsRootLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    final SVcsRoot vcsRoot = myDataProvider.getVcsRoot(vcsRootLocator);

    final CheckoutRules checkoutRules = buildType.getCheckoutRules(vcsRoot);
    if (checkoutRules != null) {
      final jetbrains.buildServer.vcs.VcsRootEntry vcsRootEntry =
        CollectionsUtil.findFirst(buildType.getVcsRootEntries(), new Filter<jetbrains.buildServer.vcs.VcsRootEntry>() {
          public boolean accept(@NotNull final jetbrains.buildServer.vcs.VcsRootEntry data) {
            return data.getVcsRoot().getId() == vcsRoot.getId();
          }
        });
      if (vcsRootEntry == null) {
        throw new NotFoundException("No VCS root with id '" + vcsRoot.getId() + "' is attached to the build type.");
      }
      return new VcsRootEntry(vcsRootEntry, myApiUrlBuilder);
    }
    return new VcsRootEntry(new jetbrains.buildServer.vcs.VcsRootEntry(vcsRoot, new CheckoutRules("")), myApiUrlBuilder);
  }

  @DELETE
  @Path("/{btLocator}/vcs-roots/{vcsRootLocator}")
  public void deleteVcsRootEntry(@PathParam("btLocator") String buildTypeLocator, @PathParam("vcsRootLocator") String vcsRootLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    final SVcsRoot vcsRoot = myDataProvider.getVcsRoot(vcsRootLocator);
    if (!buildType.containsVcsRoot(vcsRoot.getId())){
      throw new NotFoundException("No VCS root with id '" + vcsRoot.getId() + "' is attached to the build type.");
    }
    buildType.removeVcsRoot(vcsRoot);
    buildType.persist();
  }


  @GET
  @Path("/{btLocator}/steps")
  @Produces({"application/xml", "application/json"})
  public PropEntitiesStep getSteps(@PathParam("btLocator") String buildTypeLocator){
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return new PropEntitiesStep(buildType);
  }

  @POST
  @Path("/{btLocator}/steps")
  @Produces({"application/xml", "application/json"})
  public PropEntityStep addStep(@PathParam("btLocator") String buildTypeLocator, PropEntityStep stepDescription){
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    final SBuildRunnerDescriptor runnerToCreate =
      stepDescription.createRunner(myServiceLocator.getSingletonService(BuildRunnerDescriptorFactory.class));
    buildType.addBuildRunner(runnerToCreate);
    buildType.persist();
    return new PropEntityStep(buildType.findBuildRunnerById(runnerToCreate.getId()));
  }

  @GET
  @Path("/{btLocator}/steps/{stepId}")
  @Produces({"application/xml", "application/json"})
  public PropEntityStep getStep(@PathParam("btLocator") String buildTypeLocator, @PathParam("stepId") String stepId){
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuildRunnerDescriptor step = buildType.findBuildRunnerById(stepId);
    if (step == null){
      throw new NotFoundException("No step with id '" + stepId + "' is found.");
    }
    return new PropEntityStep(step);
  }

  @DELETE
  @Path("/{btLocator}/steps/{stepId}")
  public void deleteStep(@PathParam("btLocator") String buildTypeLocator, @PathParam("stepId") String stepId){
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuildRunnerDescriptor step = buildType.findBuildRunnerById(stepId);
    if (step == null){
      throw new NotFoundException("No step with id '" + stepId + "' is found.");
    }
    buildType.removeBuildRunner(stepId);
    buildType.persist();
  }

  @GET
  @Path("/{btLocator}/steps/{stepId}/parameters/{parameterName}")
  @Produces({"text/plain"})
  public String getStepParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("stepId") String stepId,
                                 @PathParam("parameterName") String parameterName) {
    SBuildRunnerDescriptor step = getBuildTypeStep(myDataProvider.getBuildType(null, buildTypeLocator), stepId);
    return getParameterValue(step, parameterName);
  }

  private static String getParameterValue(final ParametersDescriptor parametersHolder, final String parameterName) {
    Map<String, String> stepParameters = parametersHolder.getParameters();
    if (!stepParameters.containsKey(parameterName)){
      throw new NotFoundException("No parameter with name '" + parameterName + "' is found in the step parameters.");
    }
    return stepParameters.get(parameterName);
  }

  private SBuildRunnerDescriptor getBuildTypeStep(final SBuildType buildType, final String stepId) {
    SBuildRunnerDescriptor step = buildType.findBuildRunnerById(stepId);
    if (step == null) {
      throw new NotFoundException("No step with id '" + stepId + "' is found in the build configuration.");
    }
    return step;
  }

  @PUT
  @Path("/{btLocator}/steps/{stepId}/parameters/{parameterName}")
  public void addStepParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("stepId") String stepId,
                               @PathParam("parameterName") String parameterName, String newValue) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuildRunnerDescriptor step = getBuildTypeStep(buildType, stepId);
    Map<String, String> parameters = new HashMap<String, String>(step.getParameters());
    if (StringUtil.isEmpty(parameterName)){
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    parameters.put(parameterName, newValue);
    buildType.updateBuildRunner(step.getId(), step.getName(), step.getType(), parameters);
    buildType.persist();
  }



  @GET
  @Path("/{btLocator}/features")
  @Produces({"application/xml", "application/json"})
  public PropEntitiesFeature getFeatures(@PathParam("btLocator") String buildTypeLocator){
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return new PropEntitiesFeature(buildType);
  }

  @POST
  @Path("/{btLocator}/features")
  @Produces({"application/xml", "application/json"})
  public PropEntityFeature addFeature(@PathParam("btLocator") String buildTypeLocator, PropEntityFeature featureDescription){
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    final SBuildFeatureDescriptor featureToCreate =
      featureDescription.createFeature(myServiceLocator.getSingletonService(BuildFeatureDescriptorFactory.class));
    buildType.addBuildFeature(featureToCreate);
    buildType.persist();
    return new PropEntityFeature(BuildTypeUtil.getBuildTypeFeature(buildType, featureToCreate.getId()));
  }

  @GET
  @Path("/{btLocator}/features/{featureId}")
  @Produces({"application/xml", "application/json"})
  public PropEntityFeature getFeature(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String featureId){
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType, featureId);
    if (feature == null){
      throw new NotFoundException("No feature with id '" + featureId + "' is found.");
    }
    return new PropEntityFeature(feature);
  }

  @DELETE
  @Path("/{btLocator}/features/{featureId}")
  public void deleteFeature(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String id){
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType, id);
    if (feature == null){
      throw new NotFoundException("No feature with id '" + id + "' is found.");
    }
    buildType.removeBuildFeature(id);
    buildType.persist();
  }

  @GET
  @Path("/{btLocator}/features/{featureId}/parameters/{parameterName}")
  @Produces({"text/plain"})
  public String getFeatureParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String featureId,
                                 @PathParam("parameterName") String parameterName) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType, featureId);
    return feature.getParameters().get(parameterName);
  }

  @PUT
  @Path("/{btLocator}/features/{featureId}/parameters/{parameterName}")
  public void addFeatureParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String featureId,
                                  @PathParam("parameterName") String parameterName, String newValue) {

    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType, featureId);
    Map<String, String> parameters = new HashMap<String, String>();
    parameters.putAll(feature.getParameters());
    if (StringUtil.isEmpty(parameterName)){
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    parameters.put(parameterName, newValue);
    buildType.updateBuildFeature(feature.getId(), feature.getType(), parameters);
    buildType.persist();
  }



  @GET
  @Path("/{btLocator}/artifact-dependencies")
  @Produces({"application/xml", "application/json"})
  public PropEntitiesArtifactDep getArtifactDeps(@PathParam("btLocator") String buildTypeLocator){
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return new PropEntitiesArtifactDep(buildType);
  }

  @POST
  @Path("/{btLocator}/artifact-dependencies")
  @Produces({"application/xml", "application/json"})
  public PropEntityArtifactDep addArtifactDep(@PathParam("btLocator") String buildTypeLocator, PropEntityArtifactDep descripton) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);

    final List<SArtifactDependency> dependencies = buildType.getArtifactDependencies();
    dependencies.add(descripton.createDependency(myServiceLocator.getSingletonService(ArtifactDependencyFactory.class)));
    int orderNum = dependencies.size() - 1;
    buildType.setArtifactDependencies(dependencies);
    buildType.persist();
    //todo: might not be a good way to get just added dependency
    return new PropEntityArtifactDep(buildType.getArtifactDependencies().get(orderNum), orderNum);
  }

  @GET
  @Path("/{btLocator}/artifact-dependencies/{artifactDepLocator}")
  @Produces({"application/xml", "application/json"})
  public PropEntityArtifactDep getArtifactDep(@PathParam("btLocator") String buildTypeLocator,
                                              @PathParam("artifactDepLocator") String artifactDepLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    final SArtifactDependency artifactDependency = DataProvider.getArtifactDep(buildType, artifactDepLocator);
    return new PropEntityArtifactDep(artifactDependency, buildType);
  }

  @DELETE
  @Path("/{btLocator}/artifact-dependencies/{artifactDepLocator}")
  public void deleteArtifactDep(@PathParam("btLocator") String buildTypeLocator, @PathParam("artifactDepLocator") String artifactDepLocator){
    final SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    final SArtifactDependency artifactDependency = DataProvider.getArtifactDep(buildType, artifactDepLocator);
    final List<SArtifactDependency> dependencies = buildType.getArtifactDependencies();
    if (!dependencies.remove(artifactDependency)){
      throw new NotFoundException("Specified artifact dependency is not found in the build type.");
    }
    buildType.setArtifactDependencies(dependencies);
    buildType.persist();
  }



  @GET
  @Path("/{btLocator}/snapshot-dependencies")
  @Produces({"application/xml", "application/json"})
  public PropEntitiesSnapshotDep getAnpshotDeps(@PathParam("btLocator") String buildTypeLocator){
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return new PropEntitiesSnapshotDep(buildType);
  }

  @POST
  @Path("/{btLocator}/snapshot-dependencies")
  @Produces({"application/xml", "application/json"})
  public PropEntitySnapshotDep addSnapshotDep(@PathParam("btLocator") String buildTypeLocator, PropEntitySnapshotDep descripton) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);

    final Dependency dependencyDescription = descripton.createDependency(myServiceLocator.getSingletonService(DependencyFactoryImpl.class));
    final String dependOnId = dependencyDescription.getDependOnId();

    try {
      // need to remove beforehand to make it update:
      buildType.removeDependency(DataProvider.getSnapshotDep(buildType, dependOnId));
    } catch (NotFoundException e) {
      //ignore: it's OK if there is no such dependency
    }
    buildType.addDependency(dependencyDescription);
    buildType.persist();

    Dependency createdDependency = DataProvider.getSnapshotDep(buildType, dependOnId);
    return new PropEntitySnapshotDep(createdDependency);
  }

  @GET
  @Path("/{btLocator}/snapshot-dependencies/{snapshotDepLocator}")
  @Produces({"application/xml", "application/json"})
  public PropEntitySnapshotDep getSnapshotDep(@PathParam("btLocator") String buildTypeLocator,
                                              @PathParam("snapshotDepLocator") String snapshotDepLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    final Dependency dependency = DataProvider.getSnapshotDep(buildType, snapshotDepLocator);
    return new PropEntitySnapshotDep(dependency);
  }

  @DELETE
  @Path("/{btLocator}/snapshot-dependencies/{snapshotDepLocator}")
  public void deleteSnapshotDep(@PathParam("btLocator") String buildTypeLocator, @PathParam("snapshotDepLocator") String snapshotDepLocator){
    final SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    final Dependency dependency = DataProvider.getSnapshotDep(buildType, snapshotDepLocator);
    buildType.removeDependency(dependency);
    buildType.persist();
  }



  @GET
  @Path("/{btLocator}/triggers")
  @Produces({"application/xml", "application/json"})
  public PropEntitiesTrigger getTriggers(@PathParam("btLocator") String buildTypeLocator){
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return new PropEntitiesTrigger(buildType);
  }

  @POST
  @Path("/{btLocator}/triggers")
  @Produces({"application/xml", "application/json"})
  public PropEntityTrigger addSnapshotDep(@PathParam("btLocator") String buildTypeLocator, PropEntityTrigger descripton) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);

    final BuildTriggerDescriptor triggerToAdd =
      descripton.createTrigger(myServiceLocator.getSingletonService(BuildTriggerDescriptorFactory.class));
    if (!buildType.addBuildTrigger(triggerToAdd)) {
      final BuildTriggerDescriptor foundTriggerWithSameId = buildType.findTriggerById(descripton.id);
      if (foundTriggerWithSameId != null) {
        buildType.removeBuildTrigger(foundTriggerWithSameId);
      }
      if (!buildType.addBuildTrigger(triggerToAdd)) {
        throw new OperationException("Build trigger addition failed");
      }
    }
    //todo: might not be a good way to get just added trigger
    final Collection<BuildTriggerDescriptor> buildTriggersCollection = buildType.getBuildTriggersCollection();
    final BuildTriggerDescriptor justAdded = (BuildTriggerDescriptor)buildTriggersCollection.toArray()[buildTriggersCollection.size() - 1];

    buildType.persist();

    return new PropEntityTrigger(justAdded);
  }

  @GET
  @Path("/{btLocator}/triggers/{triggerLocator}")
  @Produces({"application/xml", "application/json"})
  public PropEntityTrigger getTrigger(@PathParam("btLocator") String buildTypeLocator,
                                              @PathParam("triggerLocator") String triggerLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    final BuildTriggerDescriptor trigger = DataProvider.getTrigger(buildType, triggerLocator);
    return new PropEntityTrigger(trigger);
  }

  @DELETE
  @Path("/{btLocator}/triggers/{triggerLocator}")
  public void deleteTrigger(@PathParam("btLocator") String buildTypeLocator, @PathParam("triggerLocator") String triggerLocator){
    final SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    final BuildTriggerDescriptor trigger = DataProvider.getTrigger(buildType, triggerLocator);
    if (!buildType.removeBuildTrigger(trigger)){
      throw new OperationException("Build trigger removal failed");
    }
    buildType.persist();
  }



  @GET
  @Path("/{btLocator}/agent-requirements")
  @Produces({"application/xml", "application/json"})
  public PropEntitiesAgentRequirement getAgentRequirements(@PathParam("btLocator") String buildTypeLocator){
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return new PropEntitiesAgentRequirement(buildType);
  }

  @POST
  @Path("/{btLocator}/agent-requirements")
  @Produces({"application/xml", "application/json"})
  public PropEntityAgentRequirement addAgentRequirement(@PathParam("btLocator") String buildTypeLocator, PropEntityAgentRequirement descripton) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);


    final Requirement requirementToAdd = descripton.createRequirement();
    final Requirement requirement = DataProvider.getAgentRequirement(buildType, requirementToAdd.getPropertyName());
    if (requirement != null){
      buildType.removeRequirement(requirementToAdd.getPropertyName());
    }
    buildType.addRequirement(requirementToAdd);
    buildType.persist();

    //todo: might not be a good way to get just added requirement
    final List<Requirement> requirements = buildType.getRequirements();
    return new PropEntityAgentRequirement(requirements.get(requirements.size()-1));
  }

  @GET
  @Path("/{btLocator}/agent-requirements/{agentRequirementLocator}")
  @Produces({"application/xml", "application/json"})
  public PropEntityAgentRequirement getAgentRequirement(@PathParam("btLocator") String buildTypeLocator,
                                              @PathParam("agentRequirementLocator") String agentRequirementLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    final Requirement requirement = DataProvider.getAgentRequirement(buildType, agentRequirementLocator);
    return new PropEntityAgentRequirement(requirement);
  }

  @DELETE
  @Path("/{btLocator}/agent-requirements/{agentRequirementLocator}")
  public void deleteAgentRequirement(@PathParam("btLocator") String buildTypeLocator, @PathParam("agentRequirementLocator") String agentRequirementLocator){
    final SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    final Requirement requirement = DataProvider.getAgentRequirement(buildType, agentRequirementLocator);
    buildType.removeRequirement(requirement.getPropertyName());
    buildType.persist();
  }



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
                            @QueryParam("start") @DefaultValue(value = "0") Long start,
                            @QueryParam("count") @DefaultValue(value = Constants.DEFAULT_PAGE_ITEMS_COUNT) Integer count,
                            @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    //todo: support locator parameter
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);

    final List<SBuild> buildsList = myDataProvider.getBuilds(
      // preserve 5.0 logic for personal/canceled/pinned builds
      new BuildsFilter(buildType, status, myDataProvider.getUserIfNotNull(userLocator),
                       includePersonal ? null : false, includeCanceled ? null : false, false, onlyPinned ? true : null, tags, agentName,
                       myDataProvider.getRangeLimit(buildType, sinceBuildLocator, myDataProvider.parseDate(sinceDate)), null, start,
                       count));
    return new Builds(buildsList,
                      myDataProvider,
                      new PagerData(uriInfo.getRequestUriBuilder(), request, start, count, buildsList.size()),
                      myApiUrlBuilder);
  }

  @GET
  @Path("/{btLocator}/builds/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  public Build serveBuildWithProject(@PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("buildLocator") String buildLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuild build = myDataProvider.getBuild(buildType, buildLocator);
    return new Build(build, myDataProvider, myApiUrlBuilder, myServiceLocator, myFactory);
  }


  @GET
  @Path("/{btLocator}/builds/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildField(@PathParam("btLocator") String buildTypeLocator,
                                @PathParam("buildLocator") String buildLocator,
                                @PathParam("field") String field) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuild build = myDataProvider.getBuild(buildType, buildLocator);

    return myDataProvider.getFieldValue(build, field);
  }
}
