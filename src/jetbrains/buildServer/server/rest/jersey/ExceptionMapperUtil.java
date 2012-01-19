/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 30.03.2009
 */
public class ExceptionMapperUtil {
  protected static final Logger LOG = Logger.getInstance(ExceptionMapperUtil.class.getName());

  @Context
  UriInfo myUriInfo;

  protected Response reportError(@NotNull final Response.Status responseStatus, @NotNull final Exception e, @Nullable final String message) {
    return reportError(responseStatus.getStatusCode(), e, message);
  }

  protected Response reportError(final int statusCode, @NotNull final Exception e, @Nullable final String message) {
    return getRestErrorResponse(statusCode, e, message, myUriInfo.getRequestUri().toString());
  }

  public static Response getRestErrorResponse(final int statusCode,
                                              @NotNull final Throwable e,
                                              @Nullable final String message,
                                              @NotNull String requestUri) {
    Response.Status status = Response.Status.fromStatusCode(statusCode);
    final String statusDescription = (status != null) ? status.toString() : Integer.toString(statusCode);
    Response.ResponseBuilder builder = Response.status(statusCode);
    builder.type("text/plain");
    builder.entity("Error has occurred during request processing (" + statusDescription +
                   ").\nError: " + getMessageWithCauses(e) + (message != null ? "\n" + message : ""));
    final String logMessage = "Error" + (message != null ? " '" + message + "'" : "") + " for request " + requestUri +
                              ". Sending " + statusDescription + " error in response: " + e.toString();
    LOG.warn(logMessage);
    LOG.debug(logMessage, e);
    return builder.build();
  }

  public static String getMessageWithCauses(Throwable e) {
    if (e == null){
      return "";
    }
    final String message = e.getMessage();
    String result = e.getClass().getName() + (message != null ? ": " + message : "");
    result += appendCauseInfo(e);
    return result;
  }

  private static String appendCauseInfo(final Throwable e) {
    final Throwable cause = e.getCause();
    if (cause != null && cause != e) {
      final String message = e.getMessage();
      final String causeMessage = cause.getMessage();
      if (message != null && causeMessage != null && message.contains(causeMessage)){
        //skip cause
        return appendCauseInfo(cause);
      }
      return  ", caused by: " + getMessageWithCauses(cause);
    }
    return "";
  }
}
