package jetbrains.buildServer.server.rest.jersey;

import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.data.AgentFinder;

/**
 * @author Yegor.Yarko
 *         Date: 25.12.13
 */
@Provider
public class AgentFinderProvider extends AbstractSingletonBeanProvider<AgentFinder> {
    public AgentFinderProvider(final AgentFinder object) {
      super(object, AgentFinder.class);
    }
  }
