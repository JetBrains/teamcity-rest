package jetbrains.buildServer.server.rest;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.core.Response;

/**
 * User: Yegor Yarko
 * Date: 30.03.2009
 */
public class ExceptionMapperUtil {
  protected static final Logger LOG = Logger.getInstance(ExceptionMapperUtil.class.getName());

  protected static Response reportError(@NotNull final Response.Status responseStatus, @NotNull final Exception e) {
    Response.ResponseBuilder builder = Response.status(responseStatus);
    builder.type("text/plain");
    builder.entity(e.getMessage());
    LOG.debug("Sending "+ responseStatus +" error in response.", e);
    return builder.build();
  }
}
