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
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.ItemProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 22.03.2009
 */

// Supported URI: /api/buildTypes/id=bt112/builds/status=success/number
// Supported URI: /api/buildTypes/id=bt112/builds/number=123234/id
// Supported URI: /api/buildTypes/id=bt112/builds/id=23234/status

// not yet supported URI: /api/buildTypes/name=Main/builds/tag=EAP;state=SUCCESS/number


@Path("/httpAuth/api/buildTypes")
@Singleton
public class BuildTypeRequest {
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
                                    @PathParam("field") String field) {
    SBuildType buildType = null;
    try {
      buildType = getBuildType(buildTypeLocator);
    } catch (NotFoundException e) {
      reportError(Response.Status.NOT_FOUND, "No build configuration found by " + buildTypeLocator + ", reason: " + e.getMessage());
    }

    String fieldValue = null;
    try {
      fieldValue = getFieldValue(buildType, field);
    } catch (NotFoundException e) {
      reportError(Response.Status.NOT_FOUND, "No value for field " + field + " found in build configuration " + buildType);
    }
    return fieldValue;
  }

  @GET
  @Path("/{btLocator}/builds/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildField(@PathParam("btLocator") String buildTypeLocator,
                                @PathParam("buildLocator") String buildLocator,
                                @PathParam("field") String field) {
    SBuildType buildType = null;
    try {
      buildType = getBuildType(buildTypeLocator);
    } catch (NotFoundException e) {
      reportError(Response.Status.NOT_FOUND, "No build configuration found by " + buildTypeLocator + ", reason: " + e.getMessage());
    }

    SBuild build = null;
    try {
      build = getBuild(buildType, buildLocator);
    } catch (NotFoundException e) {
      reportError(Response.Status.NOT_FOUND,
                  "No build found by " + buildLocator + " in build configuration " + buildType + ", reason: " + e.getMessage());
    }

    String fieldValue = null;
    try {
      fieldValue = getFieldValue(build, field);
    } catch (NotFoundException e) {
      reportError(Response.Status.NOT_FOUND, "No value for field " + field + " found in build " + build);
    }
    return fieldValue;
  }

  private void reportError(Response.Status responseStatus, String message) {
    Response.ResponseBuilder builder = Response.status(responseStatus);
    builder.type("text/plain");
    builder.entity(message);
    throw new WebApplicationException(builder.build());
  }

  @Nullable
  private String getFieldValue(final SBuildType buildType, final String field) throws NotFoundException {
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
  private String getFieldValue(SBuild build, String field) throws NotFoundException {
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
  private SBuild getBuild(SBuildType buildType, String buildLocator) throws NotFoundException {
    if (buildLocator.startsWith("id=")) {
      String idString = buildLocator.substring("id=".length());
      Long id;
      try {
        id = Long.parseLong(idString);
      } catch (NumberFormatException e) {
        throw new NotFoundException("Invalid build id '" + idString + "'. Should be a number.");
      }
      SBuild build = myServer.findBuildInstanceById(id);
      if (build == null) {
        throw new NotFoundException("No build can be found by id '" + id + "'.");
      }
      if (!buildType.getBuildTypeId().equals(build.getBuildTypeId())) {
        throw new NotFoundException("No build can be found by id '" + id + "' in build type" + buildType + ".");
      }
      return build;
    }

    if (buildLocator.startsWith("number=")) {
      String number = buildLocator.substring("number=".length());
      SBuild build = myServer.findBuildInstanceByBuildNumber(buildType.getBuildTypeId(), number);
      if (build == null) {
        throw new NotFoundException("No build can be found by number '" + number + "' in build configuration " + buildType
                                    + ".");
      }
      return build;
    }

    final SFinishedBuild[] foundBuild = new SFinishedBuild[1];
    if (buildLocator.startsWith("status=")) {
      final String statusText = buildLocator.substring("status=".length());
      myBuildHistory.processEntries(buildType.getBuildTypeId(), null, false, true, true, new ItemProcessor<SFinishedBuild>() {
        public boolean processItem(final SFinishedBuild build) {
          if (statusText.equals(build.getStatusDescriptor().getStatus().getText())) {
            foundBuild[0] = build;
            return false;
          }
          return true;
        }
      });
      if (foundBuild[0] != null) {
        return foundBuild[0];
      }
      throw new NotFoundException("No build with status '" + statusText + "'can be found in build configuration " + buildType + ".");
    }
    throw new NotFoundException("Build locator '" + buildLocator + "' is not supported");
  }

  @NotNull
  private SBuildType getBuildType(String buildTypeLocator) throws NotFoundException {
    if (buildTypeLocator.startsWith("id=")) {
      String id = buildTypeLocator.substring("id=".length());
      SBuildType buildType = myServer.getProjectManager().findBuildTypeById(id);
      if (buildType == null) {
        throw new NotFoundException("Build type cannot be found by id '" + id + "'.");
      }
      return buildType;
    }
    throw new NotFoundException("Build type locator '" + buildTypeLocator + "' is not supported.");
  }

  private class NotFoundException extends Exception {
    public NotFoundException(String message) {
      super(message);
    }
  }

}
