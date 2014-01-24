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

package jetbrains.buildServer.server.rest.model.buildType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "investigations")
public class Investigations {
  @XmlAttribute public Integer count;
  @XmlAttribute(required = false) @Nullable public String nextHref;
  @XmlAttribute(required = false) @Nullable public String prevHref;
  @XmlAttribute(name = "href") public String href;

  @XmlElement(name = "investigation") public List<Investigation> items;

  public Investigations() {
  }

  public Investigations(@Nullable final Collection<InvestigationWrapper> itemsP,
                        @Nullable final Href hrefP,
                        @NotNull final Fields fields,
                        @Nullable final PagerData pagerData,
                        @NotNull final BeanContext beanContext) {
    href = hrefP == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("href"), hrefP.getHref());

    if (itemsP != null) {
      if (fields.isIncluded("investigation", false, true)) {
        items = new ArrayList<Investigation>(itemsP.size());
        for (InvestigationWrapper item : itemsP) {
          items.add(new Investigation(item, fields.getNestedField("investigation", Fields.LONG, Fields.LONG), beanContext));
        }
      }
      count = ValueWithDefault.decideDefault(fields.isIncluded("count"), itemsP.size());

      if (pagerData != null) {
        nextHref = pagerData.getNextHref() == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("nextHref"),
                                                                                           beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()));
        prevHref = pagerData.getPrevHref() == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("prevHref"),
                                                                                           beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()));
      }
      }
  }
}
