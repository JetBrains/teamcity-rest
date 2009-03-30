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

package jetbrains.buildServer.server.rest;

import com.intellij.openapi.diagnostic.Logger;
import javax.ws.rs.core.Response;
import org.jetbrains.annotations.NotNull;

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
    LOG.debug("Sending " + responseStatus + " error in response.", e);
    return builder.build();
  }
}
