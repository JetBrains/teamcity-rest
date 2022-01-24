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

package jetbrains.buildServer.server.rest.errors;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.jersey.ExceptionMapperBase;
import org.jetbrains.annotations.NotNull;

/**
 * User: Yegor Yarko
 * Date: 30.03.2009
 */
@Provider
public class OperationExceptionMapper extends ExceptionMapperBase<OperationException> {
  @Override
  public ResponseData getResponseData(@NotNull final OperationException e) {
    return new ResponseData(Response.Status.INTERNAL_SERVER_ERROR, "This is probably a TeamCity bug. Please report it to TeamCity support with the server debug logs.");
  }
}