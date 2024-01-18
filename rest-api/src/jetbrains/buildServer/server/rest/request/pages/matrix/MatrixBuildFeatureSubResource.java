/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.request.pages.matrix;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.matrixBuild.MatrixParamsBuildFeature;
import jetbrains.buildServer.server.rest.data.finder.impl.BuildTypeFinder;
import jetbrains.buildServer.server.rest.data.pages.matrix.MatrixBuildFeatureService;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyInjectable;
import jetbrains.buildServer.server.rest.model.pages.ErrorDescriptor;
import jetbrains.buildServer.server.rest.model.pages.ErrorDescriptorList;
import jetbrains.buildServer.server.rest.model.pages.matrix.MatrixParameterDescriptor;
import jetbrains.buildServer.server.rest.model.pages.matrix.MatrixBuildFeatureDescriptor;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
@JerseyInjectable
@Api(hidden = true)
public class MatrixBuildFeatureSubResource {
  private final BuildTypeFinder myBuildTypeFinder;
  private final MatrixBuildFeatureService myMatrixBuildFeatureService;

  public MatrixBuildFeatureSubResource(
    @NotNull BuildTypeFinder buildTypeFinder,
    @NotNull MatrixBuildFeatureService matrixBuildFeatureService
  ) {
    myBuildTypeFinder = buildTypeFinder;
    myMatrixBuildFeatureService = matrixBuildFeatureService;
  }

  @GET
  @Path("/{featureId}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Retrieve matrix configuration.", hidden = true)
  public MatrixBuildFeatureDescriptor getConfiguration(
    @ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
    @PathParam("featureId") String featureId
  ) {
    BuildTypeOrTemplate btt = myBuildTypeFinder.getItem(buildTypeLocator);

    return myMatrixBuildFeatureService.resolveParameters(btt, featureId);
  }

  @POST
  @Produces({"application/xml", "application/json"})
  @Consumes({"application/xml", "application/json"})
  @ApiOperation(value = "Create matrix configuration.", hidden = true)
  public Response createConfiguration(
    @ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
    MatrixBuildFeatureDescriptor submitted,
    @Context HttpServletRequest request
  ) {
    BuildTypeOrTemplate btt = myBuildTypeFinder.getItem(buildTypeLocator);
    if(!btt.get().getBuildFeaturesOfType(MatrixParamsBuildFeature.TYPE).isEmpty()) {
      throw new BadRequestException("Matrix feature is already present in the given " + (btt.isBuildType() ? "build type." : "template."));
    }

    List<MatrixParameterDescriptor> submittedParams = submitted.getParameters();
    List<ErrorDescriptor> errors = myMatrixBuildFeatureService.validateParameters(submittedParams);
    if(!errors.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorDescriptorList(errors)).build();
    }

    String newFeatureId = myMatrixBuildFeatureService.createFeature(btt, submittedParams, WebUtil.isProbablyBrowser(request));
    return Response.ok(myMatrixBuildFeatureService.resolveParameters(btt, newFeatureId)).build();
  }

  @PUT
  @Path("/{featureId}")
  @ApiOperation(value = "Update parameters of the existing matrix configuration.", hidden = true)
  public Response updateConfiguration(
    @ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
    @PathParam("featureId") String featureId,
    MatrixBuildFeatureDescriptor updated,
    @Context HttpServletRequest request
  ) {
    BuildTypeOrTemplate btt = myBuildTypeFinder.getItem(buildTypeLocator);

    List<MatrixParameterDescriptor> submittedParams = updated.getParameters();
    List<ErrorDescriptor> errors = myMatrixBuildFeatureService.validateParameters(submittedParams);
    if(!errors.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorDescriptorList(errors)).build();
    }

    myMatrixBuildFeatureService.updateExistingFeature(btt, featureId, submittedParams, WebUtil.isProbablyBrowser(request));
    return Response.ok(myMatrixBuildFeatureService.resolveParameters(btt, featureId)).build();
  }

  @DELETE
  @Path("/{featureId}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Remove existing matrix configuration.", hidden = true)
  public void removeConfiguration(
    @ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
    @PathParam("featureId") String featureId,
    @Context HttpServletRequest request
  ) {
    BuildTypeOrTemplate btt = myBuildTypeFinder.getItem(buildTypeLocator);

    myMatrixBuildFeatureService.removeFeature(btt, featureId, WebUtil.isProbablyBrowser(request));
  }

  @POST
  @Path("/viewAsCode")
  @Produces({"application/json"})
  @Consumes({"application/json"})
  @ApiOperation(value = "Generate view as code.", hidden = true)
  public Response getConfiguration(
    @ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
    ViewAsCodePayload payload
  ) {
    BuildTypeOrTemplate btt = myBuildTypeFinder.getItem(buildTypeLocator);

    MatrixBuildFeatureDescriptor descriptor = payload.getDescriptor();
    List<ErrorDescriptor> validationErrors = myMatrixBuildFeatureService.validateParameters(descriptor.getParameters());
    if(!validationErrors.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorDescriptorList(validationErrors)).build();
    }

    MatrixBuildFeatureService.GenerateDslResult result = myMatrixBuildFeatureService.generateDSL(btt, payload);
    if(result.getError() != null) {
      return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorDescriptorList(Collections.singletonList(result.getError()))).build();
    }

    return Response.ok(result.getDsl()).build();
  }

  @XmlType(name = "admin_buildType_viewAsCodePayload")
  public static class ViewAsCodePayload {
    public ViewAsCodePayload() { }

    private String showDSL;
    private String showDSLVersion;
    private String showDSLPortable;
    private String featureId;
    private String featureType;
    private MatrixBuildFeatureDescriptor descriptor;

    @XmlAttribute(name = "showDSL")
    public String getShowDSL() {
      return showDSL;
    }

    public void setShowDSL(String showDSL) {
      this.showDSL = showDSL;
    }

    @XmlAttribute(name = "showDSLVersion")
    public String getShowDSLVersion() {
      return showDSLVersion;
    }

    public void setShowDSLVersion(String showDSLVersion) {
      this.showDSLVersion = showDSLVersion;
    }

    @XmlAttribute(name = "showDSLPortable")
    public String getShowDSLPortable() {
      return showDSLPortable;
    }

    public void setShowDSLPortable(String showDSLPortable) {
      this.showDSLPortable = showDSLPortable;
    }

    @XmlAttribute(name = "featureId")
    public String getFeatureId() {
      return featureId;
    }

    public void setFeatureId(String featureId) {
      this.featureId = featureId;
    }

    @XmlAttribute(name = "featureType")
    public String getFeatureType() {
      return featureType;
    }

    public void setFeatureType(String featureType) {
      this.featureType = featureType;
    }

    @XmlElement
    public MatrixBuildFeatureDescriptor getDescriptor() {
      return descriptor;
    }

    public void setDescriptor(MatrixBuildFeatureDescriptor descriptor) {
      this.descriptor = descriptor;
    }
  }
}
