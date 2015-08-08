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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.ws.rs.*;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.ParameterType;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Property;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeUtil;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.InheritableUserParametersHolder;
import jetbrains.buildServer.serverSide.Parameter;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.03.2015
 */
public class ParametersSubResource {

  @NotNull private ServiceLocator myServiceLocator;

  @NotNull private final EntityWithParameters myEntityWithParameters;
  @NotNull private final String myParametersHref;

  public ParametersSubResource(final @NotNull ServiceLocator serviceLocator, final @NotNull EntityWithParameters entityWithParameters, final @NotNull String parametersHref) {
    myServiceLocator = serviceLocator;
    myEntityWithParameters = entityWithParameters;
    myParametersHref = parametersHref;
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public Properties getParameters(@QueryParam("locator") Locator locator, @QueryParam("fields") String fields) {
    if (locator == null) {
      return new Properties(myEntityWithParameters.getParametersCollection(), myEntityWithParameters.getOwnParametersCollection(), myParametersHref,
                            new Fields(fields), myServiceLocator);
    }
    final Boolean own = locator.getSingleDimensionValueAsBoolean("own");
    if (own == null) {
      locator.checkLocatorFullyProcessed();
      return new Properties(myEntityWithParameters.getParametersCollection(), myEntityWithParameters.getOwnParametersCollection(), myParametersHref,
                            new Fields(fields), myServiceLocator);
    }
    if (own) {
      return new Properties(myEntityWithParameters.getOwnParametersCollection(), myEntityWithParameters.getOwnParametersCollection(), myParametersHref,
                            new Fields(fields), myServiceLocator);
    } else {
      throw new BadRequestException("Sorry, getting only not own parameters is not supported at the moment");
    }
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Property setParameter(Property parameter, @QueryParam("fields") String fields) {
    myEntityWithParameters.addParameter(parameter.getFromPosted(myServiceLocator));
    myEntityWithParameters.persist();
    return Property.createFrom(parameter.name, myEntityWithParameters, new Fields(fields), myServiceLocator);
  }

  @PUT
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Properties setParameters(Properties properties, @QueryParam("fields") String fields) {
    final List<Parameter> fromPosted = properties.getFromPosted(myServiceLocator); //get them first so that no modifications are made if there are parsing errors
    BuildTypeUtil.removeAllParameters(myEntityWithParameters);
    for (Parameter p : fromPosted) {
      myEntityWithParameters.addParameter(p);
    }
    myEntityWithParameters.persist();
    return new Properties(myEntityWithParameters.getParametersCollection(), myEntityWithParameters.getOwnParametersCollection(), myParametersHref,
                          new Fields(fields), myServiceLocator);
  }

  @DELETE
  public void deleteAllParameters() {
    BuildTypeUtil.removeAllParameters(myEntityWithParameters);
    myEntityWithParameters.persist();
  }

  @GET
  @Path("/{name}")
  @Produces({"application/xml", "application/json"})
  public Property getParameter(@PathParam("name") String parameterName, @QueryParam("fields") String fields) {
    return Property.createFrom(parameterName, myEntityWithParameters, new Fields(fields), myServiceLocator);
  }

  @GET
  @Path("/{name}/value")
  @Produces("text/plain")
  public String getParameterValueLong(@PathParam("name") String parameterName) {
    return BuildTypeUtil.getParameter(parameterName, myEntityWithParameters, true, false);
  }

  @PUT
  @Path("/{name}/value")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setParameterValueLong(@PathParam("name") String parameterName, String newValue) {
    BuildTypeUtil.changeParameter(parameterName, newValue, myEntityWithParameters, myServiceLocator);
    myEntityWithParameters.persist();
    return BuildTypeUtil.getParameter(parameterName, myEntityWithParameters, false, false);
  }

  @GET
  @Path("/{name}/type")
  @Produces({"application/xml", "application/json"})
  public ParameterType getParameterType(@PathParam("name") String parameterName) {
    return Property.createFrom(parameterName, myEntityWithParameters, Fields.LONG, myServiceLocator).type;
  }

  @PUT
  @Path("/{name}/type")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public ParameterType setParameterType(@PathParam("name") String parameterName, ParameterType parameterType) {
    BuildTypeUtil.changeParameterType(parameterName, parameterType.rawValue, myEntityWithParameters, myServiceLocator);
    myEntityWithParameters.persist();
    return Property.createFrom(parameterName, myEntityWithParameters, Fields.LONG, myServiceLocator).type;
  }

  @GET
  @Path("/{name}/type/rawValue")
  @Produces("text/plain")
  public String getParameterTypeRawValue(@PathParam("name") String parameterName) {
    final ParameterType type = Property.createFrom(parameterName, myEntityWithParameters, Fields.LONG, myServiceLocator).type;
    return type == null ? null : type.rawValue;
  }

  @PUT
  @Path("/{name}/type/rawValue")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setParameterTypeRawValue(@PathParam("name") String parameterName, String parameterTypeRawValue) {
    BuildTypeUtil.changeParameterType(parameterName, parameterTypeRawValue, myEntityWithParameters, myServiceLocator);
    myEntityWithParameters.persist();
    final ParameterType type = Property.createFrom(parameterName, myEntityWithParameters, Fields.LONG, myServiceLocator).type;
    return type == null ? null : type.rawValue;
  }

  /**
   * Plain text support for pre-8.1 compatibility
   */
  @GET
  @Path("/{name}")
  @Produces("text/plain")
  public String getParameterValue(@PathParam("name") String parameterName) {
    return BuildTypeUtil.getParameter(parameterName, myEntityWithParameters, true, false);
  }

  /**
   * Plain text support for pre-8.1 compatibility
   */
  @PUT
  @Path("/{name}")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setParameterValue(@PathParam("name") String parameterName, String newValue) {
    BuildTypeUtil.changeParameter(parameterName, newValue, myEntityWithParameters, myServiceLocator);
    myEntityWithParameters.persist();
    return BuildTypeUtil.getParameter(parameterName, myEntityWithParameters, false, false);
  }

  @PUT
  @Path("/{name}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Property setParameter(@PathParam("name") String parameterName, Property parameter, @QueryParam("fields") String fields) {
    parameter.name = parameterName; //overriding name int he entity with the value from URL
    final Parameter fromPosted = parameter.getFromPosted(myServiceLocator);
    //myEntityWithParameters.removeParameter(fromPosted.getName());
    myEntityWithParameters.addParameter(fromPosted); //when such parameter already exists, the method replaces it
    myEntityWithParameters.persist();
    return Property.createFrom(parameter.name, myEntityWithParameters, new Fields(fields), myServiceLocator);
  }

  @DELETE
  @Path("/{name}")
  public void deleteParameter(@PathParam("name") String parameterName) {
    BuildTypeUtil.deleteParameter(parameterName, myEntityWithParameters);
    myEntityWithParameters.persist();
  }


  interface EntityWithParameters extends InheritableUserParametersHolder {
    void persist();
  }

  public static class BuildTypeEntityWithParameters implements EntityWithParameters {
    @NotNull private final BuildTypeOrTemplate myBuildTypeOrTemplate;

    public BuildTypeEntityWithParameters(@NotNull final BuildTypeOrTemplate buildTypeOrTemplate) {
      myBuildTypeOrTemplate = buildTypeOrTemplate;
    }

    public void persist() {
      myBuildTypeOrTemplate.get().persist();
    }

    public void addParameter(@NotNull final Parameter param) {
      myBuildTypeOrTemplate.get().addParameter(param);
    }

    public void removeParameter(@NotNull final String paramName) {
      myBuildTypeOrTemplate.get().removeParameter(paramName);
    }

    @NotNull
    public Collection<Parameter> getParametersCollection() {
      return myBuildTypeOrTemplate.get().getParametersCollection();
    }

    @NotNull
    public Map<String, String> getParameters() {
      return myBuildTypeOrTemplate.get().getParameters();
    }

    @Nullable
    public String getParameterValue(@NotNull final String paramName) {
      return myBuildTypeOrTemplate.get().getParameterValue(paramName);
    }

    @NotNull
    public Collection<Parameter> getOwnParametersCollection() {
      return myBuildTypeOrTemplate.get().getOwnParametersCollection();
    }

    @NotNull
    public Map<String, String> getOwnParameters() {
      return myBuildTypeOrTemplate.get().getOwnParameters();
    }
  }

  public static class ProjectEntityWithParameters implements EntityWithParameters {
    @NotNull private final SProject myProject;

    public ProjectEntityWithParameters(@NotNull final SProject project) {
      myProject = project;
    }

    public void persist() {
      myProject.persist();
    }

    public void addParameter(@NotNull final Parameter param) {
      myProject.addParameter(param);
    }

    public void removeParameter(@NotNull final String paramName) {
      myProject.removeParameter(paramName);
    }

    @NotNull
    public Collection<Parameter> getParametersCollection() {
      return myProject.getParametersCollection();
    }

    @NotNull
    public Map<String, String> getParameters() {
      return myProject.getParameters();
    }

    @Nullable
    public String getParameterValue(@NotNull final String paramName) {
      return myProject.getParameterValue(paramName);
    }

    @NotNull
    public Collection<Parameter> getOwnParametersCollection() {
      return myProject.getOwnParametersCollection();
    }

    @NotNull
    public Map<String, String> getOwnParameters() {
      return myProject.getOwnParameters();
    }
  }
}
