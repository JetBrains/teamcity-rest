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
import javax.ws.rs.*;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.parameters.ParametersPersistableEntity;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.ParameterType;
import jetbrains.buildServer.server.rest.model.Property;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 04/06/2016
 */
@Api(hidden = true) // To prevent appearing in Swagger#definitions
public class TypedParametersSubResource extends ParametersSubResource{
  public TypedParametersSubResource(@NotNull final ServiceLocator serviceLocator,
                                    @NotNull final ParametersPersistableEntity entityWithParameters, @NotNull final String parametersHref) {
    super(serviceLocator, entityWithParameters, parametersHref);
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
}
