/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.jersey.ExceptionMapperUtil;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;

/**
 * User: Yegor Yarko
 * Date: 30.03.2009
 * <p/>
 * This will jopefull report Jersey-originated errors with more details
 */
@Provider
public class AccessDeniedExceptionMapper extends ExceptionMapperUtil implements ExceptionMapper<AccessDeniedException> {
  protected static final Logger LOG = Logger.getInstance(AccessDeniedExceptionMapper.class.getName());

  public Response toResponse(AccessDeniedException exception) {
    return reportError(Response.Status.FORBIDDEN, exception);
  }
}