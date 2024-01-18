/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

import java.util.*;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrence;
import jetbrains.buildServer.server.rest.util.fieldInclusion.FieldInclusionChecker;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestOccurrenceFieldsTest {
  private FieldInclusionChecker myChecker;

  @BeforeMethod
  public void setUp() throws Exception {
    myChecker = FieldInclusionChecker.getForClass(TestOccurrence.class);
  }

  @Test
  public void testShort() {
    Fields defaults = new Fields("$short");
    Set<String> result = myChecker.getAllPotentiallyIncludedFields(defaults);

    checkSame(
      Arrays.asList(
        "id",
        "name",
        "status",
        "ignored",
        "duration",
        "muted",
        "currentlyMuted",
        "currentlyInvestigated",
        "href"
      ),
      result
    );
  }

  @Test
  public void testLong() {
    Fields defaults = new Fields("$long");
    Set<String> result = myChecker.getAllPotentiallyIncludedFields(defaults);

    checkSame(
      Arrays.asList(
        "id",
        "name",
        "status",
        "ignored",
        "duration",
        "muted",
        "currentlyMuted",
        "currentlyInvestigated",
        "href",
        "nextFixed",
        "firstFailed",
        "build",
        "mute",
        "test",
        "details",
        "ignoreDetails"
      ),
      result
    );
  }

  @Test
  public void testAll() {
    Fields defaults = Fields.ALL;
    Set<String> result = myChecker.getAllPotentiallyIncludedFields(defaults);

    checkSame(Arrays.asList(
      "id",
      "name",
      "status",
      "ignored",
      "duration",
      "runOrder",
      "newFailure",
      "muted",
      "currentlyMuted",
      "currentlyInvestigated",
      "href",
      "ignoreDetails",
      "details",
      "test",
      "mute",
      "build",
      "firstFailed",
      "nextFixed",
      "invocations",
      "metadata",
      "logAnchor"
    ), result);
  }

  @Test
  public void testNone() {
    Fields defaults = Fields.NONE;
    Set<String> result = myChecker.getAllPotentiallyIncludedFields(defaults);

    checkSame(Collections.emptySet(), result);
  }

  private void checkSame(@NotNull Collection<String> expected, @NotNull Set<String> actual) {
    HashSet<String> leftExpected = new HashSet<>(expected);
    HashSet<String> leftActual = new HashSet<>(actual);
    for(String v : expected) {
      Assert.assertTrue("Must contain: '" + v + "'", actual.contains(v));
      leftExpected.remove(v);
      leftActual.remove(v);
    }

    Assert.assertEquals(
      "(" + String.join(",", leftExpected) + ") were expected but were not given.",
      0, leftExpected.size()
    );

    Assert.assertEquals(
      "(" + String.join(",", leftActual) + ") were given but were not expected.",
      0, leftActual.size()
    );
  }
}