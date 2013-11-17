package jetbrains.buildServer.server.rest.jersey;

import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.data.problem.TestFinder;

/**
 * @author Yegor.Yarko
 *         Date: 10.11.13
 */
@Provider
public class TestFinderProvider extends AbstractSingletonBeanProvider<TestFinder> {
  public TestFinderProvider(final TestFinder object) {
    super(object, TestFinder.class);
  }
}