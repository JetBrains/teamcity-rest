/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.health;

import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "healthStatusItems")
@XmlType(name = "healthStatusItems", propOrder = {"count", "healthItems", "href", "nextHref", "prevHref"})
public class HealthItems {
  @Nullable
  private final List<HealthItem> healthItems;
  @Nullable
  private final Integer count;
  @Nullable
  private final String href;
  @Nullable
  private final String nextHref;
  @Nullable
  private final String prevHref;

  @SuppressWarnings("unused")
  public HealthItems() {
    this.healthItems = null;
    this.count = null;
    this.nextHref = null;
    this.prevHref = null;
    this.href = null;
  }

  public HealthItems(@NotNull final List<jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem> items,
                     @NotNull final PagerData pagerData,
                     @NotNull final Fields fields,
                     @NotNull final BeanContext context) {
    this.healthItems = ValueWithDefault.decideDefault(fields.isIncluded(HealthItem.NAME, false, true),
                                                      () -> items.stream().map(i -> mapFunction(fields, i)).collect(Collectors.toList()));
    this.count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), items::size);
    if (pagerData != null) {
      this.href = ValueWithDefault.decideDefault(fields.isIncluded("href", true), () -> generateHref(context, pagerData.getHref()));
      this.nextHref = ValueWithDefault
        .decideDefault(fields.isIncluded("nextHref"), () -> pagerData.getNextHref() != null ? generateHref(context, pagerData.getNextHref()) : null);
      this.prevHref = ValueWithDefault
        .decideDefault(fields.isIncluded("prevHref"), () -> pagerData.getPrevHref() != null ? generateHref(context, pagerData.getPrevHref()) : null);
    } else {
      this.href = null;
      this.nextHref = null;
      this.prevHref = null;
    }
  }

  @NotNull
  private HealthItem mapFunction(@NotNull final Fields fields, final HealthStatusItem item) {
    return new HealthItem(item, fields.getNestedField(HealthItem.NAME, Fields.SHORT, Fields.LONG));
  }

  @NotNull
  private String generateHref(@NotNull final BeanContext context, final String href) {
    return context.getApiUrlBuilder().transformRelativePath(href);
  }

  @Nullable
  @XmlElement(name = HealthItem.NAME)
  public List<HealthItem> getHealthItems() {
    return healthItems;
  }

  @Nullable
  @XmlAttribute
  public Integer getCount() {
    return count;
  }

  @Nullable
  @XmlAttribute
  public String getHref() {
    return href;
  }

  @Nullable
  @XmlAttribute
  public String getNextHref() {
    return nextHref;
  }

  @Nullable
  @XmlAttribute
  public String getPrevHref() {
    return prevHref;
  }
}
