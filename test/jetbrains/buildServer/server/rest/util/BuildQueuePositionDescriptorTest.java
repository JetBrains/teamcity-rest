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

package jetbrains.buildServer.server.rest.util;

import java.util.Set;
import org.junit.Assert;
import org.testng.annotations.Test;

@Test
public class BuildQueuePositionDescriptorTest {
  public void should_parse_first_literal() {
    BuildQueuePositionDescriptor descriptor = BuildQueuePositionDescriptor.parse("first");

    Assert.assertEquals(BuildQueuePositionDescriptor.FIRST, descriptor);
  }

  public void should_parse_last_literal() {
    BuildQueuePositionDescriptor descriptor = BuildQueuePositionDescriptor.parse("last");

    Assert.assertEquals(BuildQueuePositionDescriptor.LAST, descriptor);
  }

  public void should_parse_1_literal() {
    BuildQueuePositionDescriptor descriptor = BuildQueuePositionDescriptor.parse("1");

    Assert.assertEquals(BuildQueuePositionDescriptor.FIRST, descriptor);
  }

  public void should_not_parse_other_literals() {
    String[] otherLiterals = new String[] {
      "2", "0", "1,2,3,4", "perviy", "-1"
    };

    for(String literal : otherLiterals) {
      BuildQueuePositionDescriptor descriptor = BuildQueuePositionDescriptor.parse(literal);
      Assert.assertNull(descriptor);
    }
  }

  public void should_parse_after_literal() {
    BuildQueuePositionDescriptor descriptor = BuildQueuePositionDescriptor.parse("after:1,2,3");

    long[] vals = new long[] { 1, 2, 3};

    Set<Long> parsedVals = descriptor.getBuildIds();
    for (long val : vals) {
      Assert.assertTrue(parsedVals.contains(val));
    }
  }

  public void should_not_parse_empty_after() {
    BuildQueuePositionDescriptor descriptor = BuildQueuePositionDescriptor.parse("after:");

    Assert.assertNull(descriptor);
  }
}
