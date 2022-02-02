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

import java.util.Comparator;
import java.util.TreeSet;
import org.jetbrains.annotations.NotNull;

/**
 * Uses given comparator for duplicates checking, effectively internally mainiting sorted order of the checked items.
 */
public class ComparatorDuplicateChecker<ITEM> implements DuplicateChecker<ITEM> {
  private final TreeSet<ITEM> mySeenItems;

  public ComparatorDuplicateChecker(@NotNull Comparator<ITEM> comparator) {
    mySeenItems = new TreeSet<>(comparator);
  }

  @Override
  public boolean checkDuplicateAndRemember(@NotNull ITEM item) {
    return !mySeenItems.add(item);
  }
}
