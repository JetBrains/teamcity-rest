package jetbrains.buildServer.server.rest.jersey;

import java.io.IOException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;
import jetbrains.buildServer.util.Disposable;
import jetbrains.buildServer.util.NamedDaemonThreadFactory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.process.internal.RequestScoped;

@Provider
public class ThreadNamingWriterInterceptorProvider implements Feature {
  @Override
  public boolean configure(FeatureContext context) {
    context.register(new AbstractBinder() {
      @Override
      protected void configure() {
        bind(ThreadNamingWriterInterceptor.class)
          .in(RequestScoped.class);
      }
    });

    return true;
  }

  public static class ThreadNamingWriterInterceptor implements WriterInterceptor {
    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
      Disposable threadName = NamedDaemonThreadFactory.patchThreadName("Serializing REST response");
      try {
        context.proceed();
      } finally {
        threadName.dispose();
      }
    }
  }
}
