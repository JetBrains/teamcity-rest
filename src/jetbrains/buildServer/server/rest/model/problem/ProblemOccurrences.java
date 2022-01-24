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

package jetbrains.buildServer.server.rest.model.problem;

import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.build.OccurrencesSummary;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Yegor.Yarko
 *         Date: 18.11.13
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "problemOccurrences")
@XmlType(name = "problemOccurrences", propOrder = {"count", "href", "nextHref", "prevHref",
  "items"})
@ModelBaseType(ObjectType.PAGINATED)
public class ProblemOccurrences extends OccurrencesSummary {
  @XmlElement(name = "problemOccurrence") public List<ProblemOccurrence> items;
  @XmlAttribute public Integer count;
  @XmlAttribute(name = "href") public String href;
  @XmlAttribute(required = false) @Nullable public String nextHref;
  @XmlAttribute(required = false) @Nullable public String prevHref;

  private boolean isDefault;

  public ProblemOccurrences() {
  }

  public ProblemOccurrences(@NotNull final List<BuildProblem> itemsP,
                            @Nullable final String shortHref,
                            @Nullable final PagerData pagerData,
                            @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    this(itemsP, null, null, null, null, null, null, shortHref, pagerData, fields, beanContext);
  }

  public ProblemOccurrences(@Nullable final List<BuildProblem> itemsP,
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
      items = ValueWithDefault.decideDefault(fields.isIncluded("problemOccurrence", false), new ValueWithDefault.Value<List<ProblemOccurrence>>() {
        @Nullable
        public List<ProblemOccurrence> get() {
          final ArrayList<ProblemOccurrence> result = new ArrayList<ProblemOccurrence>(itemsP.size());
          Fields occurrenceFields = fields.getNestedField("problemOccurrence");
          for (BuildProblem item : itemsP) {
            result.add(new ProblemOccurrence(item, beanContext, occurrenceFields));
          }
          return result;
        }
      });
      this.count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count", true), itemsP.size());
    } else {
      this.count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), count);
    }

    this.href = shortHref == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().transformRelativePath(shortHref));

    if (pagerData != null) {
      nextHref = pagerData.getNextHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null;
      prevHref = pagerData.getPrevHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null;
    }

    if (!super.isDefault()) {
      isDefault = false;
    } else if (itemsP != null && itemsP.isEmpty()) {
      isDefault = true;
    } else if (count != null && ValueWithDefault.isDefault(count)) {
      isDefault = true;
    } else {
      isDefault = ValueWithDefault.isAllDefault(this.count, this.href, this.items);
    }
  }

  @Override
  public boolean isDefault() {
    return isDefault;
  }

}
