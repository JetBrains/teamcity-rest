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

import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 15.11.2009
 */
//todo: optimize performance: use lazy calculation
public class RequestPathTransformator {
  private String myOriginalPath;
  private RequestPathTransformInfo myTransformInfo;
  private boolean myRestrictToStartOnly;
  private String myOriginalPrefix;
  private String myNewPrefix;
  private String myNewPath;

  public RequestPathTransformator(@NotNull final String originalPath,
                                  @NotNull final RequestPathTransformInfo transformInfo,
                                  boolean restrictToStartOnly) {
    myOriginalPath = originalPath;
    myTransformInfo = transformInfo;
    myRestrictToStartOnly = restrictToStartOnly;

    myOriginalPrefix = "";
    myNewPrefix = "";
    myNewPath = myOriginalPath;

    if (myRestrictToStartOnly) {
      init();
    } else {
      initInside();
    }
  }

  private void initInside() {
    String prefixToUse = getLargestMatchPrefix();
    if (prefixToUse.length() > 0) {
      myOriginalPrefix = prefixToUse;
      myNewPrefix = myTransformInfo.getNewPathPrefix();
      myNewPath = myOriginalPath.replaceFirst(prefixToUse, myNewPrefix);
    }
  }

  private void init() {
    String prefixToUse = getLargestMatchPrefix();
    if (prefixToUse.length() > 0) {
      myOriginalPrefix = prefixToUse;
      myNewPrefix = myTransformInfo.getNewPathPrefix();
      myNewPath = myNewPrefix + myOriginalPath.substring(prefixToUse.length());
    }
  }

  private String getLargestMatchPrefix() {
    String result = "";
    for (String supportedOriginalPrefix : myTransformInfo.getOriginalPathPrefixes()) {
      boolean matches;
      if (myRestrictToStartOnly) {
        matches = myOriginalPath.startsWith(supportedOriginalPrefix);
      } else {
        matches = myOriginalPath.contains(supportedOriginalPrefix);
      }
      if (matches && result.length() < supportedOriginalPrefix.length()) {
        result = supportedOriginalPrefix;
      }
    }
    return result;
  }

  public String getNewPath() {
    return myNewPath;
  }

  public String transformNewFormPathToOriginalForm(@NotNull final String newFormPath) {
    if (myRestrictToStartOnly) {
      if (!newFormPath.startsWith(myNewPrefix)) {
        throw new IllegalArgumentException("Path in new form: '" + newFormPath + "' does not start with new prefix: '" + myNewPrefix + "'");
      }
      return myOriginalPrefix + newFormPath.substring(myNewPrefix.length());
    } else {
      if (!newFormPath.contains(myNewPrefix)) {
        throw new IllegalArgumentException("Path in new form: '" + newFormPath + "' does not contain new prefix: '" + myNewPrefix + "'");
      }
      return newFormPath.replaceFirst(myNewPrefix, myOriginalPrefix);
    }
  }
}
