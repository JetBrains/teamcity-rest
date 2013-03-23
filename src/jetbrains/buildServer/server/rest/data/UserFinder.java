package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.13
 */
public class UserFinder {
  private static final Logger LOG = Logger.getInstance(UserFinder.class.getName());
  @NotNull private final DataProvider myDataProvider;

  public UserFinder(@NotNull DataProvider dataProvider) {
    myDataProvider = dataProvider;
  }

  @Nullable
  public SUser getUserIfNotNull(@Nullable final String userLocator) {
    return userLocator == null ? null : getUser(userLocator);
  }

  @NotNull
  public SUser getUser(String userLocator) {
    if (StringUtil.isEmpty(userLocator)) {
      throw new BadRequestException("Empty user locator is not supported.");
    }

    final UserModel userModel = myDataProvider.getUserModel();
    final Locator locator = new Locator(userLocator);
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's username
      SUser user = userModel.findUserAccount(null, userLocator);
      if (user == null) {
        if (!"current".equals(userLocator)) {
          throw new NotFoundException("No user can be found by username '" + userLocator + "'.");
        }
        // support for predefined "current" keyword to get current user
        final SUser currentUser = myDataProvider.getCurrentUser();
        if (currentUser == null) {
          throw new NotFoundException("No current user.");
        } else {
          return currentUser;
        }
      }
      return user;
    }

    Long id = locator.getSingleDimensionValueAsLong("id");
    if (id != null) {
      SUser user = userModel.findUserById(id);
      if (user == null) {
        throw new NotFoundException("No user can be found by id '" + id + "'.");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("User locator '" + userLocator + "' has 'id' dimension and others. Others are ignored.");
      }
      return user;
    }

    String username = locator.getSingleDimensionValue("username");
    if (username != null) {
      SUser user = userModel.findUserAccount(null, username);
      if (user == null) {
        throw new NotFoundException("No user can be found by username '" + username + "'.");
      }
      return user;
    }
    throw new NotFoundException("User locator '" + userLocator + "' is not supported.");
  }
}
