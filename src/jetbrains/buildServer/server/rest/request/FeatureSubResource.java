/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.util.BeanContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 04/06/2016
 */
@Api(hidden = true) // To prevent appearing in Swagger#definitions
public class FeatureSubResource<M, S> {
  private static final String PROPERTIES = "/properties";
  @NotNull private final Entity<M, S> myEntity;

  @NotNull private final BeanContext myBeanContext;

  interface Entity<M, S> {
    public void persist();

    @NotNull
    public S getSingle(final @NotNull String featureLocator, final @NotNull Fields fields, final @NotNull BeanContext beanContext);

    public void delete(final @NotNull String featureLocator, final @NotNull ServiceLocator serviceLocator);

    /**
     * @return locator of the new feature
     */
    @NotNull
    public String replace(final @NotNull String featureLocator, final @NotNull S newFeature, final @NotNull ServiceLocator serviceLocator);

    /**
     * @return locator of the new feature
     */
    @NotNull
    public String add(final @NotNull S entityToAdd, final @NotNull ServiceLocator serviceLocator);

    @NotNull
    public M get(final @Nullable String locator, final @NotNull Fields fields, final @NotNull BeanContext beanContext);

    public void replaceAll(final @NotNull M newEntities, final @NotNull ServiceLocator serviceLocator);

    ParametersPersistableEntity getParametersHolder(final @NotNull String featureLocator);

    String getHref();

    //String setSetting(final @NotNull String featureLocator, final @NotNull String settingName, final @Nullable String newValue);
    //
    //String getSetting(final @NotNull String featureLocator, final @NotNull String settingName);

    //todo: return true/false result in delete and replaceAll ???
  }

  @NotNull
  public static String getPropertiesHref(@NotNull String baseHref){
    return baseHref + PROPERTIES;
  }

  public FeatureSubResource(final @NotNull BeanContext beanContext, final @NotNull Entity<M, S> entity) {
    myEntity = entity;
    myBeanContext = beanContext;
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public M get(@QueryParam("locator") String locator, @QueryParam("fields") String fields) {
    return myEntity.get(locator, new Fields(fields), myBeanContext);
  }

  @PUT
  @Produces({"application/xml", "application/json"})
  @Consumes({"application/xml", "application/json"})
  public M replaceAll(M newEntities, @QueryParam("fields") String fields) {
    myEntity.replaceAll(newEntities, myBeanContext.getServiceLocator());
    myEntity.persist();
    return myEntity.get(null, new Fields(fields), myBeanContext);
  }

  @POST
  @Produces({"application/xml", "application/json"})
  @Consumes({"application/xml", "application/json"})
  public S add(S entityToAdd, @QueryParam("fields") String fields) {
    final String resultId = myEntity.add(entityToAdd, myBeanContext.getServiceLocator());
    myEntity.persist();
    return myEntity.getSingle(resultId, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{featureLocator}")
  @Produces({"application/xml", "application/json"})
  public S getSingle(@PathParam("featureLocator") String featureLocator, @QueryParam("fields") String fields) {
    return myEntity.getSingle(featureLocator, new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{featureLocator}")
  public void delete(@PathParam("featureLocator") String featureLocator) {
    myEntity.delete(featureLocator, myBeanContext.getServiceLocator());
    myEntity.persist();
  }

  @PUT
  @Path("/{featureLocator}")
  @Produces({"application/xml", "application/json"})
  public S replace(@PathParam("featureLocator") String featureLocator, @NotNull S featureDescription, @QueryParam("fields") String fields) {
    final String resultId = myEntity.replace(featureLocator, featureDescription, myBeanContext.getServiceLocator());
    myEntity.persist();
    return myEntity.getSingle(resultId, new Fields(fields), myBeanContext);
  }

  @Path("/{featureLocator}" + PROPERTIES)
  public ParametersSubResource getParametersSubResource(@PathParam("featureLocator") String featureLocator, @QueryParam("fields") String fields) {
    return new ParametersSubResource(myBeanContext, myEntity.getParametersHolder(featureLocator),
                                     myEntity.getHref() == null ? null : myEntity.getHref() + "/" + featureLocator + "/properties");
  }

  //@GET
  //@Path("/{featureLocator}/{setting}")
  //@Produces({"text/plain"})
  //public String getFeatureSetting(@PathParam("featureLocator") String featureLocator,
  //                                @PathParam("setting") String name) {
  //  return myEntity.getSetting(featureLocator, name);
  //}
  //
  //@PUT
  //@Path("/{featureLocator}/{setting}")
  //@Consumes({"text/plain"})
  //@Produces({"text/plain"})
  //public String changeFeatureSetting(@PathParam("featureLocator") String featureLocator,
  //                                 @PathParam("setting") String name, String newValue) {
  //  myEntity.setSetting(featureLocator, name, newValue);
  //  myEntity.persist();
  //  return myEntity.getSetting(featureLocator, name);
  //}
}
