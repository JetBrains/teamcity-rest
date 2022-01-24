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
 * @author Yegor.Yarko
 * Date: 02.06.13
 */
@Provider
public class IllegalArgumentExceptionMapper extends ExceptionMapperBase<IllegalArgumentException> {
  @Override
  public ResponseData getResponseData(@NotNull final IllegalArgumentException e) {
    final StackTraceElement[] stackTrace = e.getStackTrace();
    if (stackTrace.length > 0 && !stackTrace[0].getClassName().startsWith("jetbrains")) {
      //Jersey can throw IllegalArgumentException when posting properly formed bean but of the different type then expected by the URL
      return new ResponseData(Response.Status.BAD_REQUEST, "Most probably the URL requires submitting different data type. Please check the request URL and data are correct.");
    }
    return new ResponseData(Response.Status.INTERNAL_SERVER_ERROR, "Error occurred while processing this request.");
  }
}