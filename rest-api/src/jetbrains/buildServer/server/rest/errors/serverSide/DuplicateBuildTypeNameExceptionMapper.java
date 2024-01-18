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

package jetbrains.buildServer.server.rest.errors.serverSide;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.jersey.ExceptionMapperBase;
import jetbrains.buildServer.serverSide.DuplicateBuildTypeNameException;
import org.jetbrains.annotations.NotNull;

@Provider
public class DuplicateBuildTypeNameExceptionMapper extends ExceptionMapperBase<DuplicateBuildTypeNameException> {
  @Override
  public ResponseData getResponseData(@NotNull DuplicateBuildTypeNameException e) {
    return new ResponseData(Response.Status.BAD_REQUEST, e.getMessage());
  }
}