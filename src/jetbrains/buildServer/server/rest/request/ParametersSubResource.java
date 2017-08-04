/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.*;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.parameters.ParametersPersistableEntity;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Property;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeUtil;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.Parameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.03.2015
 */
@Api(hidden = true) // To prevent appearing in Swagger#definitions
public class ParametersSubResource {

  @NotNull protected final BeanContext myBeanContext;

  @NotNull protected final ParametersPersistableEntity myEntityWithParameters;
  @Nullable protected final String myParametersHref;

  public ParametersSubResource(final @NotNull BeanContext beanContext,
                               final @NotNull ParametersPersistableEntity entityWithParameters,
                               final @Nullable String parametersHref) {
    myBeanContext = beanContext;
    myEntityWithParameters = entityWithParameters;
    myParametersHref = parametersHref;
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public Properties getParameters(@QueryParam("locator") Locator locator, @QueryParam("fields") String fields) {
    Properties result = new Properties(myEntityWithParameters, myParametersHref, locator, new Fields(fields), myBeanContext);
    if (locator != null) locator.checkLocatorFullyProcessed();
    return result;
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Property setParameter(Property parameter, @QueryParam("fields") String fields) {
    addParameter(parameter);
    myEntityWithParameters.persist();
    return Property.createFrom(parameter.name, myEntityWithParameters, new Fields(fields), myBeanContext.getServiceLocator());
  }

  @NotNull
  private Parameter addParameter(@NotNull final Property parameter) {
    return parameter.addTo(myEntityWithParameters, myBeanContext.getServiceLocator());
  }

  @PUT
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Properties setParameters(Properties properties, @QueryParam("fields") String fields) {
    properties.setTo(myEntityWithParameters, myBeanContext.getServiceLocator());
    myEntityWithParameters.persist();
    return new Properties(myEntityWithParameters, myParametersHref, null, new Fields(fields), myBeanContext);
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
    return Property.createFrom(parameterName, myEntityWithParameters, new Fields(fields), myBeanContext.getServiceLocator());
  }

  @GET
  @Path("/{name}/value")
  @Produces("text/plain")
  public String getParameterValueLong(@PathParam("name") String parameterName) {
    return BuildTypeUtil.getParameter(parameterName, myEntityWithParameters, true, false, myBeanContext.getServiceLocator());
  }

  @PUT
  @Path("/{name}/value")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setParameterValueLong(@PathParam("name") String parameterName, String newValue) {
    BuildTypeUtil.changeParameter(parameterName, newValue, myEntityWithParameters, myBeanContext.getServiceLocator());
    myEntityWithParameters.persist();
    return BuildTypeUtil.getParameter(parameterName, myEntityWithParameters, false, false, myBeanContext.getServiceLocator());
  }

  /**
   * Plain text support for pre-8.1 compatibility
   */
  @GET
  @Path("/{name}")
  @Produces("text/plain")
  @ApiOperation(hidden = true, value = "Use getParameter instead")
  public String getParameterValue(@PathParam("name") String parameterName) {
    return BuildTypeUtil.getParameter(parameterName, myEntityWithParameters, true, false, myBeanContext.getServiceLocator());
  }

  /**
   * Plain text support for pre-8.1 compatibility
   */
  @PUT
  @Path("/{name}")
  @Consumes("text/plain")
  @Produces("text/plain")
  @ApiOperation(hidden = true, value = "Use setParameter instead")
  public String setParameterValue(@PathParam("name") String parameterName, String newValue) {
    BuildTypeUtil.changeParameter(parameterName, newValue, myEntityWithParameters, myBeanContext.getServiceLocator());
    myEntityWithParameters.persist();
    return BuildTypeUtil.getParameter(parameterName, myEntityWithParameters, false, false, myBeanContext.getServiceLocator());
  }

  @PUT
  @Path("/{name}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Property setParameter(@PathParam("name") String parameterName, Property parameter, @QueryParam("fields") String fields) {
    parameter.name = parameterName; //overriding name in the entity with the value from URL
    addParameter(parameter);
    myEntityWithParameters.persist();
    return Property.createFrom(parameter.name, myEntityWithParameters, new Fields(fields), myBeanContext.getServiceLocator());
  }

  @DELETE
  @Path("/{name}")
  public void deleteParameter(@PathParam("name") String parameterName) {
    BuildTypeUtil.deleteParameter(parameterName, myEntityWithParameters);
    myEntityWithParameters.persist();
  }
}
