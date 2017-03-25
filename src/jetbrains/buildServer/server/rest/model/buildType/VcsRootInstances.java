/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.change.VcsRootInstance;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "vcs-root-instances")
@XmlType(name = "vcs-root-instances")
public class VcsRootInstances {
  @XmlElement(name = "vcs-root-instance")
  public List<VcsRootInstance> vcsRoots;

  @XmlAttribute
  public Integer count;

  @XmlAttribute
  public String href;

  @XmlAttribute(required = false)
  @Nullable
  public String nextHref;

  @XmlAttribute(required = false)
  @Nullable
  public String prevHref;

  public VcsRootInstances() {
  }

  public VcsRootInstances(@NotNull final CachingValue<Collection<jetbrains.buildServer.vcs.VcsRootInstance>> serverVcsRoots,
                          @Nullable final PagerData pagerData,
                          @NotNull final Fields fields,
                          @NotNull final BeanContext beanContext) {
    vcsRoots = ValueWithDefault.decideDefault(fields.isIncluded("vcs-root-instance", false), new ValueWithDefault.Value<List<VcsRootInstance>>() {
      @Nullable
      public List<VcsRootInstance> get() {
        final Collection<jetbrains.buildServer.vcs.VcsRootInstance> value = serverVcsRoots.get();
        final ArrayList<VcsRootInstance> items = new ArrayList<VcsRootInstance>(value.size());
        for (jetbrains.buildServer.vcs.VcsRootInstance root : value) {
          items.add(new VcsRootInstance(root, fields.getNestedField("vcs-root-instance"), beanContext));
        }
        return items;
      }
    });
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count", false), new ValueWithDefault.Value<Integer>() {
      @Nullable
      public Integer get() {
        return serverVcsRoots.get().size();
      }
    });

    if (pagerData != null) {
      href = ValueWithDefault.decideDefault(fields.isIncluded("href", true), beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getHref()));
      nextHref = ValueWithDefault
        .decideDefault(fields.isIncluded("nextHref"), pagerData.getNextHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null);
      prevHref = ValueWithDefault
        .decideDefault(fields.isIncluded("prevHref"), pagerData.getPrevHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null);

    }
  }
}
