/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import java.util.Comparator;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "problems")
public class Problems implements DefaultValueAware {
  @XmlElement(name = "problem") public List<Problem> items;
  @XmlAttribute public Integer count;
  @XmlAttribute(required = false) @Nullable public String nextHref;
  @XmlAttribute(required = false) @Nullable public String prevHref;

  public Problems() {
  }

  public Problems(@NotNull final List<ProblemWrapper> itemsP,
                  @Nullable final PagerData pagerData,
                  @NotNull final Fields fields,
                  @NotNull final BeanContext beanContext) {
    items = ValueWithDefault.decideDefault(fields.isIncluded("problem", false),new ValueWithDefault.Value<List<Problem>>() {
      public List<Problem> get() {
        final List<ProblemWrapper> sortedItems = new ArrayList<ProblemWrapper>(itemsP);
        Collections.sort(sortedItems, new Comparator<ProblemWrapper>() {
          public int compare(final ProblemWrapper o1, final ProblemWrapper o2) {
            return o1.getId().compareTo(o2.getId());
          }
        });
        final Fields nestedField = fields.getNestedField("problem");
        return CollectionsUtil.convertCollection(sortedItems, new Converter<Problem, ProblemWrapper>() {
          public Problem createFrom(@NotNull final ProblemWrapper source) {
            return new Problem(source, nestedField, beanContext);
          }
        });
      }
    });
    if (pagerData != null) {
      nextHref = ValueWithDefault.decideDefault(fields.isIncluded("nextHref"),
                                                pagerData.getNextHref() == null ? null : beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()));
      prevHref = ValueWithDefault.decideDefault(fields.isIncluded("prevHref"),
                                                pagerData.getPrevHref() == null ? null : beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()));
    }
    count = ValueWithDefault.decideDefault(fields.isIncluded("count", true), itemsP.size());
  }

  public boolean isDefault() {
    return ValueWithDefault.isAllDefault(items, count);
  }
}
