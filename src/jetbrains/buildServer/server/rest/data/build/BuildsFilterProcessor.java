/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.build;

import com.intellij.openapi.diagnostic.Logger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.server.rest.data.util.FilterItemProcessor;
import jetbrains.buildServer.server.rest.data.util.ItemFilter;
import jetbrains.buildServer.server.rest.data.util.ItemFilterUtil;
import jetbrains.buildServer.server.rest.data.util.PagingItemFilter;
import jetbrains.buildServer.serverSide.BuildHistory;
import jetbrains.buildServer.serverSide.RunningBuildsManager;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.ItemProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 14.01.13
 */
public class BuildsFilterProcessor {
  final static Logger LOG = Logger.getInstance(BuildsFilterProcessor.class.getName());

  public static List<SFinishedBuild> getMatchingFinishedBuilds(@NotNull final BuildsFilter buildsFilter, @NotNull final BuildHistory buildHistory) {
    if (buildsFilter.getRunning() != null && buildsFilter.getRunning()){
      return Collections.emptyList();
    }

    final FilterItemProcessor<SFinishedBuild> buildsFilterItemProcessor =
      new FilterItemProcessor<>(new PagingItemFilter<>(new FinishedBuildsFilter(buildsFilter), buildsFilter.getStart(), buildsFilter.getCount(), null));
    if (buildsFilter.getBuildType() != null) {
      //noinspection ConstantConditions
      buildHistory.processEntries(buildsFilter.getBuildType().getBuildTypeId(),
                                    getUserForProcessEntries(buildsFilter),
                                    buildsFilter.getPersonal() == null || buildsFilter.getPersonal(),
                                    buildsFilter.getCanceled() == null || buildsFilter.getCanceled(),
                                    false,
                                    buildsFilterItemProcessor);
    } else {
      buildHistory.processEntries(buildsFilterItemProcessor);
    }
    final ArrayList<SFinishedBuild> result = buildsFilterItemProcessor.getResult();
    LOG.debug("Processed " + buildsFilterItemProcessor.getProcessedItemsCount() + " builds, " + result.size() + " selected.");
    return result;
  }

  public static List<SRunningBuild> getMatchingRunningBuilds(@NotNull final BuildsFilter buildsFilter,
                                                             @NotNull final RunningBuildsManager runningBuildsManager) {
    final FilterItemProcessor<SRunningBuild> buildsFilterItemProcessor =
      new FilterItemProcessor<>(new PagingItemFilter<>(ItemFilterUtil.ofPredicate(item -> buildsFilter.isIncluded(item)), buildsFilter.getStart(), buildsFilter.getCount(), null));
    processList(runningBuildsManager.getRunningBuilds(), buildsFilterItemProcessor);
    return buildsFilterItemProcessor.getResult();
  }

  @Nullable
  public static User getUserForProcessEntries(@NotNull final BuildsFilter buildsFilter) {
    if ((buildsFilter.getPersonal() == null || buildsFilter.getPersonal()) && buildsFilter.getUser() != null) {
      return buildsFilter.getUser();
    }
    return null;
  }

  public static <P> void processList(final List<P> entries, final ItemProcessor<P> processor) {
    for (P entry : entries) {
      if (!processor.processItem(entry)) {
        break;
      }
    }
  }

  //todo: just use AbstractFilter
  private static class FinishedBuildsFilter implements ItemFilter<SFinishedBuild> {
    private int processedItems;
    @NotNull private final BuildsFilter myBuildsFilter;

    public FinishedBuildsFilter(@NotNull final BuildsFilter buildsFilter) {
      processedItems = 0;
      myBuildsFilter = buildsFilter;
    }

    public boolean isIncluded(@NotNull final SFinishedBuild item) {
      ++processedItems;
      return myBuildsFilter.isIncluded(item);
    }

    public boolean shouldStop(@NotNull final SFinishedBuild item) {
      if (myBuildsFilter.getLookupLimit() != null && processedItems >= myBuildsFilter.getLookupLimit()) {
        return true;
      }
      //assume the builds are processed from most recent to older
      return myBuildsFilter.isExcludedBySince(item);
    }
  }

}
