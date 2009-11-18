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

package jetbrains.buildServer.server.rest.jersey;

import com.intellij.openapi.diagnostic.Logger;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.jetbrains.annotations.NotNull;

/**
 * User: Yegor Yarko
 * Date: 30.03.2009
 */
public class ExceptionMapperUtil {
  protected static final Logger LOG = Logger.getInstance(ExceptionMapperUtil.class.getName());

  @Context
  UriInfo myUriInfo;

  protected Response reportError(@NotNull final Response.Status responseStatus, @NotNull final Exception e) {
    Response.ResponseBuilder builder = Response.status(responseStatus);
    builder.type("text/plain");
    builder.entity("Error has occurred during request processing (" + responseStatus +
                   "). Error: " + getMessageWithCauses(e) + "\nPlease check URL is correct. See details in the server log.");
    LOG.warn("Error for request " + myUriInfo.getRequestUri() + ". Sending " + responseStatus + " error in response: " + e.toString());
    LOG.debug("Error for request " + myUriInfo.getRequestUri() + ". Sending " + responseStatus + " error in response.", e);
    return builder.build();
  }

  private static String getMessageWithCauses(Throwable e) {
    final String message = e.getMessage();
    String result = e.getClass().getName() + (message != null ? ": " + message : "");
    final Throwable cause = e.getCause();
    if (cause != null && cause != e) {
      result += ", caused by: " + getMessageWithCauses(cause);
    }
    return result;
  }
}
