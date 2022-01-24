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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 14.01.13
 */
public class BuildsFilterWithBuildExcludes implements BuildsFilter {
  @NotNull private final BuildsFilter myBuildsFilter;
  @NotNull private final Set<Long> myExcludedBuilds;

  @Nullable protected Integer myCount;

  public BuildsFilterWithBuildExcludes(@NotNull final BuildsFilter buildsFilter, @NotNull final Collection<SBuild> excludedBuilds) {
    myBuildsFilter = buildsFilter;
    myCount = buildsFilter.getCount();
    myExcludedBuilds = new HashSet<Long>(excludedBuilds.size());
    for (SBuild build : excludedBuilds) {
      myExcludedBuilds.add(build.getBuildId());
    }
  }

  @Nullable
  public Long getStart() {
    return myBuildsFilter.getStart();
  }

  public void setStart(@Nullable final Long start) {
    myBuildsFilter.setStart(start);
  }

  @Nullable
  public Integer getCount() {
    return myCount;
  }

  public void setCount(@Nullable final Integer count) {
    myCount = count;
  }

  @Nullable
  public Boolean getPersonal() {
    return myBuildsFilter.getPersonal();
  }

  @Nullable
  public Boolean getCanceled() {
    return myBuildsFilter.getCanceled();
  }

  @Nullable
  public Boolean getRunning() {
    return myBuildsFilter.getRunning();
  }

  @Nullable
  public SUser getUser() {
    return myBuildsFilter.getUser();
  }

  @Nullable
  public SBuildType getBuildType() {
    return myBuildsFilter.getBuildType();
  }

  public boolean isIncluded(@NotNull final SBuild build) {
    if (myExcludedBuilds.contains(build.getBuildId())){
      return false;
    }
    return myBuildsFilter.isIncluded(build);
  }

  @Nullable
  public Long getLookupLimit() {
    return myBuildsFilter.getLookupLimit();
  }

  public boolean isExcludedBySince(final SBuild build) {
    return myBuildsFilter.isExcludedBySince(build);
  }

  @Override
  public String toString() {
    final StringBuilder result = new StringBuilder();
    result.append("Builds filter with exclude (");
    result.append("builds filter:").append(myBuildsFilter).append(", ");
    result.append("exclude builds:").append(myExcludedBuilds).append(", ");
    if (myCount!= null) result.append("count:").append(myCount);
    result.append(")");
    return result.toString();
  }
}
