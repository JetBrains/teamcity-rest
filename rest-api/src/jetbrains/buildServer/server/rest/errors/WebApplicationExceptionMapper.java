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

package jetbrains.buildServer.server.rest.errors;

import com.intellij.openapi.util.text.StringUtil;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.jersey.ExceptionMapperBase;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * User: Yegor Yarko
 * Date: 30.03.2009
 * <p/>
 * This will hopefully report Jersey-originated errors with more details
 */
@Provider
@Component
public class WebApplicationExceptionMapper extends ExceptionMapperBase<WebApplicationException> {
    @Override
   public ResponseData getResponseData(@NotNull final WebApplicationException e) {
    final Response exceptionResponse = e.getResponse();
    final MultivaluedMap<String, Object> metadata = exceptionResponse.getMetadata();
    String metadataDetails = "";
    if (metadata != null && metadata.size() != 0) {
      metadataDetails = " Metadata: " + StringUtil.join(metadata.entrySet(), entry -> entry.getKey() + ":" + entry.getValue(), ", ");
    }
    return new ResponseData(exceptionResponse.getStatus(), "Not supported request. Check that URL, HTTP method and transferred data are correct." + metadataDetails);
  }
}