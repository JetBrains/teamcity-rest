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
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.RelatedEntity;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorDimensionDataType;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.audit.*;
import jetbrains.buildServer.serverSide.impl.audit.filters.BuildTypeFilter;
import jetbrains.buildServer.serverSide.impl.audit.filters.BuildTypeTemplateFilter;
import jetbrains.buildServer.serverSide.impl.audit.filters.HiddenActionTypesFilter;
import jetbrains.buildServer.serverSide.impl.audit.filters.ProjectFilter;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static jetbrains.buildServer.server.rest.data.FinderDataBinding.getItemHolder;
import static jetbrains.buildServer.server.rest.data.TypedFinderBuilder.Dimension;

@LocatorResource(value = LocatorName.AUDIT,
    extraDimensions = {PagerData.COUNT, PagerData.START, FinderImpl.DIMENSION_LOOKUP_LIMIT, AbstractFinder.DIMENSION_ITEM},
    description = "Represents a locator string for filtering AuditEvent entities." +
        "\nExamples:" +
        "\n* `count:1000` – find last 1000 audit events." +
        "\n* `user:(<userLocator>)` – find last 100 events by user found by userLocator.")
public class AuditEventFinder extends DelegatingFinder<AuditLogAction> {
  private static final Logger LOG = Logger.getInstance(AuditEventFinder.class.getName());

  @LocatorDimension("id") private static final Dimension<Long> ID = new Dimension<>("id");
  @LocatorDimension(value = "user", format = LocatorName.USER, notes = "Locator of user who caused the audit event.")
  private static final Dimension<List<SUser>> USER = new Dimension<>("user");
  @LocatorDimension(value = "systemAction", format = LocatorDimensionDataType.BOOLEAN)
  private static final Dimension<Boolean> SYSTEM_ACTION = new Dimension<>("systemAction");
  @LocatorDimension(value = "action", notes = "Use `$help` to get the full list of supported actions.")
  private static final Dimension<Set<ActionType>> ACTION = new Dimension<>("action");  //todo: consider supporting ActionTypeSet by supporting actions locator
  @LocatorDimension(value = "buildType", format = LocatorName.BUILD_TYPE, notes = "Related build type or template locator.")
  private static final Dimension<List<BuildTypeOrTemplate>> BUILD_TYPE = new Dimension<>("buildType");
  @LocatorDimension(value = "affectedProject", format = LocatorName.PROJECT, notes = "Related project locator.")
  private static final Dimension<List<SProject>> PROJECT = new Dimension<>("affectedProject");
  private static final Dimension<Set<String>> OBJECT_ID = new Dimension<>("entityInternalId");
  private static final Dimension<Set<ObjectType>> OBJECT_TYPE = new Dimension<>("entityType");
  private static final Dimension<Boolean> HIDDEN_ACTIONS = new Dimension<>("hidden");
  private static final Dimension<Long> COUNT = new Dimension<>(PagerData.COUNT);
  private static final Dimension<Long> START = new Dimension<>(PagerData.START);
  private static final Dimension<Long> LOOKUP_LIMIT = new Dimension<>(FinderImpl.DIMENSION_LOOKUP_LIMIT);
  //todo: add filter by event type (flexible/multiple include/exclude, patterns?)
  //todo: add filters for all the object types: builds, buildTypes, project, agent, test, problem, user (not difference with "performer"), userGroup, etc.
  //todo: allow to filter by main object and also additional ones?
  //todo: allow to filter by date/time
  //todo: add all possible from AuditLogFilter

  public static final Set<ActionType> HIDDEN_ACTION_TYPES = ActionType.getHiddenActionTypes();

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

      dimensionWithFinder(BUILD_TYPE, () -> myServiceLocator.getSingletonService(BuildTypeFinder.class), "buildType locator").description("related build type of the action, only single values are supported");
      dimensionProjects(PROJECT, myServiceLocator).description("related project of the action, only single values are supported");
//      dimension(OBJECT_TYPE, type(RelatedEntity::getObjectType).description("one of " + RelatedEntity.getSupportedObjectTypes())).description("entity of the action");
      dimensionSetOf(OBJECT_TYPE, "entity type: " + RelatedEntity.getSupportedObjectTypes(), RelatedEntity::getObjectType).hidden().description("entity type of the main action entity");
      dimensionSetOf(OBJECT_ID, "internal id", s -> s)
        .description("internal id of the main action entity").hidden().valueForDefaultFilter(AuditLogAction::getObjectId);

      dimensionLong(COUNT).description("number of items to return").withDefault(String.valueOf(Constants.getDefaultPageItemsCount()));
      dimensionLong(START).description("number of items to skip");
      dimensionLong(LOOKUP_LIMIT).description("maximum number of items to process when filtering").withDefault(String.valueOf(1000L));

      dimensionBoolean(SYSTEM_ACTION).description("only actions by system").withDefault("false").filter((value, item) -> FilterUtil.isIncludedByBooleanFilter(value, !item.isUserAction()));
      dimensionBoolean(HIDDEN_ACTIONS).description("only legacy actions").withDefault("false").hidden().filter((value, item) -> FilterUtil.isIncludedByBooleanFilter(value, HIDDEN_ACTION_TYPES.contains(item.getActionType())));

      //put this last as it's description is too long
      dimensionSetOf(ACTION, String.valueOf(getActionTypesValues()), s -> getValue(s, ActionType.class)).description("type of the action").valueForDefaultFilter(AuditLogAction::getActionType);

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

            Boolean systemActionFlag = getIfSingle(dimensions.lookup(SYSTEM_ACTION)); //todo: should only show system actions to users with due permissions?
            if (systemActionFlag != null && !systemActionFlag) {
              builder.setUserAction(!systemActionFlag);
              dimensions.get(SYSTEM_ACTION); //marking as used
            }
          }
        }

        Boolean hidden = getIfSingle(dimensions.lookup(HIDDEN_ACTIONS));
        if (hidden != null && !hidden) {
          dimensions.get(HIDDEN_ACTIONS);
          builder.addFilter(new HiddenActionTypesFilter());
        }

        Set<ActionType> actions = TypedFinderBuilder.getIntersected(dimensions.get(ACTION));
        if (actions != null) {
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

        Set<ObjectType> filterTypes = TypedFinderBuilder.getIntersected(dimensions.get(OBJECT_TYPE));
        if (filterTypes != null) {
          RelatedEntity.expandTypes(filterTypes);
          builder.addFilter(data -> filterTypes.contains(data.getObjectType()));
        }

        BuildTypeOrTemplate buildTypeOrTemplate = getIfSingle(getIfSingle(dimensions.lookup(BUILD_TYPE)));
        if (buildTypeOrTemplate != null) {
          dimensions.get(BUILD_TYPE); //mark as used
          if (buildTypeOrTemplate.isBuildType()) {
            //noinspection ConstantConditions
            builder.addFilter(new BuildTypeFilter(buildTypeOrTemplate.getBuildType()));
          } else {
            //noinspection ConstantConditions
            builder.addFilter(new BuildTypeTemplateFilter(buildTypeOrTemplate.getTemplate()));
          }
        }

        SProject project = getIfSingle(getIfSingle(dimensions.lookup(PROJECT)));
        if (project != null) {
          dimensions.get(PROJECT); //mark as used
          builder.addFilter(new ProjectFilter(project));
        }

        Long count = getIfSingle(dimensions.lookup(COUNT));
        int maxEntries = -1;
        Set<String> filteringDimensions = dimensions.getUnusedDimensions();
        filteringDimensions.remove(COUNT.name);
        filteringDimensions.remove(START.name);
        filteringDimensions.remove(LOOKUP_LIMIT.name);
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

    @NotNull
    private List<String> getActionTypesValues() {
      return Arrays.stream(ActionType.class.getEnumConstants()).filter(o -> !HIDDEN_ACTION_TYPES.contains(o)).map(source -> source.name().toLowerCase()).collect(Collectors.toList());
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

