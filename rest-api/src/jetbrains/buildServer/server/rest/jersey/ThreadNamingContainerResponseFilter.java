/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

import com.sun.jersey.spi.container.*;
import java.io.IOException;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.util.Disposable;
import jetbrains.buildServer.util.NamedDaemonThreadFactory;
import org.springframework.stereotype.Component;

@Provider
@Consumes
@Produces
@Component
public class ThreadNamingContainerResponseFilter implements ContainerResponseFilter {
  @Override
  public ContainerResponse filter(ContainerRequest request, ContainerResponse response) {
    return new ThreadNamingContainerResponse(response);
  }

  public class ThreadNamingContainerResponse extends AdaptingContainerResponse {
    public ThreadNamingContainerResponse(ContainerResponse delegate) {
      super(delegate);
    }

    @Override
    public void write() throws IOException {
      Disposable threadName = NamedDaemonThreadFactory.patchThreadName("Serializing REST response");
      try {
        super.write();
      } finally {
        threadName.dispose();
      }
    }
  }
}
