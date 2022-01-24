/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.swagger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import com.intellij.openapi.util.text.StringUtil;
import com.sun.jersey.spi.resource.Singleton;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.config.FilterFactory;
import io.swagger.config.SwaggerConfig;
import io.swagger.converter.ModelConverters;
import io.swagger.core.filter.SpecFilter;
import io.swagger.core.filter.SwaggerSpecFilter;
import io.swagger.jaxrs.config.DefaultJaxrsScanner;
import io.swagger.jaxrs.config.ReaderConfig;
import io.swagger.models.Model;
import io.swagger.models.Swagger;
import io.swagger.models.Tag;
import io.swagger.util.Json;
import io.swagger.util.Yaml;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.jersey.JacksonObjectMapperResolver;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.server.rest.util.AdditionalMediaTypes;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Path(Constants.API_URL + "/swagger.{type:json|yaml}")
@Singleton
@Api(hidden = true)
public class SwaggerResource {

  @Context
  private SwaggerConfig mySwaggerConfig;
  @Context
  private DataProvider myDataProvider;
  @Context
  private ReaderConfig myReaderConfig;
  @Context
  private Application myApplication;

  @GET
  @Produces({MediaType.APPLICATION_JSON, AdditionalMediaTypes.APPLICATION_YAML})
  @ApiOperation(value = "The swagger definition in either JSON or YAML", hidden = true)
  public Response getSwagger(@Context HttpHeaders headers, @Context UriInfo uriInfo, @PathParam("type") String type) {
    final MediaType mediaType;
    if (!StringUtil.isEmptyOrSpaces(type) && type.trim().equalsIgnoreCase("yaml")) {
      mediaType = AdditionalMediaTypes.APPLICATION_YAML_TYPE;
    } else {
      mediaType = MediaType.APPLICATION_JSON_TYPE;
    }

    final Swagger swagger = process(headers, uriInfo);
    return Response.ok().entity(swagger).type(mediaType).build();
  }

  private final AtomicReference<Swagger> mySwagger = new AtomicReference<Swagger>();

  @NotNull
  protected synchronized Swagger getSwagger() {
    Swagger swagger = mySwagger.get();
    if (swagger != null) return swagger;
    synchronized (mySwagger) {
      swagger = mySwagger.get();
      if (swagger != null) return swagger;

      // Configure Swagger internals first to make sure it would properly analyze our resources
      Json.mapper().registerModule(new JaxbAnnotationModule());
      Yaml.mapper().registerModule(new JaxbAnnotationModule());
      final JacksonObjectMapperResolver resolver = myDataProvider.getBean(JacksonObjectMapperResolver.class);
      final ObjectMapper mapper = resolver.getContext(ObjectMapper.class);
      ModelConverters.getInstance().addConverter(new ExtensionModelResolver(mapper));

      // Let's create swagger and populate it

      swagger = new Swagger();

      final DefaultJaxrsScanner scanner = new DefaultJaxrsScanner();

      Set<Class<?>> classes = scanner.classesFromContext(myApplication, null);
      if (classes == null) classes = Collections.emptySet();

      final LocatorAwareReader reader = new LocatorAwareReader(swagger, myReaderConfig);
      swagger = reader.read(classes);

      // Sort output maps and lists
      swagger.setPaths(SwaggerUtil.getOrderedMap(swagger.getPaths()));
      swagger.setDefinitions(SwaggerUtil.getOrderedMap(swagger.getDefinitions()));
      swagger.setParameters(SwaggerUtil.getOrderedMap(swagger.getParameters()));
      swagger.setResponses(SwaggerUtil.getOrderedMap(swagger.getResponses()));
      swagger.setSecurityDefinitions(SwaggerUtil.getOrderedMap(swagger.getSecurityDefinitions()));
      swagger.getTags().sort(Comparator.comparing(Tag::getName));

      for (Model model : swagger.getDefinitions().values()) {
        model.setProperties(SwaggerUtil.getOrderedMap(model.getProperties()));
      }

      // Analyze for unused definitions
      SwaggerUtil.doAnalyzeSwaggerDefinitionReferences(swagger);

      if (mySwaggerConfig != null) {
        mySwaggerConfig.configure(swagger);
      }
      mySwagger.compareAndSet(null, swagger);
      return swagger;
    }
  }

  @NotNull
  protected Swagger process(HttpHeaders headers, UriInfo uriInfo) {
    Swagger swagger = getSwagger();
    final SwaggerSpecFilter filter = FilterFactory.getFilter();
    if (filter != null) {
      SpecFilter f = new SpecFilter();
      swagger = f.filter(swagger, filter, getQueryParams(uriInfo.getQueryParameters()), getCookies(headers), getHeaders(headers));
    }
    return swagger;
  }

  @NotNull
  protected static Map<String, List<String>> getQueryParams(@Nullable final MultivaluedMap<String, String> params) {
    return getNotNullMap(params);
  }

  @NotNull
  protected static Map<String, List<String>> getHeaders(@Nullable final HttpHeaders headers) {
    if (headers != null) {
      return getNotNullMap(headers.getRequestHeaders());
    }
    return new HashMap<String, List<String>>();
  }

  @NotNull
  protected static Map<String, String> getCookies(@Nullable final HttpHeaders headers) {
    Map<String, String> output = new HashMap<String, String>();
    if (headers != null) {
      final Map<String, Cookie> cookies = headers.getCookies();
      if (cookies != null) {
        CollectionsUtil.convertMap(cookies, output, CollectionsUtil.SAME, new Converter<String, Cookie>() {
          public String createFrom(@NotNull final Cookie source) {
            return source.getValue();
          }
        });
      }
    }
    return output;
  }

  @NotNull
  private static <K, V> Map<K, List<V>> getNotNullMap(@Nullable final MultivaluedMap<K, V> params) {
    final Map<K, List<V>> output = new HashMap<K, List<V>>();
    if (params != null) {
      output.putAll(params);
    }
    return output;
  }
}