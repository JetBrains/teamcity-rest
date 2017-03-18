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

package jetbrains.buildServer.server.rest.data;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Created by yaegor on 18/03/2017.
 */
public class FinderImplTest extends BaseFinderTest<String> {
  static final String NAME = "name";

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
  }

  // TW-46697
  @Test
  public void testOrWithCount() {
    setFinder(new TestItemFinder(2L, "a1", "a2", "a3", "b1", "b2", "b3", "c1", "c2", "c3"));
    check("firstChar:a,count:10", "a1", "a2", "a3");
    check("firstChar:a", "a1", "a2");
    check("firstChar:b", "b1", "b2");
    check("or(firstChar:a,firstChar:b),count:5", "a1", "a2", "a3", "b1", "b2");
    check("or(firstChar:b,firstChar:a),count:5", "a1", "a2", "a3", "b1", "b2");
    check("prefixed:(firstChar:a),count:10", "_a1", "_a2");
    check("prefixed:(firstChar:b),count:10", "_b1", "_b2");
    check("prefixed:(or:(firstChar:a,firstChar:b),count:4),count:10", "_a1", "_a2", "_a3", "_b1");
  }

  private static class TestItemFinder extends AbstractFinder<String> {
    private final List<String> testItems;
    private final Long myDefaultCount;

    TestItemFinder(@Nullable final Long defaultCount, String... items) {
      super("text", "start", "end", "firstChar", "secondChar", "prefixed");
      myDefaultCount = defaultCount;
      testItems = Arrays.asList(items);
    }

    @Nullable
    @Override
    public Long getDefaultPageItemsCount() {
      return myDefaultCount;
    }

    @NotNull
    @Override
    public ItemHolder<String> getPrefilteredItems(@NotNull final Locator locator) {
      final String prefixed = locator.getSingleDimensionValue("prefixed");
      if (prefixed != null) {
        final TestItemFinder newSequence = new TestItemFinder(myDefaultCount, testItems.stream().toArray(l -> new String[l]));
        return getItemHolder(newSequence.getItems(prefixed).myEntries.stream().map(s -> "_" + s).collect(Collectors.toList()));
      }
      // .stream().map(s -> new StringBuilder(s).reverse().toString()).toArray(s -> new String[s])

      final int start = locator.getSingleDimensionValueAsLong("start", 0L).intValue();
      final int end = locator.getSingleDimensionValueAsLong("end", (long)testItems.size()).intValue();
      return getItemHolder(testItems.subList(start, end));
    }

    @NotNull
    @Override
    public ItemFilter<String> getFilter(@NotNull final Locator locator) {
      final MultiCheckerFilter<String> result = new MultiCheckerFilter<>();

      final String test = locator.getSingleDimensionValue("text");
      if (test != null) {
        result.add(item -> item.equals(test));
      }
      final String firstChar = locator.getSingleDimensionValue("firstChar");
      if (firstChar != null) {
        result.add(item -> item.length() > 0 && item.charAt(0) == firstChar.charAt(0));
      }
      final String secondChar = locator.getSingleDimensionValue("secondChar");
      if (secondChar != null) {
        result.add(item -> item.length() > 1 && item.charAt(1) == secondChar.charAt(0));
      }
      return result;
    }

    @NotNull
    @Override
    public String getItemLocator(@NotNull final String s) {
      return Locator.getStringLocator("text", s);
    }
  }
}