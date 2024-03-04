package jetbrains.buildServer.server.rest.jersey;

import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyInjectableBeanProvider;
import jetbrains.buildServer.users.UserAvatarsManager;
import org.springframework.stereotype.Service;


@Service
public class UserAvatarsManagerProvider implements JerseyInjectableBeanProvider {

  @Override
  public Class<?> getBeanClass() {
    return UserAvatarsManager.class;
  }
}
