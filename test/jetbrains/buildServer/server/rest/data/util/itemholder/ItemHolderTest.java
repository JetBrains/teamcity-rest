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

package jetbrains.buildServer.server.rest.data.util.itemholder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class ItemHolderTest {

  @Test
  public void test_filter_map() {
    ItemHolder<Integer> itemHolder = ItemHolder.of(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20));

    assertEquals(
      Arrays.asList("2", "4", "6", "8", "10"),
      toList(
        itemHolder.filter(it -> it % 2 == 0)
           .filter(it -> it <= 10)
           .map(Object::toString)
      )
    );
  }

  @Test
  public void test_flatMap() {
    ItemHolder<Integer> ih1 = ItemHolder.of(Arrays.asList(1, 2, 3));
    ItemHolder<Integer> ih2 = ItemHolder.of(Arrays.asList(4, 5, 6));
    ItemHolder<Integer> ih3 = ItemHolder.of(Arrays.asList(7, 8, 9));
    ItemHolder<ItemHolder<Integer>> itemHolder = ItemHolder.of(Arrays.asList(ih1, ih2, ih3));
    assertEquals(
      Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9),
      toList(itemHolder.flatMap(Function.identity()))
    );
  }

  @Test
  public void test_laziness_flatMap() {
    Stream<Integer> stream123 = Stream.of(1, 2, 3);
    Stream<Integer> stream456 = Stream.of(4, 5, 6);
    Stream<Integer> stream789 = Stream.of(7, 8, 9);
    AtomicInteger counter = new AtomicInteger();
    ItemHolder
      .of(Arrays.asList(stream123, stream456, stream789))
      .flatMap(ItemHolder::of)
      .process(element -> {
        counter.incrementAndGet();
        return element < 5;
      });
    assertEquals(5, counter.get());
    stream789.count(); // run terminal operation on stream to verify that stream was not used by ItemHolder.
  }

  private static <T> List<T> toList(ItemHolder<T> ih) {
    List<T> result = new ArrayList<>();
    ih.process(result::add);
    return result;
  }

}