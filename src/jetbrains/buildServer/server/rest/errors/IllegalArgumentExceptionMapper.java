package jetbrains.buildServer.server.rest.errors;

import com.intellij.openapi.diagnostic.Logger;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.jersey.ExceptionMapperUtil;

/**
 * @author Yegor.Yarko
 *         Date: 02.06.13
 */
@Provider
public class IllegalArgumentExceptionMapper extends ExceptionMapperUtil implements ExceptionMapper<IllegalArgumentException> {
  protected static final Logger LOG = Logger.getInstance(IllegalArgumentExceptionMapper.class.getName());

  public Response toResponse(IllegalArgumentException exception) {
    final StackTraceElement[] stackTrace = exception.getStackTrace();
    if (stackTrace.length > 0 && !stackTrace[0].getClassName().startsWith("jetbrains")) {
      //Jersey can throw IllegalArgumentException when posting properly formed bean but of the different type then expected by the URL
      return reportError(Response.Status.BAD_REQUEST, exception,
                         "Most probably the URL requires submitting different data type. Please check the request URL and data are correct.");
    }
    return reportError(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), exception, "Error occurred while processing this request.", true);
  }
}