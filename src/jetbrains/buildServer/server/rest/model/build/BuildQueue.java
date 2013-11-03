/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "buildQueue")
@XmlType(name = "buildQueue")
public class BuildQueue {
  @XmlElement(name = "queuedBuild")
  public List<QueuedBuildRef> queuedBuilds;

  @XmlAttribute
  public long count;

  @XmlAttribute(required = false)
  @Nullable
  public String nextHref;

  @XmlAttribute(required = false)
  @Nullable
  public String prevHref;

  public BuildQueue() {
  }

  public BuildQueue(@NotNull final List<SQueuedBuild> buildObjects,
                    @Nullable final PagerData pagerData,
                    final ApiUrlBuilder apiUrlBuilder,
                    final BeanFactory factory) {
    queuedBuilds = new ArrayList<QueuedBuildRef>(buildObjects.size());
    for (SQueuedBuild build : buildObjects) {
      queuedBuilds.add(new QueuedBuildRef(build, apiUrlBuilder, factory));
    }
    if (pagerData != null) {
      nextHref = pagerData.getNextHref() != null ? apiUrlBuilder.transformRelativePath(pagerData.getNextHref()) : null;
      prevHref = pagerData.getPrevHref() != null ? apiUrlBuilder.transformRelativePath(pagerData.getPrevHref()) : null;
    }
    count = queuedBuilds.size();
  }
}
