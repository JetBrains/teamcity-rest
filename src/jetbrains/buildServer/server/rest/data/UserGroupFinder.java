/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.server.rest.data;

import java.util.ArrayList;
import java.util.Collection;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.groups.UserGroupManager;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.13
 */
@LocatorResource(value = LocatorName.USER_GROUP,
    extraDimensions = AbstractFinder.DIMENSION_ITEM,
    baseEntity = "Group",
    examples = {
        "'name:MyGroupName' – find group with name `MyGroupName`.",
        "'key:MyGroupKey' – find group with key `MyGroupKey`."
    }
)
public class UserGroupFinder {
  @NotNull private final UserGroupManager myUserGroupManager;

  @LocatorDimension("key") private static final String KEY = "key";
  @LocatorDimension("name") private static final String NAME = "name";

  public UserGroupFinder(@NotNull UserGroupManager userGroupManager) throws LocatorProcessException {
    myUserGroupManager = userGroupManager;
  }

  @NotNull
  public static String getLocator(@NotNull final UserGroup userGroup) {
    return "key:" + userGroup.getKey();
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

    String groupKey = locator.getSingleDimensionValue(KEY);
    if (groupKey != null) {
      SUserGroup group = myUserGroupManager.findUserGroupByKey(groupKey);
      if (group == null) {
        throw new NotFoundException("No group can be found by key '" + groupKey + "'.");
      }
      return group;
    }

    String groupName = locator.getSingleDimensionValue(NAME);
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
    return new ArrayList<>(myUserGroupManager.getUserGroups());
  }
}
