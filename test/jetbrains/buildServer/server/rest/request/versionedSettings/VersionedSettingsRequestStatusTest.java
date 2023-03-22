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

package jetbrains.buildServer.server.rest.request.versionedSettings;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsError;
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsStatus;
import jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsStatusTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class VersionedSettingsRequestStatusTest extends VersionedSettingsRequestBaseTestCase {

  @DataProvider(name = "statusFields")
  private Object[][] getSuccessStatusFields() {
    return new Object[][] {
      {
        null,
        new StatusFields(true, true, true, true, new StatusErrorFields(true, true, true, true))
      },
      {
        "message",
        new StatusFields(true, false, false, false, null)
      },
      {
        "message,timestamp",
        new StatusFields(true, false, true, false, null)
      },
      {
        "type",
        new StatusFields(false, true, false, false, null)
      },
      {
        "versionedSettingsError,missingContextParameters",
        new StatusFields(false, false, false, true, new StatusErrorFields(true, true, true, true))
      },
      {
        "type,message,missingContextParameters",
        new StatusFields(true, true, false, true, null)
      },
      {
        "type,message,versionedSettingsError(type)",
        new StatusFields(true, true, false, false, new StatusErrorFields(false, true, false, false))
      },
      {
        "type,message,versionedSettingsError(type,message,file)",
        new StatusFields(true, true, false, false, new StatusErrorFields(true, true, false, true))
      },
      {
        "versionedSettingsError(stackTraceLines)",
        new StatusFields(false, false, false, false, new StatusErrorFields(false, false, true, false))
      }
    };
  }

  @Test(dataProvider = "statusFields")
  public void testGetSuccessStatus(@Nullable String fields, @NotNull StatusFields statusFields) {
    VersionedSettingsStatusTracker statusTracker = myFixture.getSingletonService(VersionedSettingsStatusTracker.class);
    statusTracker.setStatus(Collections.singleton(myProject), new jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsStatus(
      new Date(1000),
      jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsStatus.Type.INFO,
      "Some sucessfull status"
    ));

    VersionedSettingsStatus status = myRequest.getStatus(myProject.getExternalId(), fields);
    assertNotNull(status);
    assertEquals(status.getMessage(), statusFields.isMessagePresent ? "Some sucessfull status" : null);
    assertEquals(status.getType(), statusFields.isTypePresent ? VersionedSettingsStatus.StatusType.info : null);
    assertEquals(status.getTimestamp(), statusFields.isTimestampPresent ? new Date(1000).toString() : null);
    assertNull(status.getErrors());
    assertNull(status.getMissingContextParameters());
  }

  @Test(dataProvider = "statusFields")
  public void testMissingContextParametersStatus(@Nullable String fields, @NotNull StatusFields statusFields) {
    VersionedSettingsStatusTracker statusTracker = myFixture.getSingletonService(VersionedSettingsStatusTracker.class);
    jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsStatus originalStatus =
      new jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsStatus(
        new Date(1000),
        jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsStatus.Type.WARN,
        "Some DSL params are missing"
      );
    originalStatus.setRequiredContextParameters(Arrays.asList("param1", "param2"));
    statusTracker.setStatus(Collections.singleton(myProject), originalStatus);

    VersionedSettingsStatus status = myRequest.getStatus(myProject.getExternalId(), fields);
    assertNotNull(status);
    assertEquals(status.getMessage(), statusFields.isMessagePresent ? "Some DSL params are missing" : null);
    assertEquals(status.getType(), statusFields.isTypePresent ? VersionedSettingsStatus.StatusType.warn : null);
    assertEquals(status.getTimestamp(), statusFields.isTimestampPresent ? new Date(1000).toString() : null);
    if (statusFields.isMissingParamsPresent) {
      assertContains(status.getMissingContextParameters(), "param1", "param2");
    } else {
      assertNull(status.getMissingContextParameters());
    }
    assertNull(status.getErrors());
  }

  @Test(dataProvider = "statusFields")
  public void testErrorStatus(@Nullable String fields, @NotNull StatusFields statusFields) {
    VersionedSettingsStatusTracker statusTracker = myFixture.getSingletonService(VersionedSettingsStatusTracker.class);
    jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsStatus originalStatus =
      new jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsStatus(
        new Date(1000),
        jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsStatus.Type.WARN,
        "Some compilation error"
      );
    jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsError originalVersionedSettingsError =
      new jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsError(
        jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsError.Type.COMPILATION_ERROR,
        myProject.getExternalId(),
        "my_source",
        "my_location_file",
        "my_error_message"
      );
    List<String> stackTraceLines = Arrays.asList("stack_trace_line_1", "stack_trace_line_2");
    originalVersionedSettingsError.setStackTrace(stackTraceLines);
    originalStatus.setConfigErrors(Collections.singletonList(originalVersionedSettingsError));
    statusTracker.setStatus(Collections.singleton(myProject), originalStatus);

    VersionedSettingsStatus status = myRequest.getStatus(myProject.getExternalId(), fields);
    assertNotNull(status);
    assertEquals(status.getMessage(), statusFields.isMessagePresent ? "Some compilation error" : null);
    assertEquals(status.getType(), statusFields.isTypePresent ? VersionedSettingsStatus.StatusType.warn : null);
    assertEquals(status.getTimestamp(), statusFields.isTimestampPresent ? new Date(1000).toString() : null);
    assertNull(status.getMissingContextParameters());
    if (statusFields.statusErrorFields != null) {
      VersionedSettingsError versionedSettingsError = status.getErrors().get(0);
      assertEquals(versionedSettingsError.getMessage(), statusFields.statusErrorFields.isMessagePresent ? "my_error_message" : null);
      assertEquals(versionedSettingsError.getType(), statusFields.statusErrorFields.isTypePresent ? "Compilation error" : null);
      assertEquals(versionedSettingsError.getFile(), statusFields.statusErrorFields.isFilePresent ? "my_source my_location_file" : null);
      if (statusFields.statusErrorFields.isStackLinesPresent) {
        assertContains(versionedSettingsError.getStackTraceLines(), "stack_trace_line_1", "stack_trace_line_2");
      } else {
        assertNull(versionedSettingsError.getStackTraceLines());
      }
    } else {
      assertNull(status.getErrors());
    }
  }


  private static class StatusFields {
    boolean isMessagePresent;
    boolean isTypePresent;
    boolean isTimestampPresent;
    boolean isMissingParamsPresent;
    StatusErrorFields statusErrorFields;

    public StatusFields(boolean isMessagePresent,
                        boolean isTypePresent,
                        boolean isTimestampPresent,
                        boolean isMissingParamsPresent,
                        StatusErrorFields statusErrorFields) {
      this.isMessagePresent = isMessagePresent;
      this.isTimestampPresent = isTimestampPresent;
      this.isTypePresent = isTypePresent;
      this.isMissingParamsPresent = isMissingParamsPresent;
      this.statusErrorFields = statusErrorFields;
    }
  }

  private static class StatusErrorFields {
    boolean isMessagePresent;
    boolean isTypePresent;
    boolean isStackLinesPresent;
    boolean isFilePresent;

    public StatusErrorFields(boolean isMessagePresent, boolean isTypePresent, boolean isStackLinesPresent, boolean isFilePresent) {
      this.isMessagePresent = isMessagePresent;
      this.isTypePresent = isTypePresent;
      this.isStackLinesPresent = isStackLinesPresent;
      this.isFilePresent = isFilePresent;
    }
  }

}