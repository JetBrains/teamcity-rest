/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import com.sun.jersey.api.model.AbstractResource;
import com.sun.jersey.api.wadl.config.WadlGeneratorConfig;
import com.sun.jersey.api.wadl.config.WadlGeneratorDescription;
import com.sun.jersey.server.wadl.generators.WadlGeneratorApplicationDoc;
import com.sun.jersey.server.wadl.generators.WadlGeneratorJAXBGrammarGenerator;
import com.sun.jersey.server.wadl.generators.resourcedoc.WadlGeneratorResourceDocSupport;
import com.sun.research.ws.wadl.Resource;
import java.util.List;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.StringUtil;

/**
 * @author Yegor.Yarko
 *         Date: 06.08.2009
 */
public class WadlGenerator extends WadlGeneratorConfig {

  public static final String RESOURCE_JAVADOC_XML = "jetbrains/buildServer/server/rest/jersey/javadoc_generated.xml";

  @Override
  public List<WadlGeneratorDescription> configure() {
    WadlGeneratorConfigDescriptionBuilder builder = generator(WadlGeneratorApplicationDoc.class)
      .prop("applicationDocsStream", "jetbrains/buildServer/server/rest/jersey/application-doc.xml");

    if (TeamCityProperties.getBooleanOrTrue("rest.wadl.patchResourcePathWithAPIVersion")){
      builder = builder.generator(PatchedWadlGenerator.class);
    } else{
      builder = builder.generator(WadlGeneratorJAXBGrammarGenerator.class);
    }

    if (getClass().getClassLoader().getResource(RESOURCE_JAVADOC_XML) != null) {
      builder = builder.generator(WadlGeneratorResourceDocSupport.class).
        prop("resourceDocStream", RESOURCE_JAVADOC_XML);
    }

    return builder.descriptions();
  }

  public static class PatchedWadlGenerator extends WadlGeneratorJAXBGrammarGenerator {
    @Override
    public Resource createResource(final AbstractResource ar, final String path) {
      Resource resource = super.createResource(ar, path);
      if (resource != null) {
        String originalPath = resource.getPath();
        if (originalPath != null && originalPath.startsWith(Constants.API_URL) && APIController.ourFirstBindPath != null) {
          resource.setPath(StringUtil.removeLeadingSlash(APIController.ourFirstBindPath) + originalPath.substring(Constants.API_URL.length()));
        }
      }
      return resource;
    }
  }
}
