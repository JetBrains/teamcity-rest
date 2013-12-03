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
import com.intellij.openapi.util.text.StringUtil;
import com.sun.jersey.spi.inject.Errors;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.web.util.WebUtil;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 30.03.2009
 */
public class ExceptionMapperUtil {
  protected static final Logger LOG = Logger.getInstance(ExceptionMapperUtil.class.getName());
  public static final String REST_INCLUDE_EXCEPTION_STACKTRACE_PROPERTY = "rest.response.debug.includeExceptionStacktrace";
  public static final String REST_INCLUDE_REQUEST_DETAILS_INTO_ERRORS = "rest.log.includeRequestDetails";

  @Context private UriInfo myUriInfo;
  @Context private HttpServletRequest myRequest;

  protected Response reportError(@NotNull final Response.Status responseStatus, @NotNull final Exception e, @Nullable final String message) {
    return reportError(responseStatus.getStatusCode(), e, message, false);
  }

  protected Response reportError(final int statusCode, @NotNull final Exception e, @Nullable final String message, final boolean isInternalError) {
    return processRestErrorResponse(statusCode, e, message, myUriInfo.getRequestUri().toString(), isInternalError, myRequest);
  }

  public static Response processRestErrorResponse(final int statusCode,
                                                  @Nullable final Throwable e,
                                                  @Nullable final String message,
                                                  @NotNull String requestUri,
                                                  final boolean isInternalError,
                                                  @NotNull final HttpServletRequest request) {
    final String responseText = getResponseTextAndLogRestErrorErrorMessage(statusCode, e, message, requestUri, isInternalError, Level.WARN, request);
    Response.ResponseBuilder builder = Response.status(statusCode);
    builder.type("text/plain");
    builder.entity(responseText);
    return builder.build();
  }

  public static String getResponseTextAndLogRestErrorErrorMessage(final int statusCode,
                                                                  @Nullable final Throwable e,
                                                                  @Nullable final String message,
                                                                  @NotNull String requestUri,
                                                                  final boolean isInternalError,
                                                                  final Level level,
                                                                  @NotNull final HttpServletRequest request) {
    Response.Status status = Response.Status.fromStatusCode(statusCode);
    final String statusDescription = (status != null) ? status.toString() : Integer.toString(statusCode);
    StringBuffer responseText = new StringBuffer();
    responseText.append("Error has occurred during request processing (").append(statusDescription).append(").");

    //provide user-friendly message on missing or wrong Content-Type header
    if (statusCode == Response.Status.UNSUPPORTED_MEDIA_TYPE.getStatusCode()){
      //todo: response with supported content-types instead
      responseText.append("\nMake sure you have supplied correct Content-Type header.");
    }else{
      responseText.append("\nError: ");
      responseText.append(getMessageWithCauses(e));
      if (message != null) responseText.append("\n").append(message);
    }
    String result = responseText.toString();
    final String singleLineMessageStep1 = StringUtil.replace(result, ".\n", ". ");
    final String singleLineMessage = singleLineMessageStep1 == null ? "" : StringUtil.replace(singleLineMessageStep1, "\n", ". ");
    String logMessage;
    if (TeamCityProperties.getBooleanOrTrue(REST_INCLUDE_REQUEST_DETAILS_INTO_ERRORS)){
      logMessage = singleLineMessage + " Request: " + WebUtil.getRequestDump(request) + ".";
    }else{
      logMessage = singleLineMessage + " URL: " + requestUri + ".";
    }

    if (isInternalError) {
      logMessage(LOG, level, logMessage, e);
    } else {
      logMessage(LOG, level, logMessage);
      LOG.debug(logMessage, e);
    }

    final String includeStacktrace = TeamCityProperties.getProperty(REST_INCLUDE_EXCEPTION_STACKTRACE_PROPERTY);
    if (e != null && ("true".equals(includeStacktrace) || "any".equals(includeStacktrace) || String.valueOf(statusCode).startsWith(includeStacktrace))){
      StringWriter sw = new StringWriter();
      sw.write("\n\n");
      e.printStackTrace(new PrintWriter(sw));
      sw.write("\nThe stacktrace is included as '" + REST_INCLUDE_EXCEPTION_STACKTRACE_PROPERTY + "' internal property is set.");
      result += sw.toString();
    }
    return result;
  }


  private static void logMessage(final Logger log, final Level level, final String message) {
    if (level.isGreaterOrEqual(Level.ERROR)) {
      log.error(message);
    } else if (level.isGreaterOrEqual(Level.WARN)) {
      log.warn(message);
    } else if (level.isGreaterOrEqual(Level.INFO)) {
      log.info(message);
    }
  }

  private static void logMessage(final Logger log, final Level level, final String message, final Throwable e) {
    if (level.isGreaterOrEqual(Level.ERROR)) {
      log.error(message, e);
    } else if (level.isGreaterOrEqual(Level.WARN)) {
      log.warn(message, e);
    } else if (level.isGreaterOrEqual(Level.INFO)) {
      log.info(message, e);
    }
  }

  public static String getMessageWithCauses(@Nullable Throwable e) {
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
