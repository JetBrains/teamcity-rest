package jetbrains.buildServer.server.rest.jersey;

import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.data.QueuedBuildFinder;

/**
 * @author Yegor.Yarko
 *         Date: 21.12.13
 */
@Provider
public class QueuedBuildFinderProvider extends AbstractSingletonBeanProvider<QueuedBuildFinder> {
    public QueuedBuildFinderProvider(final QueuedBuildFinder object) {
      super(object, QueuedBuildFinder.class);
    }
  }