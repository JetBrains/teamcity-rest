/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.problem;

import java.util.Comparator;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.TestGroupName;

public class SortTestRunsByNewComparator implements Comparator<STestRun> {
  @Override
  public int compare(STestRun o1, STestRun o2) {
    // see also STestRun.NEW_FIRST_NAME_COMPARATOR

    // New failure goes first
    boolean isNew1 = o1.isNewFailure();
    boolean isNew2 = o2.isNewFailure();
    if (isNew1 && !isNew2) {
      return -1;
    }
    if (!isNew1 && isNew2) {
      return 1;
    }

    final TestGroupName grp1 = o1.getTest().getName().getGroupName();
    final TestGroupName grp2 = o2.getTest().getName().getGroupName();
    final int grpCompare = grp1.compareTo(grp2);
    if (grpCompare != 0) return grpCompare;

    final String name1 = o1.getTest().getName().getAsString();
    final String name2 = o2.getTest().getName().getAsString();
    final int nameCompare = name1.compareTo(name2);
    if (nameCompare != 0) return nameCompare;

    // Failure goes first
    boolean isFailed1 = o1.getStatus().isFailed();
    boolean isFailed2 = o2.getStatus().isFailed();
    if (isFailed1 && !isFailed2) {
      return -1;
    }
    if (!isFailed1 && isFailed2) {
      return 1;
    }

    // That's what STestRun.NEW_FIRST_NAME_COMPARATOR does not compare
    // We need that to be consistent with equals.
    SBuild build1 = o1.getBuild();
    SBuild build2 = o2.getBuild();

    int datesComparison = build1.getServerStartDate().compareTo(build2.getServerStartDate());
    if (datesComparison != 0) return datesComparison;

    if (build1.getBuildId() != build2.getBuildId()) {
      return Long.compare(build1.getBuildId(), build2.getBuildId());
    }

    return Integer.compare(o1.getOrderId(), o2.getOrderId());
  }
}
