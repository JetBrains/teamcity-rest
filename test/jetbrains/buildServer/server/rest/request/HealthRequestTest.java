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

package jetbrains.buildServer.server.rest.request;

import java.util.Collections;
import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.server.rest.model.health.HealthItem;
import jetbrains.buildServer.serverSide.healthStatus.*;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class HealthRequestTest extends BaseFinderTest<HealthStatusItem> {
  private static final ItemCategory INFO_CATEGORY = new ItemCategory("test", "just test", ItemSeverity.INFO);
  private HealthRequest myRequest;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myRequest = new HealthRequest();
    myRequest.initForTests(BaseFinderTest.getBeanContext(myFixture));
    CompositeHealthStatusReportTestUtil.registerSimpleReporter("type", myFixture);
  }

  public void smoky_test_get_global_item() {
    createReport();

    final HealthItem item = myRequest.getSingleHealthItem("global:true", "healthCategory:$long,severity:$long");
    assertNotNull(item);
    assertEquals(ItemSeverity.INFO, item.getSeverity());
    assertNotNull(item.getHealthCategory());
    assertEquals("test", item.getHealthCategory().getId());
  }

  private void createReport() {
    myServer.registerExtension(HealthStatusReport.class, "test_report", new CompositeHealthStatusReportTestUtil.TestReport(INFO_CATEGORY) {
      @Override
      public void report(@NotNull final HealthStatusScope scope, @NotNull final HealthStatusItemConsumer resultConsumer) {
        resultConsumer.consumeGlobal(
          new HealthStatusItem("id", INFO_CATEGORY, Collections.emptyMap()));
      }
    });
  }
}