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

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.jersey.ExceptionMapperBase;
import jetbrains.buildServer.serverSide.MissingServerResponsibilityException;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.web.readOnly.MissingResponsibilityExceptionResolver;
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
public class AccessDeniedExceptionMapper extends ExceptionMapperBase<AccessDeniedException> {
  @Override
  public ResponseData getResponseData(@NotNull final AccessDeniedException e) {
    if (e instanceof MissingServerResponsibilityException) {
      int statusCode = MissingResponsibilityExceptionResolver.getMissingResponsibilityStatusCode();
      return new ResponseData(Response.Status.fromStatusCode(statusCode), null);
    }

    return new ResponseData(Response.Status.FORBIDDEN, "Access denied. Check the user has enough permissions to perform the operation.");
  }
}