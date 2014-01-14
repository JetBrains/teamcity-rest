/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.build.OccurrencesSummary;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.11.13
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "testOccurrences")
@XmlType(name = "testOccurrences", propOrder = {"count", "href", "nextHref", "prevHref",
  "items"})
public class TestOccurrences extends OccurrencesSummary {
  @XmlElement(name = "testOccurrence") public List<TestOccurrence> items;
  @XmlAttribute public Integer count;
  @XmlAttribute(name = "href") public String href;
  @XmlAttribute(required = false) @Nullable public String nextHref;
  @XmlAttribute(required = false) @Nullable public String prevHref;

  public TestOccurrences() {
  }

  public TestOccurrences(@NotNull final List<STestRun> itemsP,
                         @Nullable final String shortHref,
                         @Nullable final PagerData pagerData,
                         @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    this(itemsP, null, null, null, null, null, null, shortHref, pagerData, fields, beanContext);
  }

  public TestOccurrences(@Nullable final List<STestRun> itemsP,
                         @Nullable final Integer count,
                         @Nullable final Integer passed,
                         @Nullable final Integer failed,
                         @Nullable final Integer newFailed,
                         @Nullable final Integer ignored,
                         @Nullable final Integer muted,
                         @Nullable final String shortHref,
                         @Nullable final PagerData pagerData, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    super(passed, failed, newFailed, ignored, muted, fields);
    if (itemsP != null) {
      items = ValueWithDefault.decideDefault(fields.isIncluded("testOccurrence"), new ValueWithDefault.Value<List<TestOccurrence>>() {
        @Nullable
        public List<TestOccurrence> get() {
          final List<STestRun> sortedItems = new ArrayList<STestRun>(itemsP);
          Collections.sort(sortedItems, STestRun.NEW_FIRST_NAME_COMPARATOR);
          final ArrayList<TestOccurrence> result = new ArrayList<TestOccurrence>(sortedItems.size());
          for (STestRun item : sortedItems) {
            result.add(new TestOccurrence(item, beanContext, fields.getNestedField("testOccurrence")));
          }
          return result;
        }
      });
      this.count = ValueWithDefault.decideDefault(fields.isIncluded("count", true), items.size());
    } else {
      this.count = ValueWithDefault.decideDefault(fields.isIncluded("count"), count);
    }

    this.href = ValueWithDefault.decide(fields.isIncluded("href"),
                                        shortHref != null ? beanContext.getApiUrlBuilder().transformRelativePath(shortHref) : null,
                                        null,
                                        !ValueWithDefault.isAllDefault(count, passed, failed, newFailed, ignored, muted));

    if (pagerData != null) {
      nextHref = pagerData.getNextHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null;
      prevHref = pagerData.getPrevHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null;
    }
  }

  @Override
  public boolean isDefault() {
    return ValueWithDefault.isAllDefault(count, href, items) && super.isDefault();
  }
}
