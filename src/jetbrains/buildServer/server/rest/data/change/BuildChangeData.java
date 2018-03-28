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

package jetbrains.buildServer.server.rest.data.change;

import jetbrains.buildServer.serverSide.BuildPromotion;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 * Date: 27/03/2018
 */
public class BuildChangeData {
  @NotNull BuildPromotion myPreviousBuild;
  @NotNull BuildPromotion myNextBuild;

  public BuildChangeData(@NotNull final BuildPromotion previousBuild, @NotNull final BuildPromotion nextBuild) {
    myPreviousBuild = previousBuild;
    myNextBuild = nextBuild;
  }

  @NotNull
  public BuildPromotion getPreviousBuild() {
    return myPreviousBuild;
  }

  @NotNull
  public BuildPromotion getNextBuild() {
    return myNextBuild;
  }
}
