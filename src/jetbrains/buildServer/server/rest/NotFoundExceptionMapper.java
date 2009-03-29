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
public class NotFoundExceptionMapper extends ExceptionMapperUtil implements ExceptionMapper<NotFoundException> {
  protected static final Logger LOG = Logger.getInstance(NotFoundExceptionMapper.class.getName());
  public Response toResponse(NotFoundException exception) {
    return reportError(Response.Status.NOT_FOUND, exception);
  }
}