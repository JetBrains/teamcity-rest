/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.build.OccurrencesSummary;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.ShortStatistics;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 * Date: 16.11.13
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "testOccurrences")
@XmlType(name = "testOccurrences", propOrder = {
  "count",
  "href",
  "nextHref",
  "prevHref",
  "items",
  "testCounters"
})
@ModelBaseType(ObjectType.PAGINATED)
public class TestOccurrences extends OccurrencesSummary {
  public static final Supplier<Integer> NULL_SUPPLIER = () -> null;

  @NotNull
  private Fields myFields = Fields.NONE;
  @Nullable
  private TestCounters myTestCounters;

  @XmlElement(name = "testOccurrence")
  @Nullable
  public List<TestOccurrence> items;

  @XmlAttribute(name = "href")
  @Nullable
  public String href;

  @XmlAttribute
  @Nullable
  public String nextHref;

  @XmlAttribute
  @Nullable
  public String prevHref;

  private Supplier<Integer> myCount = NULL_SUPPLIER;
  private Supplier<Integer> myFailed = NULL_SUPPLIER;
  private Supplier<Integer> myMuted = NULL_SUPPLIER;
  private Supplier<Integer> myPassed = NULL_SUPPLIER;
  private Supplier<Integer> myIgnored = NULL_SUPPLIER;
  private Supplier<Integer> myNewFailed = NULL_SUPPLIER;
  private Supplier<Integer> myDuration = NULL_SUPPLIER;

  public TestOccurrences() {
  }

  public TestOccurrences(@Nullable final List<STestRun> items,
                         @Nullable final ShortStatistics buildStatistics,
                         @Nullable final String shortHref,
                         @Nullable final PagerData pagerData,
                         @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    myFields = fields;

    if(items != null) {
      this.items = ValueWithDefault.decideDefault(isTestOccurrenceIncluded(fields), (ValueWithDefault.Value<List<TestOccurrence>>)() -> {
        final List<STestRun> sortedItems = new ArrayList<>(items);
        if (TeamCityProperties.getBoolean("rest.beans.testOccurrences.sortByNameAndNew")) {
          sortedItems.sort(STestRun.NEW_FIRST_NAME_COMPARATOR); //if we are to support customizable order, this should be done in the TestOccurrenceFinder
        }
        final ArrayList<TestOccurrence> result = new ArrayList<>(sortedItems.size());
        for (STestRun item : sortedItems) {
          result.add(new TestOccurrence(item, beanContext, fields.getNestedField("testOccurrence")));
        }
        return result;
      });
    }

    if(buildStatistics != null) {
      myTestCounters = makeCountersFromBuildStatistics(buildStatistics, items);
    }

    if(items != null) {
      myTestCounters = makeCountersFromItems(items);
    }

    href = shortHref == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().transformRelativePath(shortHref));

    if (pagerData != null) {
      nextHref = pagerData.getNextHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null;
      prevHref = pagerData.getPrevHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null;
    }
  }

  private TestCounters makeCountersFromItems(@NotNull final List<STestRun> testRuns) {
    // We want lazy calculations of counters and do not want to duplicate that in TestOccurrences
    // To ensure that we do calculations once lets check if the field is requested for in TestOccurrences or in TestCounters
    Fields countersFields = myFields.getNestedField("testCounters");

    boolean failedIncluded  = myFields.isIncluded("failed", false, true)  || BooleanUtils.isTrue(countersFields.isIncluded("failed"));
    boolean mutedIncluded   = myFields.isIncluded("muted", false, true)   || BooleanUtils.isTrue(countersFields.isIncluded("muted"));
    boolean successIncluded = myFields.isIncluded("passed", false, true)  || BooleanUtils.isTrue(countersFields.isIncluded("success"));
    boolean ignoredIncluded = myFields.isIncluded("ignored", false, true) || BooleanUtils.isTrue(countersFields.isIncluded("ignored"));
    boolean newFailureIncluded = myFields.isIncluded("newFailed", false, true) || countersFields.isIncluded("newFailed", false, true);
    boolean durationIncluded = countersFields.isIncluded("duration", false, true);

    Integer[] failed = new Integer[] {0};
    Integer[] muted = new Integer[] {0};
    Integer[] success = new Integer[] {0};
    Integer[] ignored = new Integer[] {0};
    Integer[] newFailure = new Integer[] {0};
    Integer[] duration = new Integer[] {0};
    for(STestRun testRun : testRuns) {
      if (mutedIncluded && testRun.isMuted()) {
        muted[0]++;
      }
      if (ignoredIncluded && testRun.isIgnored()) {
        ignored[0]++;
      }
      final Status status = testRun.getStatus();
      if (successIncluded && status.isSuccessful()) {
        success[0]++;
      }
      if (failedIncluded && status.isFailed() && !testRun.isMuted()) {
        failed[0]++;
      }
      if (newFailureIncluded && testRun.isNewFailure() && !testRun.isMuted()) {
        newFailure[0]++;
      }
      if(durationIncluded) {
        duration[0] += testRun.getDuration();
      }
    }

    myCount = testRuns::size;

    if(failedIncluded)
      myFailed = () -> failed[0];

    if(mutedIncluded)
      myMuted = () -> muted[0];

    if(successIncluded)
      myPassed = () -> success[0];

    if(ignoredIncluded)
      myIgnored = () -> ignored[0];

    if(newFailureIncluded)
      myNewFailed = () -> newFailure[0];

    if(durationIncluded)
      myDuration = () -> duration[0];

    return makeCounters();
  }

  private TestCounters makeCountersFromBuildStatistics(@NotNull final ShortStatistics statistics, @Nullable List<STestRun> items) {
    myCount     = () -> statistics.getAllTestCount();
    myIgnored   = () -> statistics.getIgnoredTestCount();
    myPassed    = () -> statistics.getPassedTestCount();
    myFailed    = () -> statistics.getFailedTestCount();
    myNewFailed = () -> statistics.getNewFailedCount();
    myMuted     = () -> statistics.getMutedTestsCount();

    // There is no information about total duration in ShortStatistics
    if(items != null) {
      Integer[] duration = new Integer[] {0};
      items.forEach(tr -> duration[0] += tr.getDuration());
      myDuration = () -> duration[0];
    }

    return makeCounters();
  }

  private TestCounters makeCounters() {
    return new TestCounters(
      myFields.getNestedField("testCounters"),
      myCount,
      myMuted,
      myPassed,
      myFailed,
      myIgnored,
      myNewFailed,
      myDuration
    );
  }

  @Nullable
  public static Boolean isTestOccurrenceIncluded(@NotNull final Fields fields) {
    return fields.isIncluded("testOccurrence", false);
  }

  @Override
  public boolean isDefault() {
    return ValueWithDefault.isAllDefault(href, items, myTestCounters) &&
           myCount == NULL_SUPPLIER &&
           myPassed == NULL_SUPPLIER &&
           myFailed == NULL_SUPPLIER &&
           myNewFailed == NULL_SUPPLIER &&
           myIgnored == NULL_SUPPLIER &&
           myMuted == NULL_SUPPLIER;
  }

  @XmlElement(name = "testCounters")
  @Nullable
  public TestCounters getTestCounters() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("testCounters", false, false), myTestCounters);
  }

  @XmlAttribute(name = "count")
  @Nullable
  public Integer getCount() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("count", true), myCount::get);
  }

  @XmlAttribute(name = "passed")
  @Nullable
  public Integer getPassed() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("passed"), myPassed::get);
  }

  @XmlAttribute(name = "failed")
  @Nullable
  public Integer getFailed() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("failed"), myFailed::get);
  }

  @XmlAttribute(name = "newFailed")
  @Nullable
  public Integer getNewFailed() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("newFailed"), myNewFailed::get);
  }

  @XmlAttribute(name = "ignored")
  @Nullable
  public Integer getIgnored() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("ignored"), myIgnored::get);
  }

  @XmlAttribute(name = "muted")
  @Nullable
  public Integer getMuted() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("muted"), myMuted::get);
  }
}
