/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.problem;

import java.util.Collection;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlType(name = "testCounters", propOrder = {
  "ignored",
  "failed",
  "muted",
  "success",
  "all"
})
@XmlRootElement(name = "testCounters")
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_DESCRIPTION,
    value = "Represents a test results counter (how many times this test was successful/failed/muted/ignored)."))
public class TestCounters {
  @Nullable
  @XmlAttribute(name = "ignored")
  private Integer ignored;
  @Nullable
  @XmlAttribute(name = "failed")
  private Integer failed;
  @Nullable
  @XmlAttribute(name = "muted")
  private Integer muted;
  @Nullable
  @XmlAttribute(name = "success")
  private Integer success;
  @Nullable
  @XmlAttribute(name = "all")
  private Integer all;

  @Used("javax.xml")
  public TestCounters() {
  }

  public TestCounters(@Nullable final Collection<STestRun> testRuns, @NotNull final Fields fields) {
    if (testRuns != null) {
      all = ValueWithDefault.decideDefault(fields.isIncluded("all"), testRuns::size);
      final boolean mutedIncluded = fields.isIncluded("muted", false, true);
      final boolean successIncluded = fields.isIncluded("success", false, true);
      final boolean failedIncluded = fields.isIncluded("failed", false, true);
      final boolean ignoredIncluded = fields.isIncluded("ignored", false, true);
      failed = failedIncluded ? 0 : null;
      muted = mutedIncluded ? 0 : null;
      success = successIncluded ? 0 : null;
      ignored = ignoredIncluded ? 0 : null;
      testRuns.forEach(sTestRun -> {
        if (mutedIncluded && sTestRun.isMuted()) {
          muted++;
        }
        if (ignoredIncluded && sTestRun.isIgnored()) {
          ignored++;
        }
        final Status status = sTestRun.getStatus();
        if (successIncluded && status.isSuccessful()) {
          success++;
        }
        if (failedIncluded && status.isFailed() && !sTestRun.isMuted()) {
          failed++;
        }
      });
    }
  }

  @Nullable
  public Integer getFailed() {
    return failed;
  }

  @Nullable
  public Integer getMuted() {
    return muted;
  }

  @Nullable
  public Integer getSuccess() {
    return success;
  }

  @Nullable
  public Integer getAll() {
    return all;
  }

  @Nullable
  public Integer getIgnored() {
    return ignored;
  }
}
