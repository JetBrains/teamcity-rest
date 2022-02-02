/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * Set-based duplicate checker, useful for checking duplicates using some key, not the item itself, e.g. checking VcsModifications by version.
 */
public class KeyDuplicateChecker<ITEM, KEY> implements DuplicateChecker<ITEM> {
  private final Function<ITEM, KEY> myKeyExtractor;
  private final Set<KEY> mySeenKeys = new HashSet<>();

  public KeyDuplicateChecker(@NotNull Function<ITEM, KEY> keyExtractor) {
    myKeyExtractor = keyExtractor;
  }

  @Override
  public boolean checkDuplicateAndRemember(@NotNull ITEM item) {
    return !mySeenKeys.add(myKeyExtractor.apply(item));
  }
}
