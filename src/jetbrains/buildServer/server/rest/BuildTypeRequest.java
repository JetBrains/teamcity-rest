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

package jetbrains.buildServer.server.rest;

import com.sun.jersey.spi.resource.Singleton;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.diagnostic.Logger;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.ItemProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * User: Yegor Yarko
 * Date: 22.03.2009
 */

@Path("/httpAuth/api")
@Singleton
public class BuildTypeRequest {
  private static final Logger LOG = Logger.getInstance(BuildTypeRequest.class.getName());
  private final DataProvider myDataProvider;

  public BuildTypeRequest(DataProvider myDataProvider) {
    this.myDataProvider = myDataProvider;
  }

  @GET
  @Path("/buildTypes/{btLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildTypeField(@PathParam("btLocator") String buildTypeLocator,
                                    @PathParam("field") String fieldName) {
    SBuildType buildType = getMandatoryBuildType(null, buildTypeLocator);

    return getMandatoryField(buildType, fieldName);
  }

  @GET
  @Path("/projects/{projectLocator}/buildTypes/{btLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildTypeFieldWithProject(@PathParam("projectLocator") String projectLocator,
                                               @PathParam("btLocator") String buildTypeLocator,
                                               @PathParam("field") String fieldName) {
    SBuildType buildType = getMandatoryBuildType(getMandatoryProject(projectLocator), buildTypeLocator);

    return getMandatoryField(buildType, fieldName);
  }

  @GET
  @Path("/buildTypes/{btLocator}/builds/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildField(@PathParam("btLocator") String buildTypeLocator,
                                @PathParam("buildLocator") String buildLocator,
                                @PathParam("field") String field) {
    SBuildType buildType = getMandatoryBuildType(null, buildTypeLocator);
    SBuild build = getMandatoryBuild(buildType, buildLocator);

    return getMandatoryField(build, field);
  }

  @GET
  @Path("/projects/{projectLocator}/buildTypes/{btLocator}/builds/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildFieldWithProject(@PathParam("projectLocator") String projectLocator,
                                           @PathParam("btLocator") String buildTypeLocator,
                                           @PathParam("buildLocator") String buildLocator,
                                           @PathParam("field") String field) {
    SBuildType buildType = getMandatoryBuildType(getMandatoryProject(projectLocator), buildTypeLocator);
    SBuild build = getMandatoryBuild(buildType, buildLocator);

    return getMandatoryField(build, field);
  }

  @GET
  @Path("/{projectLocator}/{btLocator}/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildFieldShort(@PathParam("projectLocator") String projectLocator,
                                     @PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("buildLocator") String buildLocator,
                                     @PathParam("field") String field) {
    SProject project = getMandatoryProject(projectLocator);
    SBuildType buildType = getMandatoryBuildType(project, buildTypeLocator);
    SBuild build = getMandatoryBuild(buildType, buildLocator);

    return getMandatoryField(build, field);
  }

  @GET
  @Path("/builds/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildFieldByBuildOnly(@PathParam("buildLocator") String buildLocator,
                                           @PathParam("field") String field) {
    SBuild build = getMandatoryBuild(null, buildLocator);

    return getMandatoryField(build, field);
  }

  @NotNull
  private SProject getMandatoryProject(String projectLocator) {
    SProject project = null;
    try {
      project = myDataProvider.getProject(projectLocator);
    } catch (NotFoundException e) {
      reportError(Response.Status.NOT_FOUND, "No project found by " + projectLocator + ".", e);
    } catch (ErrorInRequestException e) {
      reportError(Response.Status.BAD_REQUEST, "The request is not supported.", e);
    }
    //noinspection ConstantConditions
    return project;
  }


  @Nullable
  private String getMandatoryField(SBuild build, String field) {
    String fieldValue = null;
    try {
      fieldValue = myDataProvider.getFieldValue(build, field);
    } catch (NotFoundException e) {
      reportError(Response.Status.NOT_FOUND, "No value for field " + field + " found in build " + build + ".", e);
    }
    return fieldValue;
  }

  @NotNull
  private SBuild getMandatoryBuild(@Nullable SBuildType buildType, @Nullable String buildLocator) {
    SBuild build = null;
    try {
      build = myDataProvider.getBuild(buildType, buildLocator);
    } catch (NotFoundException e) {
      reportError(Response.Status.NOT_FOUND,
              "No build found by " + buildLocator + " in build configuration " + buildType + ".", e);
    } catch (ErrorInRequestException e) {
      reportError(Response.Status.BAD_REQUEST, "The request is not supported.", e);
    }
    //noinspection ConstantConditions
    return build;
  }

  @NotNull
  private SBuildType getMandatoryBuildType(@Nullable SProject project, String buildTypeLocator) {
    SBuildType buildType = null;
    try {
      buildType = myDataProvider.getBuildType(project, buildTypeLocator);
    } catch (NotFoundException e) {
      reportError(Response.Status.NOT_FOUND, "No build configuration found by " + buildTypeLocator + ".", e);
    } catch (ErrorInRequestException e) {
      reportError(Response.Status.BAD_REQUEST, "The request is not supported.", e);
    }
    //noinspection ConstantConditions
    return buildType;
  }

  @Nullable
  private String getMandatoryField(SBuildType buildType, String field) {
    String fieldValue = null;
    try {
      fieldValue = myDataProvider.getFieldValue(buildType, field);
    } catch (NotFoundException e) {
      reportError(Response.Status.NOT_FOUND, "No value for field " + field + " found in build configuration " + buildType + ".", e);
    }
    return fieldValue;
  }

  private void reportError(@NotNull final Response.Status responseStatus, @NotNull final String message, @Nullable final Exception e) {
    Response.ResponseBuilder builder = Response.status(responseStatus);
    builder.type("text/plain");
    builder.entity(message + (e != null ? " Cause: " + e.getMessage() : ""));
    LOG.debug(message, e);
    throw new WebApplicationException(builder.build());
  }

}
