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

import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;

public class SplitBuildsFeatureUtil {
  public static final String PARALLEL_TESTS_FEATURE_TYPE = "parallelTests";
  public static final String LINK_TO_PARENT_PARAM_NAME = "teamcity.internal.original.link.id";
  public static final String LINK_TO_PARENT_BT_PREFIX = "bt:";

  public static boolean isVirtualBuild(@NotNull BuildPromotion build) {
    SBuildType bt = build.getBuildType();
    return bt != null && bt.getProject().isVirtual();
  }

  public static boolean isVirtualConfiguration(@NotNull SBuildType bt) {
    return bt.getProject().isVirtual();
  }

  public static boolean isParallelizedBuild(@NotNull BuildPromotion buildPromotion) {
    if(!buildPromotion.isCompositeBuild()) {
      return false;
    }

    return !((BuildPromotionEx)buildPromotion).getBuildSettings().getBuildFeaturesOfType(PARALLEL_TESTS_FEATURE_TYPE).isEmpty();
  }

  @NotNull
  private static IllegalArgumentException getMissingMatrixBuildFeatureException() {
    return new IllegalArgumentException("Matrix build feature is not enabled for this build.");
  }
}