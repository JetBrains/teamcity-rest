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

package jetbrains.buildServer.server.rest.model.build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.BuildFinder;
import jetbrains.buildServer.server.rest.data.QueuedBuildFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "builds")
@XmlType(name = "builds")
public class Builds implements DefaultValueAware {
  @XmlElement(name = "build")
  public List<Build> builds;

  @XmlAttribute
  public Integer count;

  @XmlAttribute(required = false)
  @Nullable
  public String nextHref;

  @XmlAttribute(required = false)
  @Nullable
  public String prevHref;

  public Builds() {
  }

  public Builds(@NotNull final List<SBuild> buildObjects,
                final ServiceLocator serviceLocator,
                @Nullable final PagerData pagerData,
                final ApiUrlBuilder apiUrlBuilder) {
    this(
      CollectionsUtil.convertCollection(buildObjects, new Converter<BuildPromotion, SBuild>() {
        public BuildPromotion createFrom(@NotNull final SBuild source) {
          return source.getBuildPromotion();
        }
      }),
      pagerData, Fields.LONG, new BeanContext(serviceLocator.getSingletonService(BeanFactory.class), serviceLocator, apiUrlBuilder));
  }

  public Builds(@NotNull final List<BuildPromotion> buildObjects,
                @Nullable final PagerData pagerData,
                @NotNull Fields fields,
                @NotNull final BeanContext beanContext) {
    if (fields.isIncluded("build", false, true)) {
      final ArrayList<Build> buildsList = new ArrayList<Build>(buildObjects.size());
      for (BuildPromotion build : buildObjects) {
        buildsList.add(new Build(build, fields.getNestedField("build"), beanContext));
      }
      builds = ValueWithDefault.decideDefault(fields.isIncluded("build"), buildsList);
    } else {
      builds = null;
    }
    if (pagerData != null) {
      nextHref = ValueWithDefault
        .decideDefault(fields.isIncluded("nextHref"), pagerData.getNextHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null);
      prevHref = ValueWithDefault
        .decideDefault(fields.isIncluded("prevHref"), pagerData.getPrevHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null);
    }
    count = ValueWithDefault.decideDefault(fields.isIncluded("count"), buildObjects.size());
  }

  public boolean isDefault() {
    return builds != null ? builds.size() == 0 : (count == null || count == 0);
  }

  @NotNull
  public List<BuildPromotion> getFromPosted(@NotNull final ServiceLocator serviceLocator) {
    if (builds == null){
      return Collections.emptyList();
    }
    final BuildFinder buildFinder = serviceLocator.getSingletonService(BuildFinder.class);
    final QueuedBuildFinder queuedBuildFinder = serviceLocator.getSingletonService(QueuedBuildFinder.class);
    return CollectionsUtil.convertCollection(builds, new Converter<BuildPromotion, Build>() {
      public BuildPromotion createFrom(@NotNull final Build source) {
        return source.getFromPosted(buildFinder, queuedBuildFinder);
      }
    });
  }
}
