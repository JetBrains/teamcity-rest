/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

import java.util.List;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.StreamUtil;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusProvider;
import jetbrains.buildServer.serverSide.healthStatus.ItemSeverity;
import jetbrains.buildServer.serverSide.healthStatus.impl.ScopeBuilder;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HealthItemFinder extends DelegatingFinder<jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem> {
  @NotNull
  private static final TypedFinderBuilder.Dimension<ItemSeverity> MIN_SEVERITY = new TypedFinderBuilder.Dimension<>("minSeverity");
  @NotNull
  private static final TypedFinderBuilder.Dimension<List<BuildTypeOrTemplate>> BUILD_TYPE = new TypedFinderBuilder.Dimension<>("buildType");
  @NotNull
  private static final TypedFinderBuilder.Dimension<List<SProject>> PROJECT = new TypedFinderBuilder.Dimension<>("project");
  @NotNull
  private static final TypedFinderBuilder.Dimension<List<SVcsRoot>> VCS_ROOT = new TypedFinderBuilder.Dimension<>("vcsRoot");
  @NotNull
  private static final TypedFinderBuilder.Dimension<Boolean> GLOBAL = new TypedFinderBuilder.Dimension<>("global");
  @NotNull
  private final HealthStatusProvider myHealthStatusProvider;
  @NotNull
  private final ServiceLocator myServiceLocator;

  public HealthItemFinder(@NotNull final HealthStatusProvider healthStatusProvider,
                          @NotNull final ServiceLocator serviceLocator) {
    myHealthStatusProvider = healthStatusProvider;
    myServiceLocator = serviceLocator;
    setDelegate(new Builder().build());
  }

  @NotNull
  @Override
  public PagedSearchResult<HealthStatusItem> getItems(@Nullable final String locatorText) {
    return super.getItems(locatorText);
  }

  @NotNull
  @Override
  public HealthStatusItem getItem(@Nullable final String locatorText) {
    return super.getItem(locatorText);
  }

  private class Builder extends TypedFinderBuilder<HealthStatusItem> {
    Builder() {
      dimensionBoolean(GLOBAL).description("include global items").withDefault("false").valueForDefaultFilter(healthStatusItem -> Boolean.TRUE);
      dimensionBuildTypes(BUILD_TYPE, myServiceLocator);
      dimensionProjects(PROJECT, myServiceLocator);
      dimensionVcsRoots(VCS_ROOT, myServiceLocator);
      dimensionEnum(MIN_SEVERITY, ItemSeverity.class).description("minimal severity level").valueForDefaultFilter(HealthStatusItem::getSeverity);

      dimensionCount();
      dimensionStart();
      dimensionLookupLimit();


      multipleConvertToItemHolder(DimensionCondition.ALWAYS, dimensions -> {
        final ScopeBuilder scopeBuilder = new ScopeBuilder();
        StreamUtil.forEachNullableFlattened(dimensions.get(PROJECT), scopeBuilder::addProject);
        StreamUtil.forEachNullableFlattened(dimensions.get(BUILD_TYPE), buildTypeOrTemplate -> {
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
        if (global != null && global.contains(true)) {
          scopeBuilder.setGlobalItems(true);
        } else {
          scopeBuilder.setGlobalItems(false);
        }
        return new FinderDataBinding.CollectionItemHolder<>(myHealthStatusProvider.collectItemsSynchronously(scopeBuilder.build(), null));
      });
    }
  }
}
