package jetbrains.buildServer.server.rest.jersey;

import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProvider;
import java.lang.reflect.Type;
import javax.ws.rs.core.Context;

/**
 * @author Yegor.Yarko
 *         Date: 10.11.13
 */
public class AbstractSingletonBeanProvider<T> implements InjectableProvider<Context, Type>, Injectable<T> {
  private final T myObject;
  private final Class<T> myClass;

  public AbstractSingletonBeanProvider(final T object, final Class<T> classP) {
    myObject = object;
    myClass = classP;
  }

  public ComponentScope getScope() {
    return ComponentScope.Singleton;
  }

  public Injectable getInjectable(final ComponentContext ic, final Context context, final Type type) {
    if (type.equals(myClass)) {
      return this;
    }
    return null;
  }

  public T getValue() {
    return myObject;
  }
}