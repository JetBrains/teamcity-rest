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

package jetbrains.buildServer.server.rest.data.build;

import java.util.Map;
import java.util.stream.Collectors;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.parameters.impl.MapParametersProviderImpl;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.BaseBuild;
import org.jetbrains.annotations.NotNull;

public class BuildParametersUtil {
  @NotNull
  public static ParametersProvider getResultingParameters(@NotNull BuildPromotion buildPromotion) {
    SBuild build = buildPromotion.getAssociatedBuild();
    if(build == null || !(build instanceof BaseBuild)) {
      return getParametersKnownToServer(buildPromotion);
    }

    Map<String, String> parameters = ((BaseBuild)build).getBuildFinishParameters();
    if (parameters == null) {
      parameters = ((BaseBuild)build).getBuildStartParameters();
    }

    if (parameters != null) {
      return new MapParametersProviderImpl(parameters);
    }

    return getParametersKnownToServer(buildPromotion);
  }

  @NotNull
  public static ParametersProvider getStartParameters(@NotNull BuildPromotion buildPromotion) {
    SBuild build = buildPromotion.getAssociatedBuild();
    if (build == null || !(build instanceof BaseBuild)) {
      return getParametersKnownToServer(buildPromotion);
    }

    Map<String, String> parameters = ((BaseBuild)build).getBuildStartParameters();
    if (parameters != null) {
      return new MapParametersProviderImpl(parameters);
    }

    return getParametersKnownToServer(buildPromotion);
  }

  /**
   * Retrieves parameters map which should be safe to return to the end user. Does not include
   * any parameters from plugins, nor parameters resolved on the agent. All secret parameters are masked.
   * This is useful for various non-finished builds, such as queued, failed to start before, etc.
   */
  @NotNull
  private static ParametersProvider getParametersKnownToServer(@NotNull BuildPromotion promotion) {
    return new MapParametersProviderImpl(
      ((BuildPromotionEx) promotion).getParametersCollection().stream()
               .collect(Collectors.toMap(Parameter::getName, Parameter::getValue))
    );
  }
}
