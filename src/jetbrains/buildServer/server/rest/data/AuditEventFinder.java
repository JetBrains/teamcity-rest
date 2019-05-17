/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.serverSide.audit.ActionType;
import jetbrains.buildServer.serverSide.audit.AuditLogAction;
import jetbrains.buildServer.serverSide.audit.AuditLogBuilder;
import jetbrains.buildServer.serverSide.audit.AuditLogProvider;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.server.rest.data.FinderDataBinding.getItemHolder;
import static jetbrains.buildServer.server.rest.data.TypedFinderBuilder.Dimension;

public class AuditEventFinder extends DelegatingFinder<AuditLogAction> {
  private static final Logger LOG = Logger.getInstance(AuditEventFinder.class.getName());

  private static final Dimension<Long> ID = new Dimension<>("id");
  private static final Dimension<List<SUser>> USER = new Dimension<>("user");
  private static final Dimension<Boolean> SYSTEM_ACTION = new Dimension<>("systemAction");
  private static final Dimension<Set<ActionType>> ACTION = new Dimension<>("action");
  private static final Dimension<Set<String>> OBJECT_ID = new Dimension<>("internalId");
  private static final Dimension<Long> COUNT = new Dimension<>(PagerData.COUNT);
  private static final Dimension<Long> START = new Dimension<>(PagerData.START);
  private static final Dimension<Long> LOOKUP_LIMIT = new Dimension<>(FinderImpl.DIMENSION_LOOKUP_LIMIT);

  private AuditLogProvider myAuditLogProvider;
  @NotNull private final ServiceLocator myServiceLocator;

  public AuditEventFinder(@NotNull final AuditLogProvider auditLogProvider, @NotNull final ServiceLocator serviceLocator) {
    myAuditLogProvider = auditLogProvider;
    myServiceLocator = serviceLocator;
    setDelegate(new Builder().build());
  }

  public static String getLocatorById(@NotNull final Long id) {
    return Locator.getStringLocator(ID.name, String.valueOf(id));
  }

  @NotNull
  public static String getLocator(@NotNull final AuditLogAction item) {
    return getLocatorById(item.getComment().getCommentId());
  }

  private class Builder extends TypedFinderBuilder<AuditLogAction> {
    Builder() {
      singleDimension(dimension -> Collections.singletonList(getById(Long.parseLong(dimension), myAuditLogProvider)));

      dimensionLong(ID).description("action id")
                       .filter((value, item) -> value.equals(item.getComment().getCommentId()))
                       .toItems(dimension -> Collections.singletonList(getById(dimension, myAuditLogProvider)));

      dimensionUsers(USER, myServiceLocator).description("user who performed the action")
                                            .filter((value, item) -> {
                                              User user = item.getUser();
                                              return user != null && value.stream().anyMatch(u -> u.getId() == user.getId());
                                            });
      dimensionEnums(ACTION, ActionType.class).description("type of the action").valueForDefaultFilter(AuditLogAction::getActionType);
      dimensionSetOf(OBJECT_ID, "internal id", s -> s)
        .description("internal id of the main action entity").hidden().valueForDefaultFilter(AuditLogAction::getObjectId);

      dimensionLong(COUNT).description("number of items to return").withDefault(String.valueOf(Constants.getDefaultPageItemsCount()));
      dimensionLong(START).description("number of items to skip");
      dimensionLong(LOOKUP_LIMIT).description("maximum number of items to process when filtering").withDefault(String.valueOf(1000L));

      dimensionBoolean(SYSTEM_ACTION).description("only actions by system").withDefault("false").filter((value, item) -> FilterUtil.isIncludedByBooleanFilter(value, !item.isUserAction()));

      multipleConvertToItemHolder(DimensionCondition.ALWAYS, dimensions -> {
        AuditLogBuilder builder = myAuditLogProvider.getBuilder();

        List<List<SUser>> userLists = dimensions.get(USER);
        if (userLists != null) {
          SUser user = getIfSingle(getIfSingle(userLists));
          if (user != null) {
            builder.setUserId(user.getId());
          } else {
            List<Set<Long>> userSets = userLists.stream().map(users -> users.stream().map(User::getId).collect(Collectors.toSet())).collect(Collectors.toList());
            if (!userSets.isEmpty()) {
              builder.addFilter(data -> userSets.stream().anyMatch(ids -> ids.contains(data.getUserId())));
            }

            Boolean systemActionFlag = getIfSingle(dimensions.lookup(SYSTEM_ACTION)); //tood: should only show system actions to users with due permissions?
            if (systemActionFlag != null && !systemActionFlag) {
              builder.setUserAction(!systemActionFlag);
              dimensions.get(SYSTEM_ACTION); //marking as used
            }
          }
        }

        Set<ActionType> actions = TypedFinderBuilder.getIntersected(dimensions.get(ACTION));
        if (actions != null) {
          dimensions.get(ACTION); //mark as used
          builder.setActionTypes(CollectionsUtil.toArray(actions, ActionType.class));
        }

        Set<String> listOfObjectIds = TypedFinderBuilder.getIntersected(dimensions.get(OBJECT_ID));
        if (listOfObjectIds != null) {
          String single = getIfSingle(listOfObjectIds);
          if (single != null){
            builder.setObjectId(single);
          } else {
            builder.setObjectIds(listOfObjectIds);
          }
        }

        Long count = getIfSingle(dimensions.lookup(COUNT));
        int maxEntries = -1;
        Set<String> filteringDimensions = dimensions.getUnusedDimensions();
        filteringDimensions.remove(COUNT.name);
        filteringDimensions.remove(START.name);
        filteringDimensions.remove(LOOKUP_LIMIT.name);
        filteringDimensions.remove(USER.name);
        filteringDimensions.remove(ACTION.name);
        filteringDimensions.remove(OBJECT_ID.name);
        if (filteringDimensions.isEmpty() && count != null) {
          maxEntries = count.intValue();
          Long start = getIfSingle(dimensions.lookup(START));
          if (start != null) maxEntries += start;
        }

        Long lookupLimit = getIfSingle(dimensions.lookup(LOOKUP_LIMIT));
        if (lookupLimit != null) {
          if (maxEntries != -1) {
            maxEntries = Math.min(maxEntries, lookupLimit.intValue());
          } else {
            maxEntries = lookupLimit.intValue();
          }
        }

        if (maxEntries != -1) maxEntries++;  //adding 1 to make sure we hit the limitation and report it duly to the client via nextHref

        return getItemHolder(builder.getLogActions(maxEntries)); //setting maxEntries can produce unexpected results, so should be reworked. Ideally, should pass processor into audit retrieving logic
      });

      locatorProvider(AuditEventFinder::getLocator);
//      containerSetProvider(() -> new HashSet<AuditLogAction>());
    }
  }

  @Nullable
  private static <T> T getIfSingle(@Nullable final Collection<T> items) {
    if (items == null || items.size() != 1) return null;
    return items.iterator().next();
  }

  @NotNull
  private static AuditLogAction getById(@NotNull final Long id, @NotNull final AuditLogProvider auditLogProvider) {
    AuditLogBuilder builder = auditLogProvider.getBuilder();
    builder.setCommentId(id);
    AuditLogAction result = builder.findLastAction();
    if (result == null) {
      throw new NotFoundException("No audit action can be found for id '" + id + "'.");
    }
    return result;
  }
}

