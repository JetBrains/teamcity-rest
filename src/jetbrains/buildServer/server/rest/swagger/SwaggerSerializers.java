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

package jetbrains.buildServer.server.rest.swagger;

import io.swagger.models.Swagger;
import io.swagger.util.Json;
import io.swagger.util.Yaml;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.util.AdditionalMediaTypes;
import jetbrains.buildServer.serverSide.TeamCityProperties;

@Provider
@Produces({MediaType.APPLICATION_JSON, AdditionalMediaTypes.APPLICATION_YAML})
public class SwaggerSerializers implements MessageBodyWriter<Swagger> {

  public static final String CHARSET_NAME = "UTF-8";

  public boolean isWriteable(Class type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return Swagger.class.isAssignableFrom(type);
  }

  public long getSize(Swagger data, Class type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return -1;
  }

  public void writeTo(Swagger data,
                      Class<?> type,
                      Type genericType,
                      Annotation[] annotations,
                      MediaType mediaType,
                      MultivaluedMap<String, Object> headers,
                      OutputStream out) throws IOException {
    // TODO: Consider something better instead of writeValueAsString. Catch 'pipe broken (connection closed)' errors
    final boolean pretty = TeamCityProperties.getBoolean(APIController.REST_RESPONSE_PRETTYFORMAT);
    if (mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
      if (pretty) {
        out.write(Json.pretty().writeValueAsString(data).getBytes(CHARSET_NAME));
      } else {
        out.write(Json.mapper().writeValueAsString(data).getBytes(CHARSET_NAME));
      }
    } else if (mediaType.isCompatible(AdditionalMediaTypes.APPLICATION_YAML_TYPE)) {
      if (pretty) {
        out.write(Yaml.pretty().writeValueAsString(data).getBytes(CHARSET_NAME));
      } else {
        out.write(Yaml.mapper().writeValueAsString(data).getBytes(CHARSET_NAME));
      }
    }
  }
}

