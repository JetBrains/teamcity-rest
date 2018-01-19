/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.problem;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.problem.MuteData;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.11.13
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "mutes")
public class Mutes implements DefaultValueAware{
  @XmlAttribute public Integer count;
  @XmlAttribute(required = false) @Nullable public String nextHref;
  @XmlAttribute(required = false) @Nullable public String prevHref;
  @XmlAttribute(name = "href") public String href;

  @XmlElement(name = "mute") public List<Mute> items;

  private boolean isDefault;

  public Mutes() {
  }

  public Mutes(@Nullable final Collection<MuteInfo> itemsP,
               @Nullable final PagerData pagerData, //todo: not nulls are not yet implemented
               @NotNull final Fields fields,
               @NotNull final BeanContext beanContext) {
    items = itemsP == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("mute", false), new ValueWithDefault.Value<List<Mute>>() {
      public List<Mute> get() {
        return CollectionsUtil.convertCollection(itemsP, new Converter<Mute, MuteInfo>() {
          public Mute createFrom(@NotNull final MuteInfo source) {
            return new Mute(source, fields.getNestedField("mute", Fields.NONE, Fields.LONG), beanContext);
          }
        });
      }
    });
    if (pagerData != null) {
      href = ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getHref()));
      nextHref = pagerData.getNextHref() == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("nextHref"),
                                                                                         beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()));
      prevHref = pagerData.getPrevHref() == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("prevHref"),
                                                                                         beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()));
    }
    count = itemsP == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("count", true), itemsP.size());

    if (itemsP != null && itemsP.isEmpty()) {
      isDefault = true;
    } else {
      isDefault = ValueWithDefault.isAllDefault(count, href, itemsP);
    }
  }

  public boolean isDefault() {
    return isDefault;
  }

  public List<MuteData> getFromPosted(@NotNull final ServiceLocator serviceLocator) {
    if (items == null) {
      throw new BadRequestException("Invalid 'mutes' entity: 'items' should be specified");
    }
    return items.stream().map(mute -> mute.getFromPosted(serviceLocator)).collect(Collectors.toList());
  }

}
