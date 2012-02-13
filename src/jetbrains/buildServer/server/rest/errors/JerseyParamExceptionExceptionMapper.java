/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.sun.jersey.api.ParamException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.jersey.ExceptionMapperUtil;

@Provider
public class JerseyParamExceptionExceptionMapper extends ExceptionMapperUtil implements ExceptionMapper<ParamException> {
  protected static final Logger LOG = Logger.getInstance(JerseyParamExceptionExceptionMapper.class.getName());

  public Response toResponse(com.sun.jersey.api.ParamException exception) {
    return reportError(Response.Status.BAD_REQUEST, exception, "Error processing request parameter '" + exception.getParameterName() + "'. Check supported formats.");
  }
}