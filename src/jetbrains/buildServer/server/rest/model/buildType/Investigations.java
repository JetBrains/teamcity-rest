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

package jetbrains.buildServer.server.rest.model.buildType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "investigations")
public class Investigations implements DefaultValueAware {
  @XmlAttribute public Integer count;
  @XmlAttribute(required = false) @Nullable public String nextHref;
  @XmlAttribute(required = false) @Nullable public String prevHref;
  @XmlAttribute(name = "href") public String href;

  @XmlElement(name = "investigation") public List<Investigation> items;

  private boolean isDefault;

  public Investigations() {
  }

  public Investigations(@Nullable final Collection<InvestigationWrapper> itemsP,
                        @Nullable final PagerData pagerData, @NotNull final Fields fields,
                        @NotNull final BeanContext beanContext) {
    items = itemsP == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("investigation", false), new ValueWithDefault.Value<List<Investigation>>() {
      public List<Investigation> get() {
        final ArrayList<Investigation> result = new ArrayList<Investigation>(itemsP.size());
        for (InvestigationWrapper item : itemsP) {
          result.add(new Investigation(item, fields.getNestedField("investigation", Fields.NONE, Fields.LONG), beanContext));
        }
        return result;
      }
    });

    count = itemsP == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("count", true), itemsP.size());

    if (pagerData != null) {
      href = ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getHref()));
      nextHref = pagerData.getNextHref() == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("nextHref"),
                                                                                         beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()));
      prevHref = pagerData.getPrevHref() == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("prevHref"),
                                                                                         beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()));
    }

    if (itemsP != null && itemsP.isEmpty()) {
      isDefault = true;
    } else {
      isDefault = ValueWithDefault.isAllDefault(count, href, itemsP);
    }
  }

  public boolean isDefault() {
    return isDefault;
  }

  public static boolean isDataNecessary(@NotNull final Fields fields){
    return fields.isIncluded("investigation", false, true) || fields.isIncluded("count", false, true);
  }
}
