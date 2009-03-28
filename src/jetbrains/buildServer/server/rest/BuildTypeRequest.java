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

// Supported URI: /api/buildTypes/id=bt112/builds/status=success/number
// Supported URI: /api/buildTypes/id=bt112/builds/number=123234/id
// Supported URI: /api/buildTypes/id=bt112/builds/id=23234/status

// not yet supported URI: /api/buildTypes/name=Main/builds/tag=EAP;state=SUCCESS/number


// Supported URI: /api/buildTypes/id:bt112/builds/status:success/number
// not yet supported URI: /api/configurations/name:Main/builds/tag:EAP;state:SUCCESS/number

// not yet supported URI: /api/bt122/builds/tag:EAP;state:SUCCESS/number
// not yet supported URI: /api/Maia/Main/builds/tag:EAP;state:SUCCESS/number


@Path("/httpAuth/api/buildTypes")
@Singleton
public class BuildTypeRequest {
  private static final Logger LOG = Logger.getInstance(BuildTypeRequest.class.getName());

  private SBuildServer myServer;
  private BuildHistory myBuildHistory;

  public BuildTypeRequest(SBuildServer server, final BuildHistory buildHistory) {
    myServer = server;
    myBuildHistory = buildHistory;
  }

  @GET
  @Path("/{btLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildTypeField(@PathParam("btLocator") String buildTypeLocator,
                                    @PathParam("field") String fieldName) {
    SBuildType buildType = getMandatoryBuildType(buildTypeLocator);

    return getMandatoryField(buildType, fieldName);
  }

  @GET
  @Path("/{btLocator}/builds/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildField(@PathParam("btLocator") String buildTypeLocator,
                                @PathParam("buildLocator") String buildLocator,
                                @PathParam("field") String field) {
    SBuildType buildType = getMandatoryBuildType(buildTypeLocator);
    SBuild build = getMandatoryBuild(buildType, buildLocator);

    return getMandatoryField(build, field);
  }

  @Nullable
  private String getMandatoryField(SBuild build, String field) {
    String fieldValue = null;
    try {
      fieldValue = getFieldValue(build, field);
    } catch (NotFoundException e) {
      reportError(Response.Status.NOT_FOUND, "No value for field " + field + " found in build " + build + ".", e);
    }
    return fieldValue;
  }

  @NotNull
  private SBuild getMandatoryBuild(SBuildType buildType, String buildLocator) {
    SBuild build = null;
    try {
      build = getBuild(buildType, buildLocator);
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
  private SBuildType getMandatoryBuildType(String buildTypeLocator) {
    SBuildType buildType = null;
    try {
      buildType = getBuildType(buildTypeLocator);
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
      fieldValue = getFieldValue(buildType, field);
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

  @Nullable
  private static String getFieldValue(final SBuildType buildType, final String field) throws NotFoundException {
    if ("id".equals(field)) {
      return buildType.getBuildTypeId();
    } else if ("description".equals(field)) {
      return buildType.getDescription();
    } else if ("name".equals(field)) {
      return buildType.getName();
    }
    throw new NotFoundException("Field '" + field + "' is not supported.");
  }

  @Nullable
  private String getFieldValue(@NotNull final SBuild build, @Nullable final String field) throws NotFoundException {
    if ("number".equals(field)) {
      return build.getBuildNumber();
    } else if ("status".equals(field)) {
      return build.getStatusDescriptor().getStatus().getText();
    } else if ("id".equals(field)) {
      return (new Long(build.getBuildId())).toString();
    }
    throw new NotFoundException("Field '" + field + "' is not supported.");
  }

  @NotNull
  private SBuild getBuild(SBuildType buildType, String buildLocator) throws NotFoundException, ErrorInRequestException {
    if (buildLocator == null) {
      throw new ErrorInRequestException("Empty build locator is not supported.");
    }

    MultiValuesMap<String, String> buildLocatorDimensions = decodeLocator(buildLocator);

    String idString = getSingleDimensionValue(buildLocatorDimensions, "id");
    if (idString != null) {
      Long id;
      try {
        id = Long.parseLong(idString);
      } catch (NumberFormatException e) {
        throw new ErrorInRequestException("Invalid build id '" + idString + "'. Should be a number.");
      }
      SBuild build = myServer.findBuildInstanceById(id);
      if (build == null) {
        throw new NotFoundException("No build can be found by id '" + id + "'.");
      }
      if (!buildType.getBuildTypeId().equals(build.getBuildTypeId())) {
        throw new NotFoundException("No build can be found by id '" + id + "' in build type" + buildType + ".");
      }
      if (buildLocatorDimensions.keySet().size() > 1) {
        LOG.info("Build locator '" + buildLocator + "' has 'id' dimenstion and others. Others are ignored.");
      }
      return build;
    }

    String number = getSingleDimensionValue(buildLocatorDimensions, "number");
    if (number != null) {
      SBuild build = myServer.findBuildInstanceByBuildNumber(buildType.getBuildTypeId(), number);
      if (build == null) {
        throw new NotFoundException("No build can be found by number '" + number + "' in build configuration " + buildType
                + ".");
      }
      if (buildLocatorDimensions.keySet().size() > 1) {
        LOG.info("Build locator '" + buildLocator + "' has 'number' dimenstion and others. Others are ignored.");
      }
      return build;
    }

    final String status = getSingleDimensionValue(buildLocatorDimensions, "status");
    if (status != null) {
      final SFinishedBuild[] foundBuild = new SFinishedBuild[1];
      //todo: support all the parameters from URL
      myBuildHistory.processEntries(buildType.getBuildTypeId(), null, false, true, true, new ItemProcessor<SFinishedBuild>() {
        public boolean processItem(final SFinishedBuild build) {
          if (status.equalsIgnoreCase(build.getStatusDescriptor().getStatus().getText())) {
            foundBuild[0] = build;
            return false;
          }
          return true;
        }
      });
      if (foundBuild[0] != null) {
        return foundBuild[0];
      }
      throw new NotFoundException("No build with status '" + status + "'can be found in build configuration " + buildType + ".");
    }

    throw new NotFoundException("Build locator '" + buildLocator + "' is not supported");
  }

  @NotNull
  private SBuildType getBuildType(String buildTypeLocator) throws NotFoundException, ErrorInRequestException {
    if (buildTypeLocator == null) {
      throw new ErrorInRequestException("Empty build type locator is not supported.");
    }
    MultiValuesMap<String, String> buildTypeLocatorDimensions = decodeLocator(buildTypeLocator);

    String id = getSingleDimensionValue(buildTypeLocatorDimensions, "id");
    if (id != null) {
      SBuildType buildType = myServer.getProjectManager().findBuildTypeById(id);
      if (buildType == null) {
        throw new NotFoundException("Build type cannot be found by id '" + id + "'.");
      }
      if (buildTypeLocatorDimensions.keySet().size() > 1) {
        LOG.info("Build type locator '" + buildTypeLocator + "' has 'id' dimenstion and others. Others are ignored.");
      }
      return buildType;
    }

    String name = getSingleDimensionValue(buildTypeLocatorDimensions, "name");
    if (name != null) {
      SBuildType buildType = findBuildTypeByName(name);
      if (buildType == null) {
        throw new NotFoundException("Build type cannot be found by name '" + name + "'.");
      }
      if (buildTypeLocatorDimensions.keySet().size() > 1) {
        LOG.info("Build type locator '" + buildTypeLocator + "' has 'name' dimenstion and others. Others are ignored.");
      }
      return buildType;
    }
    throw new ErrorInRequestException("Build type locator '" + buildTypeLocator + "' is not supported.");
  }

  @Nullable
  private SBuildType findBuildTypeByName(@NotNull final String name) {
    List<SBuildType> allBuildTypes = myServer.getProjectManager().getAllBuildTypes();
    for (SBuildType buildType : allBuildTypes) {
      if (name.equalsIgnoreCase(buildType.getName())) {
        return buildType;
      }
    }
    return null;
  }

  /**
   * Extracts the single dimension value from dimensions.
   *
   * @param dimensions    dimenstions to extract value from.
   * @param dimensionName the name of the dimension to extract value.
   * @return 'null' if no such dimension is found, value of the dimension otherwise.
   * @throws ErrorInRequestException if there are more then a single dimension defiition for a 'dimensionName' name or the dimension has no value specified.
   */
  @Nullable
  private String getSingleDimensionValue(@NotNull final MultiValuesMap<String, String> dimensions, @NotNull final String dimensionName) throws ErrorInRequestException {
    Collection<String> idDimension = dimensions.get(dimensionName);
    if (idDimension == null || idDimension.size() == 0) {
      return null;
    }
    if (idDimension.size() > 1) {
      throw new ErrorInRequestException("Only single '" + dimensionName + "' dimension is supported in locator. Found: " + idDimension);
    }
    String result = idDimension.iterator().next();
    if (result == null) {
      throw new ErrorInRequestException("Value is empty for dimension '" + dimensionName + "'.");
    }
    return result;
  }

  @NotNull
  private MultiValuesMap<String, String> decodeLocator(@NotNull String locator) throws NotFoundException {
    MultiValuesMap<String, String> result = new MultiValuesMap<String, String>();
    for (String dimension : locator.split(";")) {
      int delimiterIndex = dimension.indexOf(":");
      if (delimiterIndex > 0) {
        result.put(dimension.substring(0, delimiterIndex), dimension.substring(delimiterIndex + 1));
      } else {
        throw new NotFoundException("Bad locator syntax: '" + locator + "'. Can't find dimension name in dimension string '" + dimension + "'");
      }
    }
    return result;
  }


  private static class NotFoundException extends Exception {
    public NotFoundException(String message) {
      super(message);
    }
  }

  private static class ErrorInRequestException extends Exception {
    public ErrorInRequestException(String message) {
      super(message);
    }
  }
}
