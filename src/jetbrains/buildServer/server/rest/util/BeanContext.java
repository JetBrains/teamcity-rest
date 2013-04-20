package jetbrains.buildServer.server.rest.util;

import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.ServiceNotFoundException;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 20.04.13
 */
public class BeanContext {
  @NotNull private final BeanFactory myFactory;
  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final ApiUrlBuilder myApiUrlBuilder;

  public BeanContext(@NotNull final BeanFactory factory, @NotNull final ServiceLocator serviceLocator, @NotNull ApiUrlBuilder apiUrlBuilder) {
    myFactory = factory;
    myServiceLocator = serviceLocator;
    myApiUrlBuilder = apiUrlBuilder;
  }

  public <T> void autowire(T t){
    myFactory.autowire(t);
  }

  @NotNull
  public <T> T getSingletonService(@NotNull Class<T> serviceClass) throws ServiceNotFoundException {
    return myServiceLocator.getSingletonService(serviceClass);
  }

  @NotNull
  public ApiUrlBuilder getContextService(@NotNull Class<ApiUrlBuilder> serviceClass) throws ServiceNotFoundException {
        return myApiUrlBuilder;
  }
}
