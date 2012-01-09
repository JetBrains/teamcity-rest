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

package jetbrains.buildServer.server.rest.jersey;

import com.sun.jersey.api.wadl.config.WadlGeneratorConfig;
import com.sun.jersey.api.wadl.config.WadlGeneratorDescription;
import com.sun.jersey.server.wadl.generators.WadlGeneratorApplicationDoc;
import com.sun.jersey.server.wadl.generators.WadlGeneratorJAXBGrammarGenerator;
import com.sun.jersey.server.wadl.generators.resourcedoc.WadlGeneratorResourceDocSupport;
import java.util.List;

/**
 * @author Yegor.Yarko
 *         Date: 06.08.2009
 */
public class WadlGenerator extends WadlGeneratorConfig {

  public static final String RESOURCEDOC_XML = "jetbrains/buildServer/server/rest/jersey/javadoc_generated.xml";

  @Override
  public List<WadlGeneratorDescription> configure() {
    WadlGeneratorConfigDescriptionBuilder builder = generator(WadlGeneratorApplicationDoc.class)
      .prop("applicationDocsStream", "jetbrains/buildServer/server/rest/jersey/application-doc.xml");

    builder = builder.generator(WadlGeneratorJAXBGrammarGenerator.class);

    //if (getClass().getClassLoader().getResourceAsStream("buildServerResources/rest-api-schema.xsd") != null) {
    //  builder = builder.generator(WadlGeneratorGrammarsSupport.class)
    //    .prop("grammarsStream", "jetbrains/buildServer/server/rest/jersey/application-grammars.xml");
    //}

    if (getClass().getClassLoader().getResourceAsStream(RESOURCEDOC_XML) != null) {
      builder = builder.generator(WadlGeneratorResourceDocSupport.class).
        prop("resourceDocStream", RESOURCEDOC_XML);
    }

    return builder.descriptions();
  }
}
