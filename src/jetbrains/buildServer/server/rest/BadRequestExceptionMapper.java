package jetbrains.buildServer.server.rest;

import com.intellij.openapi.diagnostic.Logger;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * User: Yegor Yarko
 * Date: 30.03.2009
 */
@Provider
public class BadRequestExceptionMapper extends ExceptionMapperUtil implements ExceptionMapper<BadRequestException> {
  protected static final Logger LOG = Logger.getInstance(BadRequestExceptionMapper.class.getName());
  public Response toResponse(BadRequestException exception) {
    return reportError(Response.Status.NOT_FOUND, exception);
  }
}