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

package jetbrains.buildServer.server.rest.util;

import java.util.List;
import java.util.Set;
import jetbrains.buildServer.serverSide.BuildQueue;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class BuildQueuePostitionModifier {
  @NotNull
  private final BuildQueue myBuildQueue;

  public BuildQueuePostitionModifier(@NotNull BuildQueue buildQueue) {
    myBuildQueue = buildQueue;
  }

  /**
   * Move the given build in the build queue in such a way that it will be placed at the first available position according to the given position descriptor.
   */
  public void moveBuild(@NotNull SQueuedBuild buildToMove, @NotNull BuildQueuePositionDescriptor newPosition) {
    // Special cases
    if(BuildQueuePositionDescriptor.FIRST.equals(newPosition)) {
      myBuildQueue.moveTop(buildToMove.getItemId());
      return;
    }

    if(BuildQueuePositionDescriptor.LAST.equals(newPosition)) {
      myBuildQueue.moveBottom(buildToMove.getItemId());
      return;
    }

    String[] newOrder = calculateNewOrder(buildToMove, newPosition.getBuildIds());
    if(newOrder != null) {
      // TC API issue: we've got no way to know if the new order was applied except for loonking manually into logs.
      myBuildQueue.applyOrder(newOrder);
    }
  }

  @Nullable
  private String[] calculateNewOrder(@NotNull SQueuedBuild ourBuildToMove, @NotNull Set<Long> buildsWhichMustBeBefore) {
    List<SQueuedBuild> currentOrder = myBuildQueue.getItems();

    int lastBuildWhichMustBeBeforeOurs = -1;
    int ourBuildPos = -1;
    for(int curPos = 0; curPos < currentOrder.size(); curPos++) {
      SQueuedBuild qb = currentOrder.get(curPos);
      if(qb.equals(ourBuildToMove)) {
        ourBuildPos = curPos;
      }

      if(buildsWhichMustBeBefore.contains(qb.getBuildPromotion().getId())) {
        lastBuildWhichMustBeBeforeOurs = curPos;
      }
    }

    if(ourBuildPos == -1) {
      // Build wich we want to move is not in the queue anymore, so there is nothing to reorder
      return null;
    }

    // We need to shift down or up by one all builds between new target position and current position.
    // To do that we don't actually need to reorder the whole queue, reordering the part in that range should be enough to avoid weird jumps.
    // However, putting parts of build chains interliving with other build chains will still cause unpredictable jumps from user perspective.
    if(lastBuildWhichMustBeBeforeOurs < ourBuildPos) {
      // We are moving our build up in the queue, all the given buildsWhichMustBeBefore are already before, so we are just looking for a closer position.
      int targetPos = lastBuildWhichMustBeBeforeOurs + 1;

      // E == lastBuildWhichMustBeBeforeOurs
      // X == ourBuildToMove
      // [... E B1 .. Bm X ...] -> [... E X B1 .. Bm ...]
      // In this case our build comes first in the reordered part of the queue.
      String[] newOrder = new String[ourBuildPos - targetPos + 1];
      int destPos = 1;
      for(int i = 0; i < ourBuildPos - targetPos; i++) {
        newOrder[destPos + i] = currentOrder.get(targetPos + i).getItemId();
      }

      newOrder[0] = ourBuildToMove.getItemId();

      return newOrder;
    } else {
      // We are moving our build down in the queue as some of buildsWhichMustBeBefore are after our build.
      // E == lastBuildWhichMustBeBeforeOurs
      // X == ourBuildToMove
      // [... X B1 .. Bn E ...] -> [...B1 .. Bn E X...]
      // Shift up by one all builds between current position and new target position.
      // In this case our build comes last in the reordered part of the queue.
      String[] newOrder = new String[lastBuildWhichMustBeBeforeOurs - ourBuildPos + 1];
      for(int i = 0; i < lastBuildWhichMustBeBeforeOurs - ourBuildPos; i++) {
        newOrder[i] = currentOrder.get(ourBuildPos + i + 1).getItemId();
      }

      newOrder[newOrder.length - 1] = ourBuildToMove.getItemId();

      return newOrder;
    }
  }
}