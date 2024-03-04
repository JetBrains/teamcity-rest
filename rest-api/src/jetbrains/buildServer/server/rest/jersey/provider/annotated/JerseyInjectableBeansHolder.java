package jetbrains.buildServer.server.rest.jersey.provider.annotated;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

/**
 * Holds spring beans which are not annotated with {@link JerseyInjectable} but is registered via {@link JerseyInjectableBeanProvider}.
 */
@Service
public class JerseyInjectableBeansHolder {
  private final Set<Class<?>> myInjectableBeans;

  public JerseyInjectableBeansHolder(@NotNull List<JerseyInjectableBeanProvider> providers) {
    myInjectableBeans = providers.stream()
                                 .map(JerseyInjectableBeanProvider::getBeanClass)
                                 .collect(Collectors.toSet());
  }

  public boolean contains(@NotNull Class<?> beanClass) {
    return myInjectableBeans.contains(beanClass);
  }
}
