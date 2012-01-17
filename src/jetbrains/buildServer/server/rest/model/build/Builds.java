/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.build;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.SBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "builds")
@XmlType(name = "builds")
public class Builds {
  @XmlElement(name = "build", namespace = "ref")
  public List<BuildRef> builds;

  @XmlAttribute
  public long count;

  @XmlAttribute(required = false)
  @Nullable
  public String nextHref;

  @XmlAttribute(required = false)
  @Nullable
  public String prevHref;

  public Builds() {
  }

  public Builds(@NotNull final List buildObjects,
                @NotNull final DataProvider dataProvider,
                @NotNull final PagerData pagerData,
                final ApiUrlBuilder apiUrlBuilder) {
    builds = new ArrayList<BuildRef>(buildObjects.size());
    for (Object build : buildObjects) {
      builds.add(new BuildRef((SBuild)build, dataProvider, apiUrlBuilder));
    }
    nextHref = pagerData.getNextHref() != null ? apiUrlBuilder.transformRelativePath(pagerData.getNextHref()) : null;
    prevHref = pagerData.getPrevHref() != null ? apiUrlBuilder.transformRelativePath(pagerData.getPrevHref()) : null;
    count = builds.size();
  }
}
