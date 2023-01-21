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

package jetbrains.buildServer.server.rest.data.finder;

import java.util.stream.Stream;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.model.PagerData;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 * Date: 20/04/2019
 */
public class TypedFinderTest extends BaseFinderTest<String> {
  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void testGeneric() {
    TypedFinderBuilder<String> builder = new TypedFinderBuilder<>();
    builder.dimensionString(new TypedFinderBuilder.Dimension<>("prefix")).description("prefix of the value").filter((s, item) -> item.startsWith(s));
    builder.dimensionString(new TypedFinderBuilder.Dimension<>("suffix")).description("suffix of the value").filter((s, item) -> item.endsWith(s));
    builder.multipleConvertToItemHolder(TypedFinderBuilder.DimensionCondition.ALWAYS, dimensions -> ItemHolder.of(
      Stream.of("a1", "a2", "a3", "b1", "b2", "b3", "c1", "c2", "c3")));
    setFinder(new DelegatingFinder(builder.build()));

    check(null, "a1", "a2", "a3", "b1", "b2", "b3", "c1", "c2", "c3");
    check("prefix:a", "a1", "a2", "a3");
    check("prefix:a,count:2", "a1", "a2");
    check("prefix:b", "b1", "b2", "b3");
    check("or(prefix:a,prefix:c)", "a1", "a2", "a3", "c1", "c2", "c3");
    check("or(prefix:c,prefix:a),count:4", "a1", "a2", "a3", "c1");
  }

  @Test
  public void testDefaults() {
    TypedFinderBuilder<String> builder = new TypedFinderBuilder<>();
    builder.dimensionString(new TypedFinderBuilder.Dimension<>("prefix")).description("prefix of the value").filter((s, item) -> item.startsWith(s));
    builder.dimensionString(new TypedFinderBuilder.Dimension<>("suffix")).description("suffix of the value").filter((s, item) -> item.endsWith(s));
    builder.dimensionLong(new TypedFinderBuilder.Dimension<>(PagerData.COUNT)).description("number of items to return").withDefault(String.valueOf(5));
    builder.multipleConvertToItemHolder(TypedFinderBuilder.DimensionCondition.ALWAYS, dimensions -> ItemHolder.of(
      Stream.of("b1", "a1", "a2", "a3", "a4", "a5", "a6", "c1", "c2", "c3")));
    setFinder(new DelegatingFinder(builder.build()));

    check(null, "b1", "a1", "a2", "a3", "a4");
    check("count:2", "b1", "a1");
    check("count:10", "b1", "a1", "a2", "a3", "a4", "a5", "a6", "c1", "c2", "c3");
    check("prefix:a", "a1", "a2", "a3", "a4", "a5");
    check("prefix:a,count:3", "a1", "a2", "a3");
  }

  @Test
  public void testSeveralAlwaysConditions() {
    TypedFinderBuilder<String> builder = new TypedFinderBuilder<>();
    builder.dimensionString(new TypedFinderBuilder.Dimension<>("prefix")).withDefault("a").filter((s, item) -> item.startsWith(s));
    builder.dimensionString(new TypedFinderBuilder.Dimension<>("suffix")).filter((s, item) -> item.endsWith(s));
    builder.dimensionLong(new TypedFinderBuilder.Dimension<>(PagerData.COUNT)).withDefault(String.valueOf(5));
    builder.multipleConvertToItemHolder(TypedFinderBuilder.DimensionCondition.ALWAYS, dimensions -> ItemHolder.of(
      Stream.of("b1", "a1", "a2", "a3", "a4", "a5", "a6", "c1", "c2", "c3")));
    setFinder(new DelegatingFinder(builder.build()));

    check(null, "a1", "a2", "a3", "a4", "a5");
    check("count:2", "a1", "a2");
    check("prefix:c", "c1", "c2", "c3");
  }
}
