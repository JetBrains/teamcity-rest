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
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.change.VcsRootInstanceRef;
import jetbrains.buildServer.vcs.VcsRootInstance;
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
  public List<VcsRootInstanceRef> vcsRoots;

  @XmlAttribute
  public long count;

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

  public VcsRootInstances(@NotNull final Collection<VcsRootInstance> serverVcsRoots,
                  @Nullable final PagerData pagerData,
                  @NotNull final ApiUrlBuilder apiUrlBuilder) {
    vcsRoots = new ArrayList<VcsRootInstanceRef>(serverVcsRoots.size());
    for (VcsRootInstance root : serverVcsRoots) {
      vcsRoots.add(new VcsRootInstanceRef(root, apiUrlBuilder));
    }
    if (pagerData != null) {
      nextHref = pagerData.getNextHref() != null ? apiUrlBuilder.transformRelativePath(pagerData.getNextHref()) : null;
      prevHref = pagerData.getPrevHref() != null ? apiUrlBuilder.transformRelativePath(pagerData.getPrevHref()) : null;
      href = apiUrlBuilder.transformRelativePath(pagerData.getHref());
    }
    count = vcsRoots.size();
  }
}
