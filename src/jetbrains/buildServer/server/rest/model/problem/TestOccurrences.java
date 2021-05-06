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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.problem.TestCountersData;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
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
public class TestOccurrences {
  @NotNull
  private Fields myFields = Fields.NONE;
  @NotNull
  private TestCountersData myTestCountersData = new TestCountersData();

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

  public TestOccurrences() { }

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
      myTestCountersData = new TestCountersData(buildStatistics);
    } else if(items != null) {
      makeCountersFromItems(items);
    }

    href = shortHref == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().transformRelativePath(shortHref));

    if (pagerData != null) {
      nextHref = pagerData.getNextHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null;
      prevHref = pagerData.getPrevHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null;
    }
  }

  private void makeCountersFromItems(@NotNull final List<STestRun> testRuns) {
    // We want lazy calculations of counters and do not want to duplicate that in TestCounters
    // To ensure that we do calculations once lets check if the field is requested in TestOccurrences or in TestCounters
    Fields countersFields = myFields.getNestedField("testCounters");

    boolean failedIncluded  = myFields.isIncluded("failed", false, true)  || BooleanUtils.isTrue(countersFields.isIncluded("failed"));
    boolean mutedIncluded   = myFields.isIncluded("muted", false, true)   || BooleanUtils.isTrue(countersFields.isIncluded("muted"));
    boolean successIncluded = myFields.isIncluded("passed", false, true)  || BooleanUtils.isTrue(countersFields.isIncluded("success"));
    boolean ignoredIncluded = myFields.isIncluded("ignored", false, true) || BooleanUtils.isTrue(countersFields.isIncluded("ignored"));
    boolean newFailureIncluded = myFields.isIncluded("newFailed", false, true) || countersFields.isIncluded("newFailed", false, true);
    boolean durationIncluded = countersFields.isIncluded("duration", false, true);

    myTestCountersData = new TestCountersData(testRuns, successIncluded, failedIncluded, mutedIncluded, ignoredIncluded, newFailureIncluded, durationIncluded);
  }

  @Nullable
  public static Boolean isTestOccurrenceIncluded(@NotNull final Fields fields) {
    return fields.isIncluded("testOccurrence", false);
  }

  /**
   * Checks whether TestOccurences instance could be constructed without items given ShortStatistics and requested fields.
   * @return true if ShortStatistics is enough for construction, false otherwise.
   */
  public static boolean isShortStatisticsEnoughForConstruction(@NotNull final String fieldsText) {
    Fields fields = new Fields(fieldsText);
    boolean needsActualOccurrence = fields.isIncluded("testOccurrence", false, false);
    return !needsActualOccurrence;
  }

  public boolean isDefault() {
    return ValueWithDefault.isAllDefault(href, items, new TestCounters(myTestCountersData));
  }

  @XmlElement(name = "testCounters")
  @Nullable
  public TestCounters getTestCounters() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("testCounters", false, false),
      new TestCounters(myFields.getNestedField("testCounters"), myTestCountersData)
    );
  }

  @XmlAttribute(name = "count")
  @Nullable
  public Integer getCount() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("count", true), myTestCountersData.getCount());
  }

  @XmlAttribute(name = "passed")
  @Nullable
  public Integer getPassed() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("passed"), myTestCountersData.getPassed());
  }

  @XmlAttribute(name = "failed")
  @Nullable
  public Integer getFailed() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("failed"), myTestCountersData.getFailed());
  }

  @XmlAttribute(name = "newFailed")
  @Nullable
  public Integer getNewFailed() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("newFailed"), myTestCountersData.getNewFailed());
  }

  @XmlAttribute(name = "ignored")
  @Nullable
  public Integer getIgnored() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("ignored"), myTestCountersData.getIgnored());
  }

  @XmlAttribute(name = "muted")
  @Nullable
  public Integer getMuted() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("muted"), myTestCountersData.getMuted());
  }
}
