/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 14.01.13
 */
public interface BuildsFilter {
  @Nullable
  Long getStart();

  void setStart(@Nullable Long start);

  @Nullable
  Integer getCount();

  void setCount(@Nullable Integer count);

  @Nullable
  Boolean getPersonal();

  @Nullable
  Boolean getCanceled();

  @Nullable
  Boolean getRunning();

  @Nullable
  SUser getUser();

  @Nullable
  SBuildType getBuildType();

  boolean isIncluded(@NotNull SBuild build);

  @Nullable
  Long getLookupLimit();

  boolean isExcludedBySince(SBuild build);
}