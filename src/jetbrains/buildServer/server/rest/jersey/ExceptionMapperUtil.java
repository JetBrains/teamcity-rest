/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.sun.jersey.spi.inject.Errors;
import java.lang.reflect.Field;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
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
    StringBuffer responseText = new StringBuffer();
    responseText.append("Error has occurred during request processing (").append(statusDescription).append(").");

    //provide user-friendly message on missing or wrong Content-Type header
    if (statusCode == Response.Status.UNSUPPORTED_MEDIA_TYPE.getStatusCode()){
      //todo: response with supported content-types instead
      responseText.append("\nMake sure you have supplied correct Content-Type header.");
    }else{
      responseText.append("\nError: ").append(getMessageWithCauses(e)).append(message != null ? "\n" + message : "");
    }
    builder.entity(responseText.toString());
    final String logMessage = "Error" + (message != null ? " '" + message + "'" : "") + " for request " + requestUri +
                              ". Sending " + statusDescription + " error in response: " + e.toString();
    if (statusCode == HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
      LOG.warn(logMessage, e);
    } else {
      LOG.warn(logMessage);
      LOG.debug(logMessage, e);
    }
    return builder.build();
  }

  public static String getMessageWithCauses(Throwable e) {
    if (e == null){
      return "";
    }
    final String message = e.getMessage();
    String result = e.getClass().getName() + (message != null ? ": " + message : "");
    result = addKnownExceptionsData(e, result);
    result += appendCauseInfo(e);
    return result;
  }

  private static String addKnownExceptionsData(final Throwable e, String result) {
    if (e instanceof Errors.ErrorMessagesException) { //error message does not contain details
      final List<Errors.ErrorMessage> messages = ((Errors.ErrorMessagesException)e).messages;
      if (messages != null) {
        try {
          final Field field = Errors.ErrorMessage.class.getDeclaredField("message");
          field.setAccessible(true);
          result += " (messages: ";
          for (Errors.ErrorMessage errorMessage : messages) {
            // the data is not accessible otherwise
            result += "\"" + field.get(errorMessage) + "\",";
          }
          result += ")";
        } catch (NoSuchFieldException e1) {
          //ignore
        } catch (IllegalAccessException e1) {
          //ignore
        }
      }
    }
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
