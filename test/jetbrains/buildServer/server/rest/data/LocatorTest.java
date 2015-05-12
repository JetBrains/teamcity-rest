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

import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.junit.Assert.*;

/**
 * @author Yegor.Yarko
 *         Date: 14.08.2010
 */
public class LocatorTest {

  @Test
  public void testSingleValue() {
    final Locator locator = new Locator("abc");
    assertEquals(true, locator.isSingleValue());
    assertEquals(0, locator.getDimensionsCount());
    assertEquals(null, locator.getSingleDimensionValue(""));
    assertEquals(null, locator.getSingleDimensionValue("name"));

    try {
      locator.getSingleValueAsLong();
      fail();
    } catch (LocatorProcessException ex) {
    }
  }

  @Test
  public void testSingleNumericValue() {
    final Locator locator = new Locator("123");
    assertEquals(true, locator.isSingleValue());
    assertEquals(new Long(123), locator.getSingleValueAsLong());
    assertEquals(0, locator.getDimensionsCount());
  }

  @Test(expectedExceptions =  LocatorProcessException.class)
  public void testEmpty() {
    new Locator("");
  }

  @Test
  public void testSingleDimension() {
    final Locator locator = new Locator("name:1Vasiliy");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(null, locator.getSingleValueAsLong());
    assertEquals(1, locator.getDimensionsCount());
    assertEquals(null, locator.getSingleDimensionValue(""));
    assertEquals(null, locator.getSingleDimensionValue("missing"));
    assertEquals("1Vasiliy", locator.getSingleDimensionValue("name"));
    assertEquals(null, locator.getSingleDimensionValue("Name"));
    try {
      locator.getSingleDimensionValueAsLong("name");
      fail();
    } catch (LocatorProcessException ex) {
    }
  }

  @Test
  public void testSingleDimensionComplexValue() {
    final Locator locator = new Locator("a:!@#$%^&*()_+\"\'iqhjbw`0912");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(null, locator.getSingleValueAsLong());
    assertEquals(1, locator.getDimensionsCount());
    assertEquals("!@#$%^&*()_+\"\'iqhjbw`0912", locator.getSingleDimensionValue("a"));
  }

  @Test
  public void testSingleNumericDimension() {
    final Locator locator = new Locator("age:15");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(null, locator.getSingleValueAsLong());
    assertEquals(1, locator.getDimensionsCount());
    assertEquals(new Long(15), locator.getSingleDimensionValueAsLong("age"));
    assertEquals(null, locator.getSingleDimensionValueAsLong("name"));
  }

  @Test
  public void testMultiDimension1() {
    final Locator locator = new Locator("name:Bob:32,age:2,mood:permissive");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(3, locator.getDimensionsCount());
    assertEquals(null, locator.getSingleDimensionValue("Bob"));
    assertEquals("Bob:32", locator.getSingleDimensionValue("name"));
    assertEquals("permissive", locator.getSingleDimensionValue("mood"));
    assertEquals(new Long(2), locator.getSingleDimensionValueAsLong("age"));
  }

  @Test
  public void testComplexValues1() {
    final Locator locator = new Locator("name:(Bob:32_,age:2),mood:permissive");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(2, locator.getDimensionsCount());
    assertEquals(null, locator.getSingleDimensionValue("age"));
    assertEquals("Bob:32_,age:2", locator.getSingleDimensionValue("name"));
    assertEquals("permissive", locator.getSingleDimensionValue("mood"));
  }

  @Test
  public void testComplexValues1a(){
    try {
      final Locator locator = new Locator("name:(Bob:32(,age:2),mood:permissive");
      assertTrue("Should never reach here", false);
    } catch (LocatorProcessException e) {
      assertTrue(e.getMessage().contains("Could not find matching ')'"));
      assertTrue(e.getMessage().contains("at position 6"));
    }
  }

  @Test
  public void testComplexValues2() {
    final Locator locator = new Locator("a:smth,name:(Bob:32_,age:2),mood:permissive");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(3, locator.getDimensionsCount());
    assertEquals("smth", locator.getSingleDimensionValue("a"));
    assertEquals("Bob:32_,age:2", locator.getSingleDimensionValue("name"));
    assertEquals("permissive", locator.getSingleDimensionValue("mood"));
  }

  @Test
  public void testComplexValues2a(){
    try {
      final Locator locator = new Locator("a:smth,name:(Bob:32(,age:2),mood:permissive");
      assertTrue("Should never reach here", false);
    } catch (LocatorProcessException e) {
      assertTrue(e.getMessage().contains("Could not find matching ')'"));
      assertTrue(e.getMessage().contains("at position 13"));
    }
  }

  @Test
  public void testComplexValues3() {
    final Locator locator = new Locator("name:(Bob:32_,age:2),mood:(permissive)");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(2, locator.getDimensionsCount());
    assertEquals("Bob:32_,age:2", locator.getSingleDimensionValue("name"));
    assertEquals("permissive", locator.getSingleDimensionValue("mood"));
  }

  @Test
  public void testComplexValues3a(){
    try {
      final Locator locator = new Locator("name:(Bob:32(,age:2),mood:(permissive)");
      assertTrue("Should never reach here", false);
    } catch (LocatorProcessException e) {
      assertTrue(e.getMessage().contains("Could not find matching ')'"));
    }
  }

  @Test
  public void testComplexValues4() {
    final Locator locator = new Locator("name:17,mood:(permiss:ive)");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(2, locator.getDimensionsCount());
    assertEquals("17", locator.getSingleDimensionValue("name"));
    assertEquals("permiss:ive", locator.getSingleDimensionValue("mood"));
  }

  @Test
  public void testNestedComplexValues1() {
    final Locator locator = new Locator("buildType:(name:5,project:(id:Project_1))");
    assertEquals(false, locator.isSingleValue());
    assertEquals(1, locator.getDimensionsCount());
    assertEquals("name:5,project:(id:Project_1)", locator.getSingleDimensionValue("buildType"));
  }

  @Test
  public void testNestedComplexValues2() {
    final Locator locator = new Locator("buildType:(name:5),project:(id:Project_1)");
    assertEquals(false, locator.isSingleValue());
    assertEquals("name:5", locator.getSingleDimensionValue("buildType"));
    assertEquals("id:Project_1", locator.getSingleDimensionValue("project"));
  }

  @Test
  public void testNestedComplexValues3() {
    final Locator locator = new Locator("buildType:((name:5,project:(id:Project_1)))");
    assertEquals(false, locator.isSingleValue());
    assertEquals("(name:5,project:(id:Project_1))", locator.getSingleDimensionValue("buildType"));
  }

  @Test
  public void testNestedComplexValues4() {
    final Locator locator = new Locator("buildType:(name:5,(project:(id:Project_1)),a:b(c),d),f:d");
    assertEquals(false, locator.isSingleValue());
    assertEquals("name:5,(project:(id:Project_1)),a:b(c),d", locator.getSingleDimensionValue("buildType"));
  }

  @Test
  public void testEmptyValues() {
    final Locator locator = new Locator("name:,y:aaa,x:");
    assertEquals(3, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("name"));
    assertEquals("aaa", locator.getSingleDimensionValue("y"));
    assertEquals("", locator.getSingleDimensionValue("x"));
  }

  @Test
  public void testMisc1() {
    final Locator locator = new Locator("a:,b:");
    assertEquals(2, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("a"));
    assertEquals("", locator.getSingleDimensionValue("b"));
    assertEquals(null, locator.getSingleDimensionValue("c"));
  }

  @Test
  public void testValueLess1() {
    final Locator locator = new Locator("id,number,status", true, null);
    assertEquals(3, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("id"));
    assertEquals("", locator.getSingleDimensionValue("number"));
    assertEquals("", locator.getSingleDimensionValue("status"));
  }

  @Test
  public void testValueLess2() {
    final Locator locator = new Locator("buildType(name,project(id,name))", true, null);
    assertEquals(1, locator.getDimensionsCount());
    assertEquals("name,project(id,name)", locator.getSingleDimensionValue("buildType"));
  }

  @Test
  public void testValueLess21() {
    final Locator locator = new Locator("buildType(name,project(id,name),builds)", true, null);
    assertEquals(1, locator.getDimensionsCount());
    assertEquals("name,project(id,name),builds", locator.getSingleDimensionValue("buildType"));
    assertEquals(null, locator.getSingleDimensionValue("builds"));
  }

  @Test
  public void testValueLess22() {
    final Locator locator = new Locator("buildType(name,project(id,name),builds),href", true, null);
    assertEquals(2, locator.getDimensionsCount());
    assertEquals("name,project(id,name),builds", locator.getSingleDimensionValue("buildType"));
    assertEquals("", locator.getSingleDimensionValue("href"));
  }

  @Test
  public void testValueLess23() {
    final Locator locator = new Locator("count,buildType:(name,project(id,name),builds),href", true, null);
    assertEquals(3, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("count"));
    assertEquals("name,project(id,name),builds", locator.getSingleDimensionValue("buildType"));
    assertEquals("", locator.getSingleDimensionValue("href"));
  }

  @Test
  public void testValueLess3() {
    final Locator locator = new Locator("name,project(id,name)", true, null);
    assertEquals(2, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("name"));
    assertEquals("id,name", locator.getSingleDimensionValue("project"));
  }

  @Test
  public void testValueLess4() {
    final Locator locator = new Locator("name,project(id,name),builds(),x", true, null);
    assertEquals(4, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("name"));
    assertEquals("id,name", locator.getSingleDimensionValue("project"));
    assertEquals("", locator.getSingleDimensionValue("builds"));
    assertEquals("", locator.getSingleDimensionValue("x"));
  }

  @Test
  public void testValueLess5() {
    final Locator locator = new Locator("count,parentProject(id),projects(id)", true, null);
    assertEquals(3, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("count"));
    assertEquals(null, locator.getSingleDimensionValue("parentproject"));
    assertEquals("id", locator.getSingleDimensionValue("parentProject"));
    assertEquals("id", locator.getSingleDimensionValue("projects"));
  }

  @Test
  public void testMisc2() {
    final Locator locator = new Locator("a:x y ,b(x y),c", true, null);
    assertEquals(3, locator.getDimensionsCount());
    assertEquals("x y ", locator.getSingleDimensionValue("a"));
    assertEquals("x y", locator.getSingleDimensionValue("b"));
    assertEquals("", locator.getSingleDimensionValue("c"));
  }

  @Test
  public void testMisc3() {
    final Locator locator = new Locator("name:,a", true, null);
    assertEquals(2, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("name"));
    assertEquals("", locator.getSingleDimensionValue("a"));
    assertEquals(null, locator.getSingleDimensionValue("b"));
  }

  @Test
  public void testSingleValueExtendedMode() {
    final Locator locator = new Locator("a", true, null);
    assertEquals(false, locator.isSingleValue());
    assertEquals(1, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("a"));
  }

  @Test
  public void testCustomNames1() {
    final Locator locator = new Locator("~!@#$%^&*_+(c),+,$aaa:bbb", true, "~!@#$%^&*_+", "$aaa", "+", "-");
    assertEquals(3, locator.getDimensionsCount());
    assertEquals("c", locator.getSingleDimensionValue("~!@#$%^&*_+"));
    assertEquals("", locator.getSingleDimensionValue("+"));
    assertEquals("bbb", locator.getSingleDimensionValue("$aaa"));
    assertEquals(null, locator.getSingleDimensionValue("aaa"));
    assertEquals(null, locator.getSingleDimensionValue("~"));
  }

  @Test(expectedExceptions = LocatorProcessException.class)
  public void testCustomNamesErrors() {
    new Locator("~aa:b", true, "~a", "~aaa", "-");
  }

  @DataProvider(name = "invalid-complex-values")
  public String[][] getInvalidComplexValues() {
    return new String[][] {
        {"name:("},
        {"name:(value"},
        {":value"},
        {"name:value,:value2"},
        {"name:value,(a:b)"},
        {"name:(val)a"},
        {"-:x"},
        {"a-b:y"}
    };
  }

  @Test(dataProvider = "invalid-complex-values", expectedExceptions = LocatorProcessException.class)
  public void testComplexValuesParsingErrors(String value) {
    new Locator(value);
  }

  @DataProvider(name = "invalid-complex-values-extendedMode")
  public String[][] getInvalidComplexValuesExtendedMode() {
    return new String[][] {
        {"a(b)(c),d"},
        {"a,b(a ,( b)"},
        {"+"},
        {"$a"},
        {"a$b"}
    };
  }

  @Test(dataProvider = "invalid-complex-values-extendedMode", expectedExceptions = LocatorProcessException.class)
  public void testComplexValuesParsingErrorsExtendedMode(String value) {
    new Locator(value, true, null);
  }

  @DataProvider(name = "valid-complex-values-extendedMode")
  public String[][] getValidComplexValuesExtendedMode() {
    return new String[][]{
      {"-"},
      {"a-b(-)"},
      {"a-b"},
      {"a-b:ccc"},
      {"a-b:(ccc-ddd)"}
    };
  }

  @Test(dataProvider = "valid-complex-values-extendedMode")
  public void testComplexValuesParsingNoErrorsExtendedMode(String value) {
    new Locator(value, true, null);
  }
}
