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

import java.util.function.Supplier;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.Used;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.server.rest.model.problem.TestOccurrences.NULL_SUPPLIER;

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
  private Supplier<Integer> myAll = NULL_SUPPLIER;
  @NotNull
  private Supplier<Integer> myMuted = NULL_SUPPLIER;
  @NotNull
  private Supplier<Integer> mySuccess = NULL_SUPPLIER;
  @NotNull
  private Supplier<Integer> myFailed = NULL_SUPPLIER;
  @NotNull
  private Supplier<Integer> myIgnored = NULL_SUPPLIER;
  @NotNull
  private Supplier<Integer> myNewFailed = NULL_SUPPLIER;
  @NotNull
  private Supplier<Integer> myDuration = NULL_SUPPLIER;

  @NotNull
  private Fields myFields = Fields.NONE;

  @Used("javax.xml")
  public TestCounters() {
  }

  /**
   * @implNote DO NOT FORGET to check {@link jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder#makeCountersFromItems} in case of any changes to this class.
   */
  public TestCounters(
    @NotNull final Fields fields,
    @NotNull final Supplier<Integer> all,
    @NotNull final Supplier<Integer> muted,
    @NotNull final Supplier<Integer> success,
    @NotNull final Supplier<Integer> failed,
    @NotNull final Supplier<Integer> ignored,
    @NotNull final Supplier<Integer> newFailed,
    @NotNull final Supplier<Integer> totalDuration
  ) {
    myFields = fields;
    myAll = all;
    myMuted = muted;
    mySuccess = success;
    myFailed = failed;
    myIgnored = ignored;
    myNewFailed = newFailed;
    myDuration = totalDuration;
  }

  @XmlAttribute(name = "failed")
  @Nullable
  public Integer getFailed() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("failed", false, true), myFailed.get());
  }

  @XmlAttribute(name = "muted")
  @Nullable
  public Integer getMuted() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("muted", false, true), myMuted.get());
  }

  @XmlAttribute(name = "success")
  @Nullable
  public Integer getSuccess() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("success", false, true), mySuccess.get());
  }

  @XmlAttribute(name = "all")
  @Nullable
  public Integer getAll() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("all"), myAll.get());
  }

  @XmlAttribute(name = "ignored")
  @Nullable
  public Integer getIgnored() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("ignored", false, true), myIgnored.get());
  }

  @XmlAttribute(name = "newFailed")
  @Nullable
  public Integer getNewFailed() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("newFailed", false, true), myNewFailed.get());
  }

  @XmlAttribute(name = "duration")
  @Nullable
  public Integer getDuration() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("duration", false, true), myDuration.get());
  }

  @Override
  public boolean isDefault() {
    return myAll == NULL_SUPPLIER &&
           myFailed == NULL_SUPPLIER &&
           myIgnored == NULL_SUPPLIER &&
           myMuted == NULL_SUPPLIER &&
           myNewFailed == NULL_SUPPLIER &&
           mySuccess == NULL_SUPPLIER &&
           myDuration == NULL_SUPPLIER;
  }
}
