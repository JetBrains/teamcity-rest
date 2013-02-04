package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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
    final Locator locator = new Locator("name:(Bob:32(,age:2),mood:permissive");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(2, locator.getDimensionsCount());
    assertEquals(null, locator.getSingleDimensionValue("age"));
    assertEquals("Bob:32(,age:2", locator.getSingleDimensionValue("name"));
    assertEquals("permissive", locator.getSingleDimensionValue("mood"));
  }

  @Test
  public void testComplexValues2() {
    final Locator locator = new Locator("a:smth,name:(Bob:32(,age:2),mood:permissive");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(3, locator.getDimensionsCount());
    assertEquals("smth", locator.getSingleDimensionValue("a"));
    assertEquals("Bob:32(,age:2", locator.getSingleDimensionValue("name"));
    assertEquals("permissive", locator.getSingleDimensionValue("mood"));
  }

  @Test
  public void testComplexValues3() {
    final Locator locator = new Locator("name:(Bob:32(,age:2),mood:(permissive)");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(2, locator.getDimensionsCount());
    assertEquals("Bob:32(,age:2", locator.getSingleDimensionValue("name"));
    assertEquals("permissive", locator.getSingleDimensionValue("mood"));
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
  public void testEmptyValues() {
    final Locator locator = new Locator("name:,y:aaa,x:");
    assertEquals(3, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("name"));
    assertEquals("aaa", locator.getSingleDimensionValue("y"));
    assertEquals("", locator.getSingleDimensionValue("x"));
  }

  @DataProvider(name = "invalid-complex-values")
  public Object[][] getInvalidComplexValues() {
    return new Object[][] {
        {"name:("},
        {"name:(value"},
        {"name:,a"},
        {":value"},
        {"name:value,:value2"},
        {"name:value,(a:b)"},
        {"name:(val)a"}
    };
  }

  @Test(dataProvider = "invalid-complex-values", expectedExceptions = LocatorProcessException.class)
  public void testComplexValuesParsingErrors(String value) {
    new Locator(value);
  }
}
