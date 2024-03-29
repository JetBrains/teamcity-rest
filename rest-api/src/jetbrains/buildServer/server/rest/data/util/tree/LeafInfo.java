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

package jetbrains.buildServer.server.rest.data.util.tree;

import java.util.Collection;
import org.jetbrains.annotations.NotNull;

/**
 * Represents tree leaf source data, which is used to build a full scope tree out of given leaves, see {@link ScopeTree}.
 * Holds a collection of data associated with this leaf and counters which representing this data.
 */
public interface LeafInfo<DATA, COUNTERS extends TreeCounters<COUNTERS>> {
  @NotNull
  COUNTERS getCounters();

  @NotNull
  Iterable<Scope> getPath();

  @NotNull
  Collection<DATA> getData();
}
