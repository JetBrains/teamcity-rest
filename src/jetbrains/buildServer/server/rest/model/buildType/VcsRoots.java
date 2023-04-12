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

package jetbrains.buildServer.server.rest.model.buildType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.finder.impl.VcsRootFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "vcs-roots")
@XmlType(name = "vcs-roots")
@ModelBaseType(ObjectType.PAGINATED)
public class VcsRoots {
  @XmlElement(name = "vcs-root")
  public List<VcsRoot> vcsRoots;

  @XmlAttribute
  public Integer count;

  @XmlAttribute(required = false)
  @Nullable
  public String href;

  @XmlAttribute(required = false)
  @Nullable
  public String nextHref;

  @XmlAttribute(required = false)
  @Nullable
  public String prevHref;

  public VcsRoots() {
  }

  public VcsRoots(@NotNull final Collection<jetbrains.buildServer.vcs.SVcsRoot> serverVcsRoots,
                  @Nullable final PagerData pagerData,
                  @NotNull final Fields fields,
                  @NotNull final BeanContext beanContext) {
    vcsRoots = ValueWithDefault.decideDefault(fields.isIncluded("vcs-root", false), () -> {
        final ArrayList<VcsRoot> items = new ArrayList<VcsRoot>(serverVcsRoots.size());
        Fields nestedFields = fields.getNestedField("vcs-root");
        for (jetbrains.buildServer.vcs.SVcsRoot root : serverVcsRoots) {
          items.add(new VcsRoot(root, nestedFields, beanContext));
        }
        return items;
    });
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), serverVcsRoots.size());

    if (pagerData != null) {
      href = ValueWithDefault.decideDefault(fields.isIncluded("href", true), beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getHref()));
      nextHref = ValueWithDefault
        .decideDefault(fields.isIncluded("nextHref"), pagerData.getNextHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null);
      prevHref = ValueWithDefault
        .decideDefault(fields.isIncluded("prevHref"), pagerData.getPrevHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null);

    }
  }

  public List<SVcsRoot> getVcsRoots(@NotNull VcsRootFinder vcsRootFinder){
    if (vcsRoots == null){
      return Collections.emptyList();
    }
    final ArrayList<SVcsRoot> result = new ArrayList<SVcsRoot>(vcsRoots.size());
    for (VcsRoot vcsRoot : vcsRoots) {
      result.add(vcsRoot.getVcsRoot(vcsRootFinder));
    }
    return result;
  }
}
