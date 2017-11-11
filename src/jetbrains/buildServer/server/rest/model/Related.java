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

package jetbrains.buildServer.server.rest.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.data.BuildPromotionFinder;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildPromotion;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 * Date: 11/11/2017
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "related")
public class Related {
  @XmlElement
  public Builds builds;

  public Related() {
  }

  public Related(@NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    final @NotNull ItemsProviders.LocatorAware<ItemsProviders.ItemsRetriever<BuildPromotion>> buildsData =
      new ItemsProviders.LocatorAwareItemsRetriever<BuildPromotion>(beanContext.getSingletonService(BuildPromotionFinder.class).getLazyResult(), null);

    builds = ValueWithDefault.decideDefault(fields.isIncluded("builds", true, true),
                                            () -> {
                                              Fields nestedFields = fields.getNestedField("builds", Fields.SHORT, Fields.LONG);
                                              return nestedFields.getLocator() == null ? null : new Builds(buildsData, nestedFields, beanContext);
                                            });
  }
}
