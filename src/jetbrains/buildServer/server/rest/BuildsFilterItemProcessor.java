/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest;

import java.util.ArrayList;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.util.ItemProcessor;

/**
 * @author Yegor.Yarko
 *         Date: 16.08.2009
 */
public class BuildsFilterItemProcessor implements ItemProcessor<SFinishedBuild> {
  long myCurrentIndex = 0;
  private final BuildsFilterSettings myBuildsFilterSettings;
  private final ArrayList<SFinishedBuild> myList = new ArrayList<SFinishedBuild>();

  public BuildsFilterItemProcessor(final BuildsFilterSettings buildsFilterSettings) {
    myBuildsFilterSettings = buildsFilterSettings;
  }

  public boolean processItem(final SFinishedBuild item) {
    if (!myBuildsFilterSettings.isIncluded(item)) {
      return true;
    }
    if (myBuildsFilterSettings.isIncludedByRange(myCurrentIndex)) {
      myList.add(item);
    }
    ++myCurrentIndex;
    return myBuildsFilterSettings.isBelowUpperRangeLimit(myCurrentIndex);
  }

  public ArrayList<SFinishedBuild> getResult() {
    return myList;
  }
}
