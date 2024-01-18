/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.jersey;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.intellij.openapi.diagnostic.Logger;
import java.text.SimpleDateFormat;
import java.util.Locale;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.model.Constants;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.springframework.stereotype.Component;

/**
 * @author Vladislav.Rassokhin
 */
@Component
@Provider
@Produces(MediaType.APPLICATION_JSON)
public class JacksonObjectMapperResolver implements ContextResolver<ObjectMapper> {

  final static Logger LOG = Logger.getInstance(JacksonObjectMapperResolver.class.getName());

  private final ObjectMapper myMapper;

  public JacksonObjectMapperResolver() {
    myMapper = new ObjectMapper();
    myMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    myMapper.setAnnotationIntrospector(new JaxbAnnotationIntrospector(myMapper.getTypeFactory()));
    myMapper.setDateFormat(new SimpleDateFormat(Constants.TIME_FORMAT, Locale.ENGLISH));
    myMapper.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, true);
    myMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, TeamCityProperties.getBoolean("rest.response.json.deserialize.ignoreUnknownProperties"));
    if (TeamCityProperties.getBoolean(APIController.REST_RESPONSE_PRETTYFORMAT)) {
      myMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    }
  }

  public ObjectMapper getContext(Class<?> type) {
    LOG.debug("Using own customized ObjectMapper for class '" + type.getCanonicalName() + "'");

//    final String name = type.getPackage().getName();
//    return name.startsWith("jetbrains.buildServer.server.rest") ? myMapper : null;

    return myMapper;
  }
}