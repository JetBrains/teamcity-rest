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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.Used;
import jetbrains.buildServer.server.rest.data.problem.TestCountersData;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@XmlType(name = "testCounters", propOrder = {
  "ignored",
  "failed",
  "muted",
  "success",
  "all",
  "newFailed",
  "duration"
})
@XmlRootElement(name = "testCounters")
@ModelDescription("Represents a test results counter (how many times this test was successful/failed/muted/ignored).")
public class TestCounters implements DefaultValueAware {
  @NotNull
  private final TestCountersData myTestCountersData;
  @NotNull
  private Fields myFields = Fields.NONE;

  @Used("javax.xml")
  public TestCounters() {
    myTestCountersData = new TestCountersData();
  }

  public TestCounters(@NotNull TestCountersData testCountersData) {
    myTestCountersData = testCountersData;
  }

  public TestCounters(@NotNull final Fields fields, @NotNull TestCountersData testCountersData) {
    myFields = fields;
    myTestCountersData = testCountersData;
  }

  @XmlAttribute(name = "failed")
  @Nullable
  public Integer getFailed() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("failed", false, true), myTestCountersData.getFailed());
  }

  @XmlAttribute(name = "muted")
  @Nullable
  public Integer getMuted() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("muted", false, true), myTestCountersData.getMuted());
  }

  @XmlAttribute(name = "success")
  @Nullable
  public Integer getSuccess() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("success", false, true), myTestCountersData.getPassed());
  }

  @XmlAttribute(name = "all")
  @Nullable
  public Integer getAll() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("all"), myTestCountersData.getCount());
  }

  @XmlAttribute(name = "ignored")
  @Nullable
  public Integer getIgnored() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("ignored", false, true), myTestCountersData.getIgnored());
  }

  @XmlAttribute(name = "newFailed")
  @Nullable
  public Integer getNewFailed() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("newFailed", false, true), myTestCountersData.getNewFailed());
  }

  @XmlAttribute(name = "duration")
  @Nullable
  public Long getDuration() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("duration", false, true), myTestCountersData.getDuration());
  }

  @Override
  public boolean isDefault() {
    return ValueWithDefault.isAllDefault(
      myTestCountersData.getCount(),
      myTestCountersData.getPassed(),
      myTestCountersData.getFailed(),
      myTestCountersData.getDuration(),
      myTestCountersData.getMuted(),
      myTestCountersData.getIgnored(),
      myTestCountersData.getNewFailed()
    );
  }
}
