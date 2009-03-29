package jetbrains.buildServer.server.rest;

import jetbrains.buildServer.server.rest.data.*;

import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.ContextResolver;
import javax.xml.bind.JAXBContext;

import com.sun.jersey.api.json.JSONJAXBContext;
import com.sun.jersey.api.json.JSONConfiguration;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@Provider
public class JAXBContextResolver implements ContextResolver<JAXBContext> {
      private JAXBContext context;
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
