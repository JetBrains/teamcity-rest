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

package jetbrains.buildServer.server.rest.model.build;

import java.util.stream.Collectors;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.BuildPromotionFinder;
import jetbrains.buildServer.server.rest.model.*;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.*;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "builds")
@XmlType(name = "builds")
@ModelBaseType(ObjectType.PAGINATED)
public class Builds implements DefaultValueAware {
  private BeanContext myBeanContext;
  private Fields myFields = Fields.NONE;
  private ItemsProviders.ItemsRetriever<BuildPromotion> myBuildDataRetriever = new ListBasedItemsRetriever<>(Collections.emptyList());

  public Builds() {
  }

  private Builds(@NotNull final ItemsProviders.ItemsRetriever<BuildPromotion> buildPromotionsRetriever,
                 @NotNull final Fields fields,
                 @NotNull final BeanContext beanContext) {
    myFields = fields;
    myBeanContext = beanContext;
    myBuildDataRetriever = buildPromotionsRetriever;
  }

  @XmlElement(name = "build")
  public List<Build> getBuilds() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("build", false, true),
      () -> {
        List<BuildPromotion> promotions = myBuildDataRetriever.getItems();
        if(promotions == null) {
          return Collections.emptyList();
        }

        Fields nestedFields = myFields.getNestedField("build");
        return promotions.stream().map(bp -> new Build(bp, nestedFields, myBeanContext)).collect(Collectors.toList());
      }
    );
  }

  @XmlAttribute(name = "count")
  public Integer getCount() {
    return ValueWithDefault.decideIncludeByDefault(
      myFields.isIncluded("count", myBuildDataRetriever.isCountCheap(), myBuildDataRetriever.isCountCheap(), true),
      () -> myBuildDataRetriever.getCount()
    );
  }

  @XmlAttribute(required = false)
  @Nullable
  public String getHref() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("href"),
      () -> {
        PagerData pager = myBuildDataRetriever.getPagerData();
        if(pager == null) {
          return null;
        }
        return myBeanContext.getApiUrlBuilder().transformRelativePath(pager.getHref());
      }
    );
  }

  @XmlAttribute(required = false)
  @Nullable
  public String getNextHref() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("nextHref"),
      () -> {
        PagerData pager = myBuildDataRetriever.getPagerData();
        if(pager == null || pager.getNextHref() == null) {
          return null;
        }
        return myBeanContext.getApiUrlBuilder().transformRelativePath(pager.getNextHref());
      }
    );
  }

  @XmlAttribute(required = false)
  @Nullable
  public String getPrevHref() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("prevHref"),
      () -> {
        PagerData pager = myBuildDataRetriever.getPagerData();
        if(pager == null || pager.getPrevHref() == null) {
          return null;
        }
        return myBeanContext.getApiUrlBuilder().transformRelativePath(pager.getPrevHref());
      }
    );
  }

  @Override
  public boolean isDefault() {
    if(myBuildDataRetriever == null) {
      return true;
    }

    if(myBuildDataRetriever.isCountCheap()) {
      return ValueWithDefault.isAllDefault(myBuildDataRetriever.getCount(), getHref());
    }

    // it may not actually be false, but it requires calculation, which is not always necessary
    return false;
  }

  @Nullable
  private List<Build> mySubmittedBuilds;

  public void setBuilds(@Nullable List<Build> builds) {
    mySubmittedBuilds = builds;
  }

  // this should be ignored when serializing
  @Nullable
  public List<Build> getSubmittedBuilds() {
    return mySubmittedBuilds;
  }

  @NotNull
  public List<BuildPromotion> getFromPosted(@NotNull final ServiceLocator serviceLocator, @NotNull final Map<Long, Long> buildPromotionIdReplacements) {
    if (mySubmittedBuilds == null) {
      return Collections.emptyList();
    }
    final BuildPromotionFinder buildPromotionFinder = serviceLocator.getSingletonService(BuildPromotionFinder.class);
    return CollectionsUtil.convertCollection(mySubmittedBuilds, submittedBuild -> submittedBuild.getFromPosted(buildPromotionFinder, buildPromotionIdReplacements));
  }

  /**
   * Considers given list of promotions raw and <b>supports post filtering</b> with {@code fields.getLocator()} in case it is present.
   */
  @NotNull
  public static Builds createFromBuildPromotions(@Nullable final List<BuildPromotion> promotions,
                                                 @NotNull final Fields fields,
                                                 @NotNull final BeanContext beanContext) {
    if(promotions == null) {
      return new Builds(new ListBasedItemsRetriever<>(Collections.emptyList()), fields, beanContext);
    }
    final BuildPromotionFinder finder = beanContext.getSingletonService(BuildPromotionFinder.class);
    return new Builds(new FilteringItemsRetriever<>(new ListBasedItemsRetriever<>(promotions), fields.getLocator(), finder), fields, beanContext);
  }

  /**
   * Considers promotions from given retriever raw and <b>supports post filtering</b> with {@code fields.getLocator()} in case it is present.
   */
  @NotNull
  public static Builds createFromBuildPromotions(@NotNull final ItemsProviders.ItemsRetriever<BuildPromotion> promotionsRetriever,
                                                 @NotNull final Fields fields,
                                                 @NotNull final BeanContext beanContext) {
    final BuildPromotionFinder finder = beanContext.getSingletonService(BuildPromotionFinder.class);
    return new Builds(new FilteringItemsRetriever<>(promotionsRetriever, fields.getLocator(), finder), fields, beanContext);
  }


  /**
   * Considered given list of promotions <b>pre-filtered</b> with {@code fields.getLocator()} in case it is present in {@code fields}.
   */
  @NotNull
  public static Builds createFromPrefilteredBuildPromotions(@Nullable final List<BuildPromotion> prefilteredPromotions,
                                                            @Nullable final PagerData pagerData,
                                                            @NotNull final Fields fields,
                                                            @NotNull final BeanContext beanContext) {
    if(prefilteredPromotions == null) {
      return new Builds(new ListBasedItemsRetriever<>(Collections.emptyList()), fields, beanContext);
    }

    return new Builds(new ListBasedItemsRetriever<>(prefilteredPromotions, pagerData), fields, beanContext);
  }

  /**
   * Considered given list of promotions <b>pre-filtered</b> with {@code fields.getLocator()} in case it is present in {@code fields}.
   */
  @NotNull
  public static Builds createFromPrefilteredBuildPromotions(@NotNull final ItemsProviders.ItemsProvider<BuildPromotion> prefilteredPromotions,
                                                            @NotNull final Fields fields,
                                                            @NotNull final BeanContext beanContext) {
    return new Builds(new ItemProviderBasedItemsRetriever<>(prefilteredPromotions, fields.getLocator()), fields, beanContext);
  }

}
