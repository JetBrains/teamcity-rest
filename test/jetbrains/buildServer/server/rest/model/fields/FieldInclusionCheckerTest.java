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

package jetbrains.buildServer.server.rest.model.fields;


import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.fieldInclusion.FieldInclusionChecker;
import jetbrains.buildServer.server.rest.util.fieldInclusion.FieldRule;
import jetbrains.buildServer.server.rest.util.fieldInclusion.FieldStrategy;
import jetbrains.buildServer.server.rest.util.fieldInclusion.FieldStrategySupported;
import org.junit.Assert;
import org.testng.annotations.Test;

public class FieldInclusionCheckerTest {
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void checksForSupportAnnotation() {
    FieldInclusionChecker.getForClass(Object.class);
  }

  @Test
  public void doesNotCreateDuplicates() {
    FieldInclusionChecker checker1 = FieldInclusionChecker.getForClass(Model.class);
    FieldInclusionChecker checker2 = FieldInclusionChecker.getForClass(Model.class);

    Assert.assertSame(checker1, checker2);
  }

  @Test
  public void testDefaultsDefaults() {
    FieldInclusionChecker checker = FieldInclusionChecker.getForClass(Model.class);

    Assert.assertNull(checker.isIncluded("getter", Fields.SHORT));
    Assert.assertNull(checker.isIncluded("getter", Fields.LONG));
    Assert.assertFalse(checker.isIncluded("getter", Fields.NONE));
    Assert.assertTrue(checker.isIncluded("getter", Fields.ALL));
  }

  @Test
  public void testDefaultsExclude() {
    FieldInclusionChecker checker = FieldInclusionChecker.getForClass(Model.class);

    Assert.assertFalse(checker.isIncluded("exclude", Fields.SHORT));
    Assert.assertFalse(checker.isIncluded("exclude", Fields.LONG));
    Assert.assertFalse(checker.isIncluded("exclude", Fields.NONE));
    Assert.assertTrue(checker.isIncluded("exclude", Fields.ALL));
  }

  @Test
  public void testDefaultsInclude() {
    FieldInclusionChecker checker = FieldInclusionChecker.getForClass(Model.class);

    Assert.assertTrue(checker.isIncluded("include", Fields.SHORT));
    Assert.assertTrue(checker.isIncluded("include", Fields.LONG));
    Assert.assertFalse(checker.isIncluded("include", Fields.NONE));
    Assert.assertTrue(checker.isIncluded("include", Fields.ALL));
  }

  @Test
  public void testDefaultsIncludeNonDefault() {
    FieldInclusionChecker checker = FieldInclusionChecker.getForClass(Model.class);

    Assert.assertNull(checker.isIncluded("includeNonDefault", Fields.SHORT));
    Assert.assertNull(checker.isIncluded("includeNonDefault", Fields.LONG));
    Assert.assertFalse(checker.isIncluded("includeNonDefault", Fields.NONE));
    Assert.assertTrue(checker.isIncluded("includeNonDefault", Fields.ALL));
  }

  @Test
  public void testInclude() {
    FieldInclusionChecker checker = FieldInclusionChecker.getForClass(Model.class);

    Assert.assertTrue(checker.isIncluded("getter", new Fields("getter")));
  }

  @Test
  public void testPotentiallyIncluded() {
    FieldInclusionChecker checker = FieldInclusionChecker.getForClass(Model.class);

    List<String> shouldInclude = Arrays.asList(
      "field",
      "getter",
      "include",
      "includeNonDefault",
      "public void jetbrains.buildServer.server.rest.model.fields.FieldInclusionCheckerTest$Model.forgottenField()"
    );

    Set<String> toCheck = checker.getAllPotentiallyIncludedFields(new Fields(null));

    Assert.assertEquals(shouldInclude.size(), toCheck.size());
    for(String f : shouldInclude) {
      Assert.assertTrue(toCheck.contains(f));
    }
  }


  @FieldStrategySupported
  class Model {
    @FieldStrategy(name = "field")
    public int field;

    @FieldStrategy(name = "getter")
    public void getter() { }

    @FieldStrategy(name = "exclude", defaultForShort = FieldRule.EXCLUDE, defaultForLong = FieldRule.EXCLUDE)
    public void exclude() { }

    @FieldStrategy(name = "include", defaultForShort = FieldRule.INCLUDE, defaultForLong = FieldRule.INCLUDE)
    public void include() { }

    @FieldStrategy(name = "includeNonDefault", defaultForShort = FieldRule.INCLUDE_NON_DEFAULT, defaultForLong = FieldRule.INCLUDE_NON_DEFAULT)
    public void includeNonDefault() { }

    @XmlAttribute
    public void forgottenField() { }

    @XmlTransient
    public void transientField() { }
  }
}
