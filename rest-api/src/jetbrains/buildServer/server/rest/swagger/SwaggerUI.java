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

package jetbrains.buildServer.server.rest.swagger;

import com.intellij.openapi.util.io.StreamUtil;
import io.swagger.annotations.Api;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.server.rest.SwaggerUIUtil;
import jetbrains.buildServer.server.rest.request.Constants;

@Path(Constants.API_URL + "/swaggerui")
@Api(hidden = true)
public class SwaggerUI {

  @Context
  private UriInfo myUri;

  @GET
  @Produces({MediaType.TEXT_HTML})
  public String serveSwaggerUI() {
    try (InputStream input = SwaggerUIUtil.getFileFromResources(SwaggerUIUtil.INDEX)) {
      return StreamUtil.readText(input, "UTF-8");
    } catch (IOException e) {
      throw new UncheckedIOException("Error while retrieving Swagger UI", e);
    }
  }

  @GET
  @Path("/{path:.*}")
  public Object serveSwaggerResource(@PathParam("path") String path) {
    if (path.equals(SwaggerUIUtil.INDEX)) {
      return serveSwaggerUI();
    }

    try (InputStream input = SwaggerUIUtil.getFileFromResources(path)) {
      if (path.endsWith(".js") || path.endsWith(".css")) {
        return StreamUtil.readText(input, "UTF-8");
      } else if (path.endsWith(".png")) {
        return ImageIO.read(input);
      }
      return input;
    } catch (IOException e) {
      throw new UncheckedIOException(String.format("Error while retrieving Swagger UI element %s", path), e);
    }
  }

}