package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.server.rest.errors.BadRequestException;
import junit.framework.TestCase;
import org.junit.Test;

/**
 * @author Yegor.Yarko
 *         Date: 14.08.2010
 */
public class LocatorTest extends TestCase {

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
    } catch (BadRequestException ex) {
    }
  }

  @Test
  public void testSingleNumericValue() {
    final Locator locator = new Locator("123");
    assertEquals(true, locator.isSingleValue());
    assertEquals(new Long(123), locator.getSingleValueAsLong());
    assertEquals(0, locator.getDimensionsCount());
  }

  @Test
  public void testEmpty() {
    try {
      new Locator("");
      fail();
    } catch (BadRequestException ex) {
    }
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
    } catch (BadRequestException ex) {
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

}
