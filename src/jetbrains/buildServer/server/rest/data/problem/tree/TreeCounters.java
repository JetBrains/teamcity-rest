/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.problem.tree;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a quantitave characteristic of a {@link ScopeTree} node.<br/>
 * For each node in a tree the following statement holds true:<br/>
 * <code>
 *   parentCounters == parent.getChildren().reduce(ZERO, (c1, c2) -> c1.combinedWith(c2));
 * </code>
 */
public interface TreeCounters<COUNTERS_IMPL extends TreeCounters<COUNTERS_IMPL>> {
  /**
   * @return Calculates a combined counters, i.e. this is a generalized addition.
   * @implSpec Implementation must treat <code>this</code> and <code>other</code> as immutable and return a new instance of <code>T</code>.
   */
  COUNTERS_IMPL combinedWith(@NotNull COUNTERS_IMPL other);
}
