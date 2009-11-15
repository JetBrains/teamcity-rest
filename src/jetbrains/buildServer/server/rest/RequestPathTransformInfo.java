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
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 15.11.2009
 */
public class RequestPathTransformInfo {
  private Set<String> myOriginalPathPrefixes = Collections.emptySet();
  private String myNewPathPrefix = "";

  public RequestPathTransformInfo() {
  }

  public void setOriginalPathPrefixes(@NotNull final Set<String> originalPathPrefixes) {
    myOriginalPathPrefixes = originalPathPrefixes;
  }

  public void setNewPathPrefix(@NotNull final String newPathPrefix) {
    myNewPathPrefix = newPathPrefix;
  }

  @NotNull
  public String getNewPathPrefix() {
    return myNewPathPrefix;
  }

  @NotNull
  public Set<String> getOriginalPathPrefixes() {
    return myOriginalPathPrefixes;
  }

  @Override
  public String toString() {
    return "originalPrefixes: " + myOriginalPathPrefixes + ", newPrefix: '" + myNewPathPrefix + "'";
  }
}
