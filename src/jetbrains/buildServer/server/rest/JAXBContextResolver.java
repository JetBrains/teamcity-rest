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

import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.api.json.JSONJAXBContext;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import jetbrains.buildServer.server.rest.data.BuildType;
import jetbrains.buildServer.server.rest.data.BuildTypes;
import jetbrains.buildServer.server.rest.data.Project;
import jetbrains.buildServer.server.rest.data.Projects;
import jetbrains.buildServer.server.rest.data.build.Build;
import jetbrains.buildServer.server.rest.data.build.Builds;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@Provider
public class JAXBContextResolver implements ContextResolver<JAXBContext> {
  private JAXBContext context;
  //todo: what it needed to be listed here?
  private Class[] types = {Build.class, BuildType.class, Project.class, Builds.class, BuildTypes.class, Projects.class};

  public JAXBContextResolver() throws Exception {
    this.context = new JSONJAXBContext(
      JSONConfiguration.natural().build(),
      types);
  }

  public JAXBContext getContext(Class<?> objectType) {
    return (types[0].equals(objectType)) ? context : null;
  }
}
