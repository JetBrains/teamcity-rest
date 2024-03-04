package jetbrains.buildServer.server.rest.swagger;

import io.swagger.jaxrs.config.DefaultReaderConfig;
import io.swagger.jaxrs.config.ReaderConfig;
import javax.inject.Singleton;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

@Provider
public class SwaggerDynamicFeature implements Feature {
  @Override
  public boolean configure(FeatureContext context) {
    context.register(new AbstractBinder() {
      @Override
      protected void configure() {
        bind(DefaultReaderConfig.class)
          .to(ReaderConfig.class)
          .in(Singleton.class);
      }
    });

    return true;
  }
}
