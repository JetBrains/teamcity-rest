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

import java.util.Date;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.08.2009
 */
public class RangeLimit {
  @Nullable private SBuild myBuild;
  @NotNull private Date myDate;

  public RangeLimit(@NotNull final SBuild build) {
    myBuild = build;
  }

  public RangeLimit(@NotNull final Date date) {
    myDate = date;
  }

  @Nullable
  public SBuild getBuild() {
    return myBuild;
  }

  @NotNull
  public Date getDate() {
    if (myBuild != null) {
      return myBuild.getStartDate();
    }
    return myDate;
  }

  public boolean before(@NotNull SBuild build){
    if (myBuild != null) {
      return myBuild.getBuildId() < build.getBuildId();
    }
    return myDate.before(build.getStartDate());
  }

  @Override
  public String toString() {
    return "(date: " + myDate + (myBuild == null ? "" : LogUtil.describe(myBuild)) + ")";
  }
}
