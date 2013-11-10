package jetbrains.buildServer.server.rest.jersey;

import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationFinder;

/**
 * @author Yegor.Yarko
 *         Date: 10.11.13
 */
@Provider
public class InvestigationFinderProvider extends AbstractSingletonBeanProvider<InvestigationFinder> {
  public InvestigationFinderProvider(final InvestigationFinder object) {
    super(object, InvestigationFinder.class);
  }
}