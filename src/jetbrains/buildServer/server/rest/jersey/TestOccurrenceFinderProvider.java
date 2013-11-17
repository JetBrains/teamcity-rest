package jetbrains.buildServer.server.rest.jersey;

import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;

/**
 * @author Yegor.Yarko
 *         Date: 17.11.13
 */
@Provider
public class TestOccurrenceFinderProvider extends AbstractSingletonBeanProvider<TestOccurrenceFinder> {
  public TestOccurrenceFinderProvider(final TestOccurrenceFinder object) {
    super(object, TestOccurrenceFinder.class);
  }
}