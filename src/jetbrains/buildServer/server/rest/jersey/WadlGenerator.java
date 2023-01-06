/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.sun.jersey.api.model.AbstractResource;
import com.sun.jersey.api.wadl.config.WadlGeneratorConfig;
import com.sun.jersey.api.wadl.config.WadlGeneratorDescription;
import com.sun.jersey.server.wadl.ApplicationDescription;
import com.sun.jersey.server.wadl.generators.WadlGeneratorApplicationDoc;
import com.sun.jersey.server.wadl.generators.WadlGeneratorJAXBGrammarGenerator;
import com.sun.jersey.server.wadl.generators.resourcedoc.WadlGeneratorResourceDocSupport;
import com.sun.research.ws.wadl.Doc;
import com.sun.research.ws.wadl.Param;
import com.sun.research.ws.wadl.Resource;
import com.sun.research.ws.wadl.Resources;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * WADL - Web Application Description Language.
 *
 * @author Yegor.Yarko
 * @since 06.08.2009
 */
public class WadlGenerator extends WadlGeneratorConfig {
  private static final Logger LOG = Logger.getInstance(WadlGenerator.class);

  public static final String RESOURCE_JAVADOC_XML = "jetbrains/buildServer/server/rest/jersey/javadoc_generated.xml";
  private static final boolean ourExcludePlugins = TeamCityProperties.getBooleanOrTrue("rest.wadl.excludePlugins");

  @Override
  public List<WadlGeneratorDescription> configure() {
    WadlGeneratorConfigDescriptionBuilder builder = generator(WadlGeneratorApplicationDoc.class)
      .prop("applicationDocsStream", "jetbrains/buildServer/server/rest/jersey/application-doc.xml");

    if (TeamCityProperties.getBooleanOrTrue("rest.wadl.patchResourcePathWithAPIVersion")) {
      builder = builder.generator(PatchedWadlGenerator.class);
    } else {
      builder = builder.generator(WadlGeneratorJAXBGrammarGenerator.class);
    }

    if (getClass().getClassLoader().getResource(RESOURCE_JAVADOC_XML) != null) {
      builder = builder.generator(WadlGeneratorResourceDocSupport.class).
        prop("resourceDocStream", RESOURCE_JAVADOC_XML);
    }

    return builder.descriptions();
  }

  public static class PatchedWadlGenerator extends WadlGeneratorJAXBGrammarGenerator {

    /**
     * Decorates the super method to remove (exclude) resources, provided by extensions.
     * <p/>
     * This is a workaround, because of missing support in Jersey 1.x.
     *
     * @param introspector The root description used to resolve these entries
     * @author Yegor Yarko
     * @since 12.2019
     */
    @Override
    public void attachTypes(final ApplicationDescription introspector) {
      if (ourExcludePlugins) {
        // It's a hack that we modify the wadl application resources in this method. There just does not seem to be a good place for it.
        for (final Resources resources : introspector.getApplication().getResources()) {
          try {
            resources.getResource().removeIf(r -> {
              boolean shouldRemove = r instanceof RestPluginWadlResource;
              if (shouldRemove) {
                LOG.info("Filtered out REST plugin resource from application.wadl for path \"" + r.getPath() + "\"");
              }
              return shouldRemove;
            });
          } catch (Exception e) {
            LOG.warnAndDebugDetails("Cannot filter out REST plugin resources from application.wadl", e);
          }
          //let's also sort them to provide a predictable order in the resulting wadl
          Collections.sort(resources.getResource(), (o1, o2) -> StringUtil.compare(o1.getPath(), o2.getPath()));
        }
      }
      super.attachTypes(introspector);
    }

    @Override
    public Resource createResource(final AbstractResource ar, final String path) {
      Resource resource = super.createResource(ar, path);
      if (resource != null) {
        String originalPath = resource.getPath();
        if (originalPath != null && originalPath.startsWith(Constants.API_URL) && APIController.getFirstBindPath() != null) {
          resource.setPath(StringUtil.removeLeadingSlash(APIController.getFirstBindPath()) + originalPath.substring(Constants.API_URL.length()));
        }
      }
      if (ourExcludePlugins && resource != null && ar.getResourceClass().getClassLoader() != getClass().getClassLoader()) {
        return new RestPluginWadlResource(resource); //wrap so that we can later check where the wadl resource comes from
      }
      return resource;
    }

    class RestPluginWadlResource extends Resource {
      @NotNull private final Resource myDelegate;
      public RestPluginWadlResource(@NotNull Resource delegate) {super(); myDelegate = delegate;}
      @Override public List<Doc> getDoc() {return myDelegate.getDoc();}
      @Override public List<Param> getParam() {return myDelegate.getParam();}
      @Override public List<Object> getMethodOrResource() {return myDelegate.getMethodOrResource();}
      @Override public List<Object> getAny() {return myDelegate.getAny();}
      @Override public String getId() {return myDelegate.getId();}
      @Override public void setId(final String value) {myDelegate.setId(value);}
      @Override public List<String> getType() {return myDelegate.getType();}
      @Override public String getQueryType() {return myDelegate.getQueryType();}
      @Override public void setQueryType(final String value) {myDelegate.setQueryType(value);}
      @Override public String getPath() {return myDelegate.getPath();}
      @Override public void setPath(final String value) {myDelegate.setPath(value);}
      @Override public Map<QName, String> getOtherAttributes() {return myDelegate.getOtherAttributes();
      }
    }
  }
}
