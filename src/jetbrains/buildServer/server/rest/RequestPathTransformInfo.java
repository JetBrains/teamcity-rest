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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 15.11.2009
 */
public class RequestPathTransformInfo {
  private List<String> myOriginalPathPrefixes = Collections.emptyList();
  private String myNewPathPrefix = "";

  public RequestPathTransformInfo() {
  }

  public void setOriginalPathPrefixes(@NotNull final List<String> originalPathPrefixes) {
    myOriginalPathPrefixes = originalPathPrefixes;
    Collections.sort(myOriginalPathPrefixes, new Comparator<String>() {

      public int compare(final String o1, final String o2) {
        return o1.length() - o2.length();
      }
    });
  }

  public void setNewPathPrefix(@NotNull final String newPathPrefix) {
    myNewPathPrefix = newPathPrefix;
  }

  @NotNull
  public String getNewPathPrefix() {
    return myNewPathPrefix;
  }

  @NotNull
  public List<String> getOriginalPathPrefixes() {
    return myOriginalPathPrefixes;
  }

  @Override
  public String toString() {
    return "originalPrefixes: " + myOriginalPathPrefixes + ", newPrefix: '" + myNewPathPrefix + "'";
  }
}
