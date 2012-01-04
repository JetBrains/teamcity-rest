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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.BuildsFilter;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.build.Tags;
import jetbrains.buildServer.server.rest.model.buildType.*;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.parameters.ParameterFactory;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
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

  public static String getBuildTypeHref(SBuildType buildType) {
    return API_BUILD_TYPES_URL + "/id:" + buildType.getBuildTypeId();
  }


  public static String getBuildsHref(final SBuildType buildType) {
    return getBuildTypeHref(buildType) + "/builds/";
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public BuildTypes serveBuildTypesXML() {
    return new BuildTypes(myDataProvider.getServer().getProjectManager().getAllBuildTypes(), myDataProvider, myApiUrlBuilder);
  }

  @GET
  @Path("/{btLocator}")
  @Produces({"application/xml", "application/json"})
  public BuildType serveBuildTypeXML(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return new BuildType(buildType, myDataProvider, myApiUrlBuilder);
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
    buildType.getProject().persist();
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
    buildType.getProject().persist();
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
    buildType.getProject().persist();
  }



  @GET
  @Path("/{btLocator}/settings")
  @Produces({"application/xml", "application/json"})
  public Properties serveBuildTypeSettings(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return new Properties(BuildTypeUtil.getSettingsParameters(buildType));
  }

  @GET
  @Path("/{btLocator}/settings/{name}")
  @Produces("text/plain")
  public String serveBuildTypeSettings(@PathParam("btLocator") String buildTypeLocator, @PathParam("name") String parameterName) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Setting parameter name cannot be empty.");
    }

    Map<String,String> parameters = BuildTypeUtil.getSettingsParameters(buildType);
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

    if (!BuildTypeUtil.getSettingsParameters(buildType).containsKey(parameterName)){
      throw new BadRequestException("Setting parameter with name '" + parameterName + "' is not known.");
    }

    try {
      BuildTypeUtil.setSettingsParameter(buildType, parameterName, newValue);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(
        "Could not set setting parameter with name '" + parameterName + "' to value '" + newValue + "'. Error: " + e.getMessage());
    }
    buildType.getProject().persist();
  }


  @GET
  @Path("/{btLocator}/steps")
  @Produces({"application/xml", "application/json"})
  //todo: devise a way to serve  appropriate root element in XML
  //  @XmlElement(name = "steps")
  public PropEntities getSteps(@PathParam("btLocator") String buildTypeLocator){
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return BuildTypeUtil.getSteps(buildType);
  }

  @GET
  @Path("/{btLocator}/steps/{stepId}")
  @Produces({"application/xml", "application/json"})
  public PropEntity getStep(@PathParam("btLocator") String buildTypeLocator, @PathParam("stepId") String stepId){
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuildRunnerDescriptor step = buildType.findBuildRunnerById(stepId);
    if (step == null){
      throw new NotFoundException("No step with id '" + stepId + "' is found.");
    }
    return new PropEntity(step);
  }

  @GET
  @Path("/{btLocator}/steps/{stepId}/parameters/{parameterName}")
  @Produces({"text/plain"})
  public String getStepParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("stepId") String stepId,
                                 @PathParam("parameterName") String parameterName) {
    SBuildRunnerDescriptor step = getBuildTypeStep(myDataProvider.getBuildType(null, buildTypeLocator), stepId);
    return getParameterValue(step, parameterName);
  }

  private String getParameterValue(final ParametersDescriptor parametersHolder, final String parameterName) {
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
  public PropEntities getFeatures(@PathParam("btLocator") String buildTypeLocator){
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return BuildTypeUtil.getFeatures(buildType);
  }

  @GET
  @Path("/{btLocator}/features/{featureId}/parameters/{parameterName}")
  @Produces({"text/plain"})
  public String getFeatureParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String featureId,
                                 @PathParam("parameterName") String parameterName) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuildFeatureDescriptor feature = getBuildTypeFeature(buildType, featureId);
    return feature.getParameters().get(parameterName);
  }

  @PUT
  @Path("/{btLocator}/features/{featureId}/parameters/{parameterName}")
  public void addFeatureParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String featureId,
                                  @PathParam("parameterName") String parameterName, String newValue) {

    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuildFeatureDescriptor feature = getBuildTypeFeature(buildType, featureId);
    Map<String, String> parameters = new HashMap<String, String>();
    parameters.putAll(feature.getParameters());
    if (StringUtil.isEmpty(parameterName)){
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    parameters.put(parameterName, newValue);
    buildType.updateBuildFeature(feature.getId(), feature.getType(), parameters);
    buildType.persist();
  }

  private SBuildFeatureDescriptor getBuildTypeFeature(final SBuildType buildType, @NotNull final String featureId) {
    if (StringUtil.isEmpty(featureId)){
      throw new BadRequestException("Feature Id cannot be empty.");
    }
    SBuildFeatureDescriptor feature = CollectionsUtil.findFirst(buildType.getBuildFeatures(), new Filter<SBuildFeatureDescriptor>() {
      public boolean accept(@NotNull final SBuildFeatureDescriptor data) {
        return data.getId().equals(featureId);
      }
    });
    if (feature == null) {
      throw new NotFoundException("No feature with id '" + featureId + "' is found in the build configuration.");
    }
    return feature;
  }

  /**
   * @deprecated
   * @see getBuildTypeStep()
   */
  @PUT
  @Path("/{btLocator}/runParameters/{name}")
  @Produces("text/plain")
  public void putBuildTypeRunParameter(@PathParam("btLocator") String buildTypeLocator,
                                       @PathParam("name") String parameterName,
                                       String newValue) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    if (StringUtil.isEmpty(newValue)) {
      throw new BadRequestException("Parameter value cannot be empty.");
    }
    buildType.addRunParameter(new SimpleParameter(parameterName, newValue));
    buildType.getProject().persist();
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
