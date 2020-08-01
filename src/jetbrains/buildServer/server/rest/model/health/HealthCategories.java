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

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.healthStatus.ItemCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.List;
import java.util.stream.Collectors;

@XmlRootElement(name = "healthCategories")
@XmlType(name = "healthCategories", propOrder = {"count", "healthCategories", "href", "nextHref", "prevHref"})
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_BASE_TYPE, value = ObjectType.PAGINATED))
public class HealthCategories {
  @NotNull
  static final String NAME = "healthCategory";
  @Nullable
  private final Integer count;
  @Nullable
  private final List<HealthCategory> healthCategories;
  @Nullable
  private final String href;
  @Nullable
  private final String nextHref;
  @Nullable
  private final String prevHref;

  @SuppressWarnings("unused")
  public HealthCategories() {
    this.count = null;
    this.healthCategories = null;
    this.href = null;
    this.nextHref = null;
    this.prevHref = null;
  }

  public HealthCategories(@NotNull final List<ItemCategory> items,
                          @NotNull final PagerData pagerData,
                          @NotNull final Fields fields,
                          @NotNull final BeanContext context) {
    this.count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), items.size());
    this.healthCategories = ValueWithDefault.decideDefault(fields.isIncluded(NAME, false, true),
                                                           () -> items.stream().map(i -> mapFunction(fields, i)).collect(Collectors.toList()));
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

  @XmlAttribute
  @Nullable
  public Integer getCount() {
    return count;
  }

  @XmlElement(name = NAME)
  @Nullable
  public List<HealthCategory> getHealthCategories() {
    return healthCategories;
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

  @NotNull
  private String generateHref(@NotNull final BeanContext context, final String href) {
    return context.getApiUrlBuilder().transformRelativePath(href);
  }

  @NotNull
  private HealthCategory mapFunction(@NotNull final Fields fields, final ItemCategory item) {
    return new HealthCategory(item, fields.getNestedField(NAME, Fields.SHORT, Fields.LONG));
  }
}
