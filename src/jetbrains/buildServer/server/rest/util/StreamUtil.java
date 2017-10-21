/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.util;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 * Date: 20/10/2017
 */
public class StreamUtil {

  /**
   * Combines the elements of the passed streams (already ordered by comparator), into resulting stream ordered by comparator
   */
  public static <T> Stream<T> merge(@NotNull Stream<Stream<T>> streams, @NotNull Comparator<T> comparator) {
    return StreamSupport.stream(new MergingSpliterator<T>(streams, comparator), false);
  }

  private static class MergingSpliterator<T> extends Spliterators.AbstractSpliterator <T> {
    @NotNull private final SortedMap<T, Iterator<T>> myElements;

    MergingSpliterator(@NotNull final Stream<Stream<T>> streams, @NotNull final Comparator<T> comparator) {
      super(Long.MAX_VALUE, 0);
      Set<Iterator<T>> duplicating = new HashSet<>();
      myElements = streams.map(stream -> stream.iterator()).filter(it -> it.hasNext())
                          .collect(Collectors.toMap(it -> it.next(), it -> it, (o, o2) -> {duplicating.add(o2); return o;}, () -> new TreeMap<T, Iterator<T>>(comparator)));
      duplicating.forEach(it -> add(myElements, it));
    }

    @Override
    public boolean tryAdvance(final Consumer<? super T> action) {
      if (myElements.isEmpty()) return false;
      T result = myElements.firstKey();
      action.accept(result);
      Iterator<T> it = myElements.remove(result);
      add(myElements, it);
      return true;
    }

    private void add(@NotNull final SortedMap<T, Iterator<T>> map, @NotNull final Iterator<T> it) {
      while (it.hasNext()) {
        T next = it.next();
        if (map.get(next) == null) {
          myElements.put(next, it);
          return;
        }
      }
    }
  }
}
