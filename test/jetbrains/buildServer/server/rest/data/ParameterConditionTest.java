/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.parameters.impl.MapParametersProviderImpl;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.Test;

import static jetbrains.buildServer.util.Util.map;

/**
 * @author Yegor.Yarko
 *         Date: 22/04/2016
 */
public class ParameterConditionTest extends BaseServerTestCase { //need to extend BaseServerTestCase at least to get TeamCityProperties
  @Test
  public void testBasic() {
    assertNull(ParameterCondition.create((String)null));

    exception(LocatorProcessException.class, "");
    exception(LocatorProcessException.class, "name:a,some:b");
    exception(LocatorProcessException.class, "some:b");
    exception(BadRequestException.class, "name:a,value:b,matchType:abra");
  }

  @Test
  public void testName() {
    matchesFalse("name:aaa");
    matchesTrue("name:aaa", "aaa", "value");
    matchesTrue("name:aaa", "aaa", "");
    matchesFalse("name:aaa", "aaaa", "value");
    matchesFalse("name:aaa", "aa", "value");
    matchesFalse("name:aaa", "x", "aaa");
    matchesFalse("name:aaa", "aa", "");
    matchesFalse("name:a", "A", "value");
    matchesFalse("name:A", "a", "value");

    matchesTrue("a", "a", "value");
    matchesFalse("a", "aa", "a");
    matchesFalse("a");
    matchesFalse("A", "a", "value");
  }

  @Test
  public void testValue() {
    matchesFalse("value:aaa");
    matchesTrue("value:aaa", "x", "aaa");
    matchesTrue("value:aaa", "", "aaa");
    matchesTrue("value:aaa", "x", "aaaa"); //contains by default
    matchesTrue("value:aaa", "x", "baaa");
    matchesTrue("value:aaa", "x", "aaac");
    matchesTrue("value:aaa", "x", "baaac");
    matchesFalse("value:aaa", "x", "AAA");
    matchesFalse("value:aaa", "x", "aa");
  }

  @Test
  public void testNameValue() {
    matchesFalse("name:xxx,value:aaa");
    matchesTrue("name:xxx,value:aaa", "xxx", "aaa");
    matchesTrue("name:xxx,value:aaa", "xxx", "baaac");
    matchesFalse("name:xxx,value:aaa", "xxx", "AAA");
    matchesFalse("name:xxx,value:aaa", "xxxx", "AAA");
    matchesFalse("name:xxx,value:aaa", "xxxx", "");
    matchesFalse("name:xxx,value:aaa", "", "aaa");
  }

  @Test
  public void testMatches() {
    matchesFalse("matchType:exists"); //todo ?
    matchesTrue("matchType:exists", "a", "b"); //todo ?
    matchesFalse("matchType:not-exists");  //todo ?

    exception(BadRequestException.class, "matchType:matches");
    exception(BadRequestException.class, "matchType:does-not-match");

    exception(BadRequestException.class, "matchType:equals");
    exception(BadRequestException.class, "name:xxx,matchType:matches");
    matchesFalse("value:x.x,matchType:matches", "a", "z", "b", "zzz"); //at least some parameter should match
    matchesTrue("value:x.x,matchType:matches", "a", "x", "b", "xxx");
    matchesFalse("value:x.x,matchType:matches,matchScope:all", "a", "x", "b", "xxx");
    matchesTrue("value:x.x,matchType:matches,matchScope:all", "a", "xXx", "b", "xxx");


    matchesFalse("name:xxx,value:aaa,matchType:matches");
    matchesTrue("name:xxx,value:aaa,matchType:matches", "xxx", "aaa");
    matchesFalse("name:xxx,value:aaa,matchType:matches", "xxx", "aaaa");
    matchesFalse("name:xxx,value:aaa,matchType:matches", "yyy", "aaa");
    matchesFalse("name:xxx,value:aaa,matchType:matches", "", "aaa");

    matchesTrue("name:xxx,value:.,matchType:matches", "xxx", "a");
    matchesTrue("name:xxx,value:.,matchType:matches", "xxx", "A");
    matchesTrue("name:xxx,value:.,matchType:matches", "xxx", ".");
    matchesFalse("name:xxx,value:.,matchType:matches", "xxx", "aa");

    exception(BadRequestException.class, "name:xxx,value:.,matchType:Matches");

    matchesTrue("name:xxx,value:a.c,matchType:matches", "xxx", "abc", "yyy", "ccc");
    matchesFalse("name:xxx,value:a.c,matchType:matches", "xxx", "abb", "yyy", "ccc");

    matchesTrue("name:xxx,matchType:exists", "xxx", "abc", "yyy", "ccc");
    matchesTrue("name:xxx,value:mmm,matchType:exists", "xxx", "abc", "yyy", "ccc");
    matchesFalse("name:xxx,matchType:exists", "xxxy", "abc", "yyy", "ccc");

    matchesFalse("name:xxx,matchType:not-exists", "xxx", "abc", "yyy", "ccc");
    matchesFalse("name:xxx,value:mmm,matchType:not-exists", "xxx", "abc", "yyy", "ccc");
    matchesTrue("name:xxx,matchType:not-exists", "xxxy", "abc", "yyy", "ccc");

    matchesTrue("name:xxx,value:abc,matchType:equals", "xxx", "abc", "yyy", "ccc");
    matchesFalse("name:xxx,value:abc,matchType:equals", "xxx", "abcc", "yyy", "abc");

    matchesFalse("name:xxx,value:abc,matchType:does-not-equal", "xxx", "abc", "yyy", "ccc");
    matchesTrue("name:xxx,value:abc,matchType:does-not-equal", "xxx", "abcc", "yyy", "abc");

    exception(BadRequestException.class, "name:xxx,matchType:more-than");
    matchesFalse("name:xxx,value:a,matchType:more-than", "xxx", "b", "yyy", "ccc");  //should report error?
    matchesTrue("name:xxx,value:1.5,matchType:more-than", "xxx", "1.5005", "yyy", "ccc");
    matchesFalse("name:xxx,value:1.5,matchType:more-than", "xxx", "1.4995", "yyy", "ccc");
    matchesFalse("name:xxx,value:1.5,matchType:more-than", "xxx", "-2", "yyy", "ccc");
    matchesFalse("name:xxx,value:(1,5),matchType:more-than", "xxx", "5", "yyy", "ccc");  //should report error?
    matchesTrue("name:xxx,value:-5,matchType:more-than", "xxx", "0", "yyy", "ccc");
    matchesFalse("name:xxx,value:-5,matchType:more-than", "xxx", "-5", "yyy", "ccc");
    matchesFalse("name:xxx,value:-5,matchType:more-than", "xxx", "-15", "yyy", "ccc");
    matchesFalse("name:xxx,value:-5,matchType:more-than", "xxx", "-900000000000000000000000000000000000000000000", "yyy", "ccc");
    matchesTrue("name:xxx,value:-5,matchType:more-than", "xxx", "900000000000000000000000000000000000000000000", "yyy", "ccc");

    matchesFalse("name:xxx,value:-5,matchType:no-more-than", "xxx", "0", "yyy", "ccc");
    matchesTrue("name:xxx,value:-5,matchType:no-more-than", "xxx", "-5", "yyy", "ccc");
    matchesTrue("name:xxx,value:-5,matchType:no-more-than", "xxx", "-15", "yyy", "ccc");

    matchesTrue("value:a,matchType:any", "xxx", "abc", "yyy", "ccc");
    matchesTrue("name:xxx,matchType:any", "xxx", "abc", "yyy", "ccc");
    matchesTrue("name:xxx,value:abc,matchType:any", "xxx", "abc", "yyy", "ccc");
    matchesTrue("name:xxx,value:abc,matchType:any");
  }

  @Test
  public void testInheritable() {
    assertTrue(matchesWithOwn("name:a", new MapParametersProviderImpl(map("a", "b", "x", "y")), new MapParametersProviderImpl(map("x", "y"))));
    assertTrue(matchesWithOwn("name:x", new MapParametersProviderImpl(map("a", "b", "x", "y")), new MapParametersProviderImpl(map("x", "y"))));
    assertTrue(matchesWithOwn("name:a,inherited:true", new MapParametersProviderImpl(map("a", "b", "x", "y")), new MapParametersProviderImpl(map("x", "y"))));
    assertFalse(matchesWithOwn("name:a,inherited:false", new MapParametersProviderImpl(map("a", "b", "x", "y")), new MapParametersProviderImpl(map("x", "y"))));
    assertFalse(matchesWithOwn("name:x,inherited:true", new MapParametersProviderImpl(map("a", "b", "x", "y")), new MapParametersProviderImpl(map("x", "y"))));
    assertTrue(matchesWithOwn("name:x,inherited:false", new MapParametersProviderImpl(map("a", "b", "x", "y")), new MapParametersProviderImpl(map("x", "y"))));

    assertTrue(matchesWithOwn("name:x,value:y,inherited:false", new MapParametersProviderImpl(map("a", "b", "x", "y")), new MapParametersProviderImpl(map("x", "y"))));
    assertFalse(matchesWithOwn("name:a,value:y,inherited:true", new MapParametersProviderImpl(map("a", "b", "x", "y")), new MapParametersProviderImpl(map("x", "y"))));
  }

  @Test
  public void testNameMatchType() {
    matchesFalse("name:(value:xxx,matchType:contains),value:aaa,matchType:equals", "xx", "aaa", "xxy", "bbb");
    matchesTrue("name:(value:xxx,matchType:contains),value:aaa,matchType:equals", "xxx", "aaa", "xxy", "bbb");
    matchesTrue("name:(value:xxx,matchType:contains),value:aaa,matchType:equals", "xxy", "bbb", "xxx", "aaa");
    matchesFalse("name:(value:xxx,matchType:contains),value:aaa,matchType:equals", "xxx", "bbb", "xxy", "bbb");
    matchesFalse("name:(value:xxx,matchType:contains),value:aaa,matchType:equals", "xxx", "bbb", "xxy", "bbb");
    matchesTrue("name:(value:xxx,matchType:contains),value:aaa,matchType:equals", "xxx", "bbb", "xxxy", "aaa");
    matchesTrue("name:(value:xxx,matchType:contains),value:aaa,matchType:equals", "xxx", "aaa", "xxxy", "aaa");
    matchesTrue("name:(value:xxx,matchType:contains),value:aaa,matchType:equals,matchScope:any", "xxx", "aaa", "xxxy", "aaa");
    matchesTrue("name:(value:xxx,matchType:contains),value:aaa,matchType:equals,matchScope:all", "xxx", "aaa", "xxy", "aaa");
    matchesTrue("name:(value:xxx,matchType:contains),value:aaa,matchType:equals", "xxx", "aaa", "xxy", "aaa", "xxxy", "bbb");
    matchesTrue("name:(value:xxx,matchType:contains),value:aaa,matchType:equals,matchScope:any", "xxx", "aaa", "xxy", "aaa", "xxxy", "bbb");
    matchesFalse("name:(value:xxx,matchType:contains),value:aaa,matchType:equals,matchScope:all", "xxx", "aaa", "xxy", "aaa", "xxxy", "bbb");
    matchesTrue("name:(value:xxx,matchType:contains),value:aaa,matchType:equals,matchScope:all", "xxx", "aaa", "xxxy", "aaa");
    matchesFalse("name:(value:xxx,matchType:contains),value:aaa,matchType:equals,matchScope:all", "xxx", "aaa", "xxxy", "bbb");

    matchesTrue("name:xxx,value:aaa,matchScope:all", "xxx", "aaa", "xxxy", "bbb"); //or should report error?

    matchesTrue("name:(value:.*,matchType:matches),value:aaa,matchType:equals,matchScope:all", "xxx", "aaa", "xxxy", "aaa");
    matchesFalse("name:(value:.*,matchType:matches),value:aaa,matchType:equals,matchScope:all", "xxx", "aaa", "xxxy", "aaab");
    matchesFalse("name:(value:.*,matchType:matches),value:aaa,matchType:equals,matchScope:all", "xxx", "aaa", "xxxy", "aaaB");

    exception(BadRequestException.class, "name:(value:xxx,matchType:contains),value:aaa,matchType:equals,matchScope:aaa");
    exception(BadRequestException.class, "name:(value:xxx,matchType:Contains),value:aaa,matchType:equals,matchScope:aaa");
    exception(BadRequestException.class, "name:(value:xxx,matchType:bbb),value:aaa,matchType:equals,matchScope:aaa");
    exception(BadRequestException.class, "name:(matchType:contains),value:aaa,matchType:equals");
    exception(BadRequestException.class, "name:(matchType:contains),value:aaa,matchType:equals,matchScope:all");

    matchesFalse("value:aaa,matchScope:all", "xxx", "aaa", "xxxy", "bbb");
    matchesTrue("value:aaa,matchScope:all", "xxx", "aaa", "xxxy", "bbaaab");
  }

  @Test
  public void testIgnoreCase() {
    matchesFalse("name:A,value:x,matchType:equals", "a", "x", "A", "X");
    matchesTrue("name:A,value:x,matchType:equals,ignoreCase:true", "a", "x", "A", "X");

    matchesTrue("name:(value:a,matchType:equals),value:x,matchType:equals,matchScope:all", "a", "x", "A", "X");
    matchesFalse("name:(value:a,matchType:equals,ignoreCase:true),value:x,matchType:equals,matchScope:all", "a", "x", "A", "X");

    matchesTrue("name:(value:a,matchType:contains),value:x,matchType:equals,matchScope:all", "a", "x", "A", "X");
    matchesFalse("name:(value:a,matchType:contains,ignoreCase:true),value:x,matchType:equals,matchScope:all", "a", "x", "A", "X");

    matchesFalse("name:(matchType:any),value:x,matchType:equals,matchScope:all", "a", "x", "A", "X");
    matchesTrue("name:(matchType:any),value:x,matchType:equals,matchScope:all,ignoreCase:true", "a", "x", "A", "X");

    matchesSingleFalse("value:x", "X");
    matchesSingleTrue("value:x,ignoreCase:true", "X");
  }

  @Test
  public void testSingleValueMatching() {
    matchesSingleTrue("aaa", "aaa");
    matchesSingleFalse("aaa", "aaaa");
    matchesSingleFalse("aaa", "bbb");

    matchesSingleFalse("value:aaa", null);
    matchesSingleTrue("value:aaa", "aaa");
    matchesSingleTrue("value:aaa", "xxaaayy"); //contains by default ???
    matchesSingleFalse("value:aaa", "AAA");
    matchesSingleFalse("value:aaa", "aa");

    exceptionSingle(BadRequestException.class, "matchType:exists");
    exceptionSingle(BadRequestException.class, "matchType:exists,value:a");
    exceptionSingle(BadRequestException.class, "matchType:not-exists");

    exceptionSingle(BadRequestException.class, "matchType:matches");
    exceptionSingle(BadRequestException.class, "matchType:does-not-match");

    matchesSingleFalse("value:aaa,matchType:equals", null);
    matchesSingleTrue("value:aaa,matchType:equals", "aaa");
    matchesSingleFalse("value:aaa,matchType:equals", "aaaa");

    matchesSingleTrue("value:.,matchType:matches", "a");
    matchesSingleTrue("value:.,matchType:matches", "A");
    matchesSingleTrue("value:.,matchType:matches", ".");
    matchesSingleFalse("value:.,matchType:matches", "aa");

    exceptionSingle(BadRequestException.class, "value:aaa,matchType:exists");
    exceptionSingle(BadRequestException.class, "value:aaa,matchType:not-exists");
    assertNull(ParameterCondition.createValueCondition((String)null));
    exceptionSingle(LocatorProcessException.class, "");
    exceptionSingle(LocatorProcessException.class, "name:a,some:b");
    exceptionSingle(LocatorProcessException.class, "name:a");
    exceptionSingle(LocatorProcessException.class, "some:b");
    exceptionSingle(LocatorProcessException.class, "name:xxx,value:aaa");
    exceptionSingle(LocatorProcessException.class, "name:a,value:b,matchType:contains");
    exceptionSingle(BadRequestException.class, "name:a,value:b,matchType:abra");
    exceptionSingle(LocatorProcessException.class, "name:xxx,value:aaa,matchType:equals,nameMatchType:contains");
    exceptionSingle(LocatorProcessException.class, "name:xxx,value:aaa,matchType:equals,matchScope:any");
  }

  // ==============================
  private static void matchesTrue(@NotNull final String propertyConditionLocator, @NotNull String... args) {
    assertTrue(matches(propertyConditionLocator, args));
  }

  private static void matchesFalse(@NotNull final String propertyConditionLocator, @NotNull String... args) {
    assertFalse(matches(propertyConditionLocator, args));
  }

  private static boolean matches(@NotNull final String propertyConditionLocator, @NotNull String... args) {
    return ParameterCondition.create(propertyConditionLocator).matches(new MapParametersProviderImpl(CollectionsUtil.asMap(args)));
  }


  private static boolean matchesWithOwn(@NotNull final String propertyConditionLocator, @NotNull final ParametersProvider parametersProvider,
                                        @NotNull final ParametersProvider ownParametersProvider) {
    return ParameterCondition.create(propertyConditionLocator).matches(parametersProvider, ownParametersProvider);
  }


  private static void matchesSingleTrue(@NotNull final String propertyConditionLocator, @Nullable String value) {
    assertTrue(matchesSingle(propertyConditionLocator, value));
  }

  private static void matchesSingleFalse(@NotNull final String propertyConditionLocator, @Nullable String value) {
    assertFalse(matchesSingle(propertyConditionLocator, value));
  }

  private static boolean matchesSingle(@NotNull final String propertyConditionLocator, @Nullable String value) {
    return ParameterCondition.createValueCondition(propertyConditionLocator).matches(value);
  }


  private static <E extends Throwable> void exception(final Class<E> exception, @NotNull final String propertyConditionLocator) {
    //noinspection ThrowableResultOfMethodCallIgnored
    BaseFinderTest.checkException(exception, new Runnable() {
      @Override
      public void run() {
        ParameterCondition.create(propertyConditionLocator);
      }
    }, null);
  }

  private static <E extends Throwable> void exceptionSingle(final Class<E> exception, @NotNull final String propertyConditionLocator) {
    //noinspection ThrowableResultOfMethodCallIgnored
    BaseFinderTest.checkException(exception, new Runnable() {
      @Override
      public void run() {
        ParameterCondition.createValueCondition(propertyConditionLocator);
      }
    }, null);
  }
}
