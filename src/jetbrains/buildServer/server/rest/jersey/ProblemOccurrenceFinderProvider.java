package jetbrains.buildServer.server.rest.jersey;

import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.data.problem.ProblemOccurrenceFinder;

/**
 * @author Yegor.Yarko
 *         Date: 10.11.13
 */
@Provider
public class ProblemOccurrenceFinderProvider extends AbstractSingletonBeanProvider<ProblemOccurrenceFinder> {
  public ProblemOccurrenceFinderProvider(final ProblemOccurrenceFinder object) {
    super(object, ProblemOccurrenceFinder.class);
  }
}