/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.finder.impl;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.finder.DelegatingFinder;
import jetbrains.buildServer.server.rest.data.finder.Finder;
import jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder;
import jetbrains.buildServer.server.rest.data.locator.Dimension;
import jetbrains.buildServer.server.rest.data.locator.StubDimension;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.StreamUtil;
import jetbrains.buildServer.serverSide.healthStatus.*;
import jetbrains.buildServer.serverSide.healthStatus.impl.HealthStatusProfileBuilder;
import jetbrains.buildServer.serverSide.healthStatus.impl.ScopeBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@JerseyContextSingleton
@Component("healthStatusItemFinder")
public class HealthItemFinder extends DelegatingFinder<HealthStatusItem> {
  @NotNull
  private static final Dimension MIN_SEVERITY = new StubDimension("minSeverity");
  @NotNull
  private static final Dimension CATEGORY = new StubDimension("healthCategory");
  @NotNull
  private static final Dimension REPORT_TYPE = new StubDimension("reportType");
  @NotNull
  private static final Dimension BUILD_TYPE = new StubDimension("buildType");
  @NotNull
  private static final Dimension PROJECT = new StubDimension("project");
  @NotNull
  private static final Dimension VCS_ROOT = new StubDimension("vcsRoot");
  @NotNull
  private static final Dimension GLOBAL = new StubDimension("global");
  @NotNull
  private final HealthStatusProvider myHealthStatusProvider;
  @NotNull
  private final HealthStatusReportLocator myHealthStatusReportLocator;
  @NotNull
  private final ServiceLocator myServiceLocator;
  @NotNull
  private final Finder<ItemCategory> myCategoryFinder;
  @NotNull
  private final Predicate<ItemCategory> myEmptyPredicate = item -> true;

  public HealthItemFinder(@NotNull final HealthStatusProvider healthStatusProvider,
                          @NotNull final HealthStatusReportLocator healthStatusReportLocator,
                          @NotNull final ServiceLocator serviceLocator) {
    myHealthStatusProvider = healthStatusProvider;
    myHealthStatusReportLocator = healthStatusReportLocator;
    myServiceLocator = serviceLocator;
    setDelegate(new Builder().build());
    myCategoryFinder = new CategoryFinderBuilder().build();
  }

  @NotNull
  @Override
  public HealthStatusItem getItem(@Nullable final String locatorText) {
    return super.getItem(locatorText);
  }

  @NotNull
  public ItemCategory getCategory(@Nullable final String locatorText) {
    return myCategoryFinder.getItem(locatorText);
  }

  @NotNull
  public PagedSearchResult<ItemCategory> getCategories(@Nullable final String locatorText) {
    return myCategoryFinder.getItems(locatorText);
  }

  private class CategoryFinderBuilder extends TypedFinderBuilder<ItemCategory> {
    CategoryFinderBuilder() {
      dimensionString(new StubDimension("id")).description("health category id").filter((s, item) -> s.equalsIgnoreCase(item.getId()));

      singleDimension(dimension -> getAllMatching(category -> dimension.equalsIgnoreCase(category.getId())));

      multipleConvertToItemHolder(DimensionCondition.ALWAYS, dimensions -> ItemHolder.of(getAllMatching(myEmptyPredicate)));
    }

    @NotNull
    private List<ItemCategory> getAllMatching(@NotNull final Predicate<ItemCategory> predicate) {
      return myHealthStatusReportLocator.getAvailableReports().stream()
                                        .flatMap(healthStatusReport -> healthStatusReport.getCategories().stream())
                                        .filter(predicate)
                                        .collect(Collectors.toList());
    }
  }

  private class Builder extends TypedFinderBuilder<HealthStatusItem> {
    Builder() {
      dimensionBoolean(GLOBAL).description("include global items").withDefault("false").valueForDefaultFilter(healthStatusItem -> Boolean.TRUE);
      dimensionBuildTypes(BUILD_TYPE, myServiceLocator);
      dimensionProjects(PROJECT, myServiceLocator);
      dimensionVcsRoots(VCS_ROOT, myServiceLocator);
      dimensionString(CATEGORY).description("health category id").filter((value, item) -> value.equalsIgnoreCase(item.getCategory().getId()));
      dimensionString(REPORT_TYPE).description("report type");
      dimensionEnum(MIN_SEVERITY, ItemSeverity.class).description("minimal severity level").valueForDefaultFilter(HealthStatusItem::getSeverity);

      dimensionCount();
      dimensionStart();
      dimensionLookupLimit();


      multipleConvertToItemHolder(DimensionCondition.ALWAYS, dimensions -> {
        final ScopeBuilder scopeBuilder = new ScopeBuilder();
        StreamUtil.forEachNullableFlattened(dimensions.get(PROJECT), scopeBuilder::addProject);
        StreamUtil.forEachNullableFlattened(dimensions.get(BUILD_TYPE), (BuildTypeOrTemplate buildTypeOrTemplate) -> {
          if (buildTypeOrTemplate.getBuildType() != null) {
            scopeBuilder.addBuildType(buildTypeOrTemplate.getBuildType());
          }
          if (buildTypeOrTemplate.getTemplate() != null) {
            scopeBuilder.addBuildTypeTemplate(buildTypeOrTemplate.getTemplate());
          }
        });
        StreamUtil.forEachNullableFlattened(dimensions.get(VCS_ROOT), scopeBuilder::addVcsRoot);
        final List<ItemSeverity> itemSeverities = dimensions.get(MIN_SEVERITY);
        if (itemSeverities != null) {
          scopeBuilder.setMinSeverity(ItemSeverity.min(itemSeverities));
        }
        final List<Boolean> global = dimensions.get(GLOBAL);
        scopeBuilder.setGlobalItems(global != null && global.contains(true));
        @Nullable final HealthStatusProfile profile;
        final List<String> reportTypes = dimensions.get(REPORT_TYPE);
        if (!CollectionUtils.isEmpty(reportTypes)) {
          final HealthStatusProfileBuilder builder = new HealthStatusProfileBuilder();
          reportTypes.forEach(builder::addReportType);
          profile = builder.build();
        } else {
          profile = null;
        }
        return ItemHolder.of(myHealthStatusProvider.collectItemsSynchronously(scopeBuilder.build(), profile));
      });
    }
  }
}
