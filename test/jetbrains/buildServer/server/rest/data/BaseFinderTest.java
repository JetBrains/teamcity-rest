/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import jetbrains.buildServer.log.Loggable;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Created by yaegor on 13/06/2015.
 */
public abstract class BaseFinderTest<T extends Loggable> extends BaseServerTestCase{
  private AbstractFinder<T> myFinder;

  public void setFinder(AbstractFinder<T> finder){
    myFinder = finder;
  }

  public void check(@Nullable final String locator, T... items) {
    final List<T> result = myFinder.getItems(locator).myEntries;
    final String expected = getDescription(Arrays.asList(items));
    final String actual = getDescription(result);
    assertEquals("For itemS locator \"" + locator + "\"\n" +
                 "Expected:\n" + expected + "\n\n" +
                 "Actual:\n" + actual, items.length, result.size());

    for (int i = 0; i < items.length; i++) {
      if (!items[i].equals(result.get(i))) {
        fail("Wrong item found for locator \"" + locator + "\" at position " + (i + 1) + "/" + items.length + "\n" +
             "Expected:\n" + expected + "\n" +
             "\nActual:\n" + actual);
      }
    }

    //check single item retrieve
    if (locator != null) {
      if (items.length == 0) {
        try {
          T singleResult = myFinder.getItem(locator);
          fail("No items should be found by locator \"" + locator + "\", but found: " + LogUtil.describeInDetail(singleResult));
        } catch (NotFoundException e) {
          //exception is expected
        }
      } else {
        T singleResult = myFinder.getItem(locator);
        final T item = items[0];
        if (!item.equals(singleResult)) {
          fail("While searching for single item with locator \"" + locator + "\"\n" +
               "Expected: " + LogUtil.describeInDetail(item) + "\n" +
               "Actual: " + LogUtil.describeInDetail(singleResult));
        }
      }
    }
  }

  public <E extends Throwable> void checkExceptionOnItemsSearch(final Class<E> exception, final String multipleSearchLocator) {
    BuildPromotionFinderTest.checkException(exception, new Runnable() {
      public void run() {
        myFinder.getItems(multipleSearchLocator);
      }
    }, "searching for itemS with locator \"" + multipleSearchLocator + "\"");
  }

  public <E extends Throwable> void checkExceptionOnItemSearch(final Class<E> exception, final String singleSearchLocator) {
    BuildPromotionFinderTest.checkException(exception, new Runnable() {
      public void run() {
        myFinder.getItem(singleSearchLocator);
      }
    }, "searching for item with locator \"" + singleSearchLocator + "\"");
  }

  public String getDescription(final List<T> result) {
    return LogUtil.describe(result, false, "\n", "", "");
  }

}
