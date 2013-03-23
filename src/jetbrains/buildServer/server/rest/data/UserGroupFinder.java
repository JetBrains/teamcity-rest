package jetbrains.buildServer.server.rest.data;

import java.util.ArrayList;
import java.util.Collection;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.groups.UserGroupManager;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.13
 */
public class UserGroupFinder {
  @NotNull private final UserGroupManager myUserGroupManager;

  public UserGroupFinder(@NotNull UserGroupManager userGroupManager) throws LocatorProcessException {
    myUserGroupManager = userGroupManager;
  }

  @NotNull
  public SUserGroup getGroup(final String groupLocator) {
    if (StringUtil.isEmpty(groupLocator)) {
      throw new BadRequestException("Empty group locator is not supported.");
    }

    final Locator locator = new Locator(groupLocator);
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's group key
      SUserGroup group = myUserGroupManager.findUserGroupByKey(groupLocator);
      if (group == null) {
        throw new NotFoundException("No group can be found by key '" + groupLocator + "'.");
      }
      return group;
    }

    String groupKey = locator.getSingleDimensionValue("key");
    if (groupKey != null) {
      SUserGroup group = myUserGroupManager.findUserGroupByKey(groupKey);
      if (group == null) {
        throw new NotFoundException("No group can be found by key '" + groupKey + "'.");
      }
      return group;
    }

    String groupName = locator.getSingleDimensionValue("name");
    if (groupName != null) {
      SUserGroup group = myUserGroupManager.findUserGroupByName(groupName);
      if (group == null) {
        throw new NotFoundException("No group can be found by name '" + groupName + "'.");
      }
      return group;
    }
    throw new NotFoundException("Group locator '" + groupLocator + "' is not supported.");
  }

  @NotNull
  public Collection<UserGroup> getAllGroups() {
    final Collection<SUserGroup> serverUserGroups = myUserGroupManager.getUserGroups();
    final Collection<UserGroup> result = new ArrayList<UserGroup>(serverUserGroups.size());
    for (SUserGroup group : serverUserGroups) {
      result.add(group);
    }
    return result;
  }
}
