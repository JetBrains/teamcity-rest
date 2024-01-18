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

package jetbrains.buildServer.server.rest.data.util;

import java.util.Date;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This wrapper allows to sort build promotions.
 * <br/>
 * The issue it is designed to solve is the following:
 * Desired comparison algorithm relies on promotion being static and not changing while being sorted. Unfortunately,
 * that does not hold in many situations, e.g. when sorting list of dependencies of the queued composite build.
 * To overcome this, we remember all the information used in the comparison in the wrapper, making it effectively immutable.
 * <br/>
 * <ol>
 * Comparison rules:
 * <li> queued < running < finished < no associated build
 * <li> both queued -> compare by itemId
 * <li> both running/finished -> compare by start date, then by buildId
 * <li> both no associated build -> by promotionId
 * </ol>
 */
public class ComparableBuildPromotionWrapper implements Comparable<ComparableBuildPromotionWrapper> {
  private enum State {
    QUEUED, RUNNING, FINISHED, NO_BUILD;
  }

  private final BuildPromotion myPromotion;
  private final State myState;
  private final String myQueuedItemId;
  private final Date myStartDate;
  private final Long myBuildId;

  private ComparableBuildPromotionWrapper(@NotNull BuildPromotion promotion,
                                          @NotNull State state,
                                          @Nullable String queuedItemId,
                                          @Nullable Date startDate,
                                          @Nullable Long buildId) {
    myState = state;
    myQueuedItemId = queuedItemId;
    myStartDate = startDate;
    myBuildId = buildId;
    myPromotion = promotion;
  }

  public static ComparableBuildPromotionWrapper fromPromotion(@NotNull BuildPromotion promotion) {
    SQueuedBuild queued = promotion.getQueuedBuild();
    if (queued != null) {
      return new ComparableBuildPromotionWrapper(promotion, State.QUEUED, queued.getItemId(), null, null);
    }

    SBuild associatedBuild = promotion.getAssociatedBuild();
    if (associatedBuild == null) {
      return new ComparableBuildPromotionWrapper(promotion, State.NO_BUILD, null, null, null);
    }

    return new ComparableBuildPromotionWrapper(
      promotion,
      associatedBuild.isFinished() ? State.FINISHED : State.RUNNING,
      null,
      associatedBuild.getStartDate(),
      associatedBuild.getBuildId()
    );
  }

  public BuildPromotion getPromotion() {
    return myPromotion;
  }

  public State getState() {
    return myState;
  }

  public String getQueuedItemId() {
    return myQueuedItemId;
  }

  public Date getStartDate() {
    return myStartDate;
  }

  public Long getBuildId() {
    return myBuildId;
  }

  @Override
  public int compareTo(@NotNull ComparableBuildPromotionWrapper o) {
    int stateComparison = myState.compareTo(o.getState());
    if (stateComparison != 0) {
      return stateComparison;
    }

    // Same state
    switch (myState) {
      case QUEUED:
        return -myQueuedItemId.compareTo(o.getQueuedItemId());
      case RUNNING:
      case FINISHED:
        int startDateComparison = myStartDate.compareTo(o.getStartDate());
        if (startDateComparison == 0) {
          return -Long.valueOf(myBuildId).compareTo(o.getBuildId());
        }
        return -startDateComparison;
      case NO_BUILD:
        return -Long.compare(myPromotion.getId(), o.getPromotion().getId());
    }

    return 0;
  }

  @Override
  public String toString() {
    String params = "state=" + myState;
    switch (myState) {
      case QUEUED:
        params += ",itemId=" + myQueuedItemId;
        break;
      case FINISHED:
      case RUNNING:
        params += ",started=" + myStartDate + ",buildId=" + myBuildId;
        break;
      case NO_BUILD:
        params += ",promotionId=" + myPromotion.getId();
        break;
    }

    return "ComparablePromotion{" + params + "}";
  }
}
