/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import java.util.Map;
import javax.ws.rs.*;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Property;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeUtil;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.InheritableUserParametersHolder;
import jetbrains.buildServer.serverSide.Parameter;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.UserParametersHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.03.2015
 */
@Api(hidden = true) // To prevent appearing in Swagger#definitions
public class ParametersSubResource {

  @NotNull protected ServiceLocator myServiceLocator;

  @NotNull protected final EntityWithParameters myEntityWithParameters;
  @Nullable protected final String myParametersHref;

  public ParametersSubResource(final @NotNull ServiceLocator serviceLocator, final @NotNull EntityWithParameters entityWithParameters, final @Nullable String parametersHref) {
    myServiceLocator = serviceLocator;
    myEntityWithParameters = entityWithParameters;
    myParametersHref = parametersHref;
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public Properties getParameters(@QueryParam("locator") Locator locator, @QueryParam("fields") String fields) {
      return new Properties(myEntityWithParameters.getParametersCollection(), myEntityWithParameters.getOwnParametersCollection(), myParametersHref,
                            locator, new Fields(fields), myServiceLocator);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Property setParameter(Property parameter, @QueryParam("fields") String fields) {
    addParameter(parameter);
    myEntityWithParameters.persist();
    return Property.createFrom(parameter.name, myEntityWithParameters, new Fields(fields), myServiceLocator);
  }

  @NotNull
  private Parameter addParameter(@NotNull final Property parameter) {
    Map<String, String> ownParameters = myEntityWithParameters.getOwnParameters();
    return parameter.addTo(myEntityWithParameters, ownParameters == null ? null : ownParameters.keySet(), myServiceLocator);
  }

  @PUT
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Properties setParameters(Properties properties, @QueryParam("fields") String fields) {
    properties.setTo(myEntityWithParameters, myEntityWithParameters.getOwnParametersCollection(), myServiceLocator);
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
    return BuildTypeUtil.getParameter(parameterName, myEntityWithParameters, true, false, myServiceLocator);
  }

  @PUT
  @Path("/{name}/value")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setParameterValueLong(@PathParam("name") String parameterName, String newValue) {
    BuildTypeUtil.changeParameter(parameterName, newValue, myEntityWithParameters, myServiceLocator);
    myEntityWithParameters.persist();
    return BuildTypeUtil.getParameter(parameterName, myEntityWithParameters, false, false, myServiceLocator);
  }

  /**
   * Plain text support for pre-8.1 compatibility
   */
  @GET
  @Path("/{name}")
  @Produces("text/plain")
  public String getParameterValue(@PathParam("name") String parameterName) {
    return BuildTypeUtil.getParameter(parameterName, myEntityWithParameters, true, false, myServiceLocator);
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
    return BuildTypeUtil.getParameter(parameterName, myEntityWithParameters, false, false, myServiceLocator);
  }

  @PUT
  @Path("/{name}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Property setParameter(@PathParam("name") String parameterName, Property parameter, @QueryParam("fields") String fields) {
    parameter.name = parameterName; //overriding name in the entity with the value from URL
    addParameter(parameter);
    myEntityWithParameters.persist();
    return Property.createFrom(parameter.name, myEntityWithParameters, new Fields(fields), myServiceLocator);
  }

  @DELETE
  @Path("/{name}")
  public void deleteParameter(@PathParam("name") String parameterName) {
    BuildTypeUtil.deleteParameter(parameterName, myEntityWithParameters);
    myEntityWithParameters.persist();
  }


  public static abstract class EntityWithParameters implements UserParametersHolder {
    abstract public void persist();
    /**
     * @return null if own parameters are not supported.
     * @see also InheritableUserParametersHolder
     */
    @Nullable
    abstract public Collection<Parameter> getOwnParametersCollection();

    /**
     * @return null if own parameters are not supported.
     * @see also InheritableUserParametersHolder
     */
    @Nullable
    abstract public Map<String, String> getOwnParameters();

    @Nullable
    public Boolean isInherited(@NotNull final String parameterName){
      Map<String, String> ownParameters = getOwnParameters();
      return ownParameters == null ? null : !ownParameters.containsKey(parameterName);
    }
  }

  public static class BuildTypeEntityWithParameters extends EntityWithParameters {
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

    @Nullable
    @Override
    public Parameter getParameter(@NotNull final String paramName) {
      return myBuildTypeOrTemplate.get().getParameter(paramName);
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

  public static class ProjectEntityWithParameters extends InheritableUserParametersHolderEntityWithParameters {
    @NotNull private final SProject myProject;

    public ProjectEntityWithParameters(@NotNull final SProject project) {
      super(project);
      myProject = project;
    }

    public void persist() {
      myProject.persist();
    }
 }

  public static abstract class InheritableUserParametersHolderEntityWithParameters extends UserParametersHolderEntityWithParameters {
    @NotNull private final InheritableUserParametersHolder myEntity;

    public InheritableUserParametersHolderEntityWithParameters(@NotNull final InheritableUserParametersHolder entity) {
      super(entity);
      myEntity = entity;
    }

    @NotNull
    public Collection<Parameter> getOwnParametersCollection() {
      return myEntity.getOwnParametersCollection();
    }

    @NotNull
    public Map<String, String> getOwnParameters() {
      return myEntity.getOwnParameters();
    }
  }

  public static abstract class UserParametersHolderEntityWithParameters extends EntityWithParameters {
    @NotNull private final UserParametersHolder myEntity;

    public UserParametersHolderEntityWithParameters(@NotNull final UserParametersHolder entity) {
      myEntity = entity;
    }

    public void addParameter(@NotNull final Parameter param) {
      myEntity.addParameter(param);
    }

    public void removeParameter(@NotNull final String paramName) {
      myEntity.removeParameter(paramName);
    }

    @NotNull
    public Collection<Parameter> getParametersCollection() {
      return myEntity.getParametersCollection();
    }

    @Nullable
    @Override
    public Parameter getParameter(@NotNull final String paramName) {
      return myEntity.getParameter(paramName);
    }

    @NotNull
    public Map<String, String> getParameters() {
      return myEntity.getParameters();
    }

    @Nullable
    public String getParameterValue(@NotNull final String paramName) {
      return myEntity.getParameterValue(paramName);
    }

    @Nullable
    public Collection<Parameter> getOwnParametersCollection() {
      return null;
    }

    @Nullable
    public Map<String, String> getOwnParameters() {
      return null;
    }
  }
}
