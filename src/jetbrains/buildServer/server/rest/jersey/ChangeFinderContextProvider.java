package jetbrains.buildServer.server.rest.jersey;

import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProvider;
import java.lang.reflect.Type;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.data.ChangeFinder;

/**
 * @author Yegor.Yarko
 *         Date: 12.05.13
 */
@Provider
public class ChangeFinderContextProvider implements InjectableProvider<Context, Type>, Injectable<ChangeFinder> {
  private final ChangeFinder myFinder;

  public ChangeFinderContextProvider(final ChangeFinder finder) {
    myFinder = finder;
  }

  public ComponentScope getScope() {
    return ComponentScope.Singleton;
  }

  public Injectable getInjectable(final ComponentContext ic, final Context context, final Type type) {
    if (type.equals(ChangeFinder.class)) {
      return this;
    }
    return null;
  }

  public ChangeFinder getValue() {
    return myFinder;
  }
}