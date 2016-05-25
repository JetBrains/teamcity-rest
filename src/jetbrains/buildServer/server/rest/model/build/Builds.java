/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.BuildPromotionFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
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
  public String href;

  @XmlAttribute(required = false)
  @Nullable
  public String nextHref;

  @XmlAttribute(required = false)
  @Nullable
  public String prevHref;

  public Builds() {
  }

  public Builds(@Nullable final List<BuildPromotion> buildObjects,
                @Nullable final PagerData pagerData,
                @NotNull final Fields fields,
                @NotNull final BeanContext beanContext) {
    if (buildObjects != null && (fields.isIncluded("build", false, true) || fields.isIncluded("count", false, true))) {
      final ArrayList<Build> buildsList = new ArrayList<Build>(buildObjects.size());
      for (BuildPromotion build : buildObjects) {
        buildsList.add(new Build(build, fields.getNestedField("build"), beanContext));
      }
      init(buildsList, pagerData, fields, beanContext);
    } else {
      init(null, pagerData, fields, beanContext);
    }
  }

  private Builds(@Nullable final PagerData pagerData,
                @NotNull final Fields fields,
                @NotNull final BeanContext beanContext,
                @Nullable final List<SBuild> buildObjects                ) {
    if (buildObjects != null && fields.isIncluded("build", false, true)) {
      final List<Build> buildsList = new ArrayList<Build>(buildObjects.size());
      for (SBuild build : buildObjects) {
        buildsList.add(new Build(build, fields.getNestedField("build"), beanContext));
      }

      init(buildsList, pagerData, fields, beanContext);
    } else {
      init(null, pagerData, fields, beanContext);
    }
  }

  private void init(@Nullable final List<Build> buildObjects, @Nullable final PagerData pagerData, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    if (buildObjects != null && fields.isIncluded("build", false, true)) {
      builds = ValueWithDefault.decideDefault(fields.isIncluded("build"), buildObjects);
    } else {
      builds = null;
    }
    if (pagerData != null) {
      href = ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getHref()));
      nextHref = ValueWithDefault
        .decideDefault(fields.isIncluded("nextHref"), pagerData.getNextHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null);
      prevHref = ValueWithDefault
        .decideDefault(fields.isIncluded("prevHref"), pagerData.getPrevHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null);
    }
    count = buildObjects == null ? null : ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), buildObjects.size());
  }

  @Override
  public boolean isDefault() {
    return ValueWithDefault.isAllDefault(builds, count, href);
  }

  @NotNull
  public List<BuildPromotion> getFromPosted(@NotNull final ServiceLocator serviceLocator, @NotNull final Map<Long, Long> buildPromotionIdReplacements) {
    if (builds == null){
      return Collections.emptyList();
    }
    final BuildPromotionFinder buildFinder = serviceLocator.getSingletonService(BuildPromotionFinder.class);
    return CollectionsUtil.convertCollection(builds, new Converter<BuildPromotion, Build>() {
      @Override
      public BuildPromotion createFrom(@NotNull final Build source) {
        return source.getFromPosted(buildFinder, buildPromotionIdReplacements);
      }
    });
  }

  @NotNull
  public static Builds createFromBuildPromotions(@Nullable final List<BuildPromotion> buildObjects, @Nullable final PagerData pagerData, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    return new Builds(buildObjects, pagerData, fields, beanContext);
  }

  @NotNull
  public static Builds createFromBuilds(@Nullable final List<BuildPromotion> buildObjects, @Nullable final PagerData pagerData, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    return new Builds(buildObjects, pagerData, fields, beanContext);
  }
}
