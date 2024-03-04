package jetbrains.buildServer.server.rest.jersey.provider.annotated;

import org.jvnet.hk2.annotations.Contract;

/**
 * This interface allows to add spring beans to Jersey IoC container without annotating the bean class with {@link JerseyInjectable}.
 * This is useful when such bean is located in another module, and it's impossible to add an annotation.
 * <br/>
 * If adding {@link JerseyInjectable} annotation is possible, it's prefferable to implementing this interface.
 *
 * @apiNote Implemntations of this class must be spring beans.
 */
public interface JerseyInjectableBeanProvider {
  Class<?> getBeanClass();
}
