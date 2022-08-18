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

package jetbrains.buildServer.server.rest.util;

import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes build position, three options:
 * - top of the queue
 * - bottom of the queue
 * - position which is closest to the top of the BuildQueue and satisfies the condition "comes after all of given item ids".
 */
public class BuildQueuePositionDescriptor {
  /**
   * Position at the top of the build queue.
   */
  public static final BuildQueuePositionDescriptor FIRST = new BuildQueuePositionDescriptor(-1);

  /**
   * Position at the bottom of the build queue.
   */
  public static final BuildQueuePositionDescriptor LAST = new BuildQueuePositionDescriptor(Long.MAX_VALUE - 1);
  private final Set<Long> myBuildIds;

  BuildQueuePositionDescriptor(@NotNull long... buildIdsWhichMustComeFirst) {
    myBuildIds = new HashSet<>();
    for(long buildId: buildIdsWhichMustComeFirst) {
      myBuildIds.add(buildId);
    }
  }

  private BuildQueuePositionDescriptor(@NotNull Set<Long> buildIdsWhichMustComeFirst) {
    myBuildIds = buildIdsWhichMustComeFirst;
  }

  @NotNull
  public Set<Long> getBuildIds() {
    return myBuildIds;
  }

  /**
   * @return null if unable to parse the given string, descriptor otherwise.
   */
  @Nullable
  public static BuildQueuePositionDescriptor parse(@NotNull String queuePosition) {
    if ("first".equals(queuePosition) || "1".equals(queuePosition)) {
      return FIRST;
    }
    if ("last".equals(queuePosition)) {
      return LAST;
    }

    if(!queuePosition.startsWith("after:")) {
      return null;
    }

    if("after:".length() == queuePosition.length()) {
      return null;
    }

    Set<Long> parsedIds = new HashSet<>();
    for(String id : queuePosition.substring("after:".length()).split(",")) {
      parsedIds.add(Long.parseLong(id));
    }

    return new BuildQueuePositionDescriptor(parsedIds);
  }
}