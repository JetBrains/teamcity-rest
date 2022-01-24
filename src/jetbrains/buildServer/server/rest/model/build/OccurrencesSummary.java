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

package jetbrains.buildServer.server.rest.model.build;

import javax.xml.bind.annotation.XmlAttribute;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 09.01.14
 */
//@XmlRootElement(name = "problemOccurencesSummary")
//@XmlType(name = "problemOccurencesSummary", propOrder = {"passed", "failed", "newFailed", "ignored", "muted", "total", "href"})
@SuppressWarnings("PublicField")
public class OccurrencesSummary implements DefaultValueAware {
  @XmlAttribute(name = "passed")
  public Integer passed;

  @XmlAttribute(name = "failed")
  public Integer failed;

  @XmlAttribute(name = "newFailed")
  public Integer newFailed;

  @XmlAttribute(name = "ignored")
  public Integer ignored;

  @XmlAttribute(name = "muted")
  public Integer muted;

  public OccurrencesSummary() {
  }

  public OccurrencesSummary(@Nullable final Integer passed,
                            @Nullable final Integer failed,
                            @Nullable final Integer newFailed,
                            @Nullable final Integer ignored,
                            @Nullable final Integer muted,
                            @NotNull Fields fields) {
    this.passed = ValueWithDefault.decideDefault(fields.isIncluded("passed"), passed);
    this.failed = ValueWithDefault.decideDefault(fields.isIncluded("failed"), failed);
    this.newFailed = ValueWithDefault.decideDefault(fields.isIncluded("newFailed"), newFailed);
    this.ignored = ValueWithDefault.decideDefault(fields.isIncluded("ignored"), ignored);
    this.muted = ValueWithDefault.decideDefault(fields.isIncluded("muted"), muted);
  }

  public boolean isDefault() {
    return ValueWithDefault.isAllDefault(passed, failed, newFailed, ignored, muted);
  }
}