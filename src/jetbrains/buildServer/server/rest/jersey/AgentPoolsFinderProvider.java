package jetbrains.buildServer.server.rest.jersey;

import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProvider;
import java.lang.reflect.Type;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.data.AgentPoolsFinder;

/**
 * @author Yegor.Yarko
 *         Date: 07.11.13
 */
@Provider
public class AgentPoolsFinderProvider implements InjectableProvider<Context, Type>, Injectable<AgentPoolsFinder> {
  private final AgentPoolsFinder myObject;

  public AgentPoolsFinderProvider(final AgentPoolsFinder object) {
    myObject = object;
  }

  public ComponentScope getScope() {
    return ComponentScope.Singleton;
  }

  public Injectable getInjectable(final ComponentContext ic, final Context context, final Type type) {
    if (type.equals(AgentPoolsFinder.class)) {
      return this;
    }
    return null;
  }

  public AgentPoolsFinder getValue() {
    return myObject;
  }
}
