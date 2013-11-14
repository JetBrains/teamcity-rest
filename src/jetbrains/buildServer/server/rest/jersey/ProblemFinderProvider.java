package jetbrains.buildServer.server.rest.jersey;

import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.data.problem.ProblemFinder;

/**
 * @author Yegor.Yarko
 *         Date: 10.11.13
 */
@Provider
public class ProblemFinderProvider extends AbstractSingletonBeanProvider<ProblemFinder> {
  public ProblemFinderProvider(final ProblemFinder object) {
    super(object, ProblemFinder.class);
  }
}