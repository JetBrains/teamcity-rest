/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.jersey.ExceptionMapperUtil;

/**
 * User: Yegor Yarko
 * Date: 30.03.2009
 * <p/>
 * This will hopefully report Jersey-originated errors with more details
 */
@Provider
public class WebApplicationExceptionMapper extends ExceptionMapperUtil implements ExceptionMapper<WebApplicationException> {
  protected static final Logger LOG = Logger.getInstance(WebApplicationExceptionMapper.class.getName());

  public Response toResponse(WebApplicationException exception) {
    final Response.Status status = Response.Status.fromStatusCode(exception.getResponse().getStatus());
    return reportError(status != null ? status : Response.Status.INTERNAL_SERVER_ERROR, exception);
  }
}