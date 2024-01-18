/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.cloud;

import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.List;
import java.util.stream.Collectors;

@XmlRootElement(name = "cloudImages")
@XmlType(name = "cloudImages")
@ModelBaseType(ObjectType.PAGINATED)
public class CloudImages {
  private List<CloudImage> cloudImages;

  private Integer count;

  @Nullable
  private String nextHref;

  @Nullable
  private String prevHref;

  @Nullable
  private String href;

  public CloudImages() {
  }

  public CloudImages(
    @NotNull
    CachingValue<List<jetbrains.buildServer.clouds.CloudImage>> items,
    @Nullable
    PagerData pagerData,
    @NotNull
    Fields fields,
    @NotNull
    BeanContext context
  ) {
    cloudImages = ValueWithDefault.decideDefault(fields.isIncluded("cloudImage", false, true),
                                                 () -> items.get().stream().map(i -> new CloudImage(i, fields.getNestedField("cloudImage"), context)).collect(Collectors.toList()));

    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count", false, true), () -> items.get().size());

    if (pagerData != null) {
      href = ValueWithDefault.decideDefault(fields.isIncluded("href", true), context.getApiUrlBuilder().transformRelativePath(pagerData.getHref()));
      nextHref = ValueWithDefault
        .decideDefault(fields.isIncluded("nextHref"), pagerData.getNextHref() != null ? context.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null);
      prevHref = ValueWithDefault
        .decideDefault(fields.isIncluded("prevHref"), pagerData.getPrevHref() != null ? context.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null);
    }
  }

  @XmlElement(name = "cloudImage")
  public List<CloudImage> getCloudImages() {
    return cloudImages;
  }

  @XmlAttribute
  public Integer getCount() {
    return count;
  }

  @Nullable
  @XmlAttribute(required = false)
  public String getNextHref() {
    return nextHref;
  }

  @Nullable
  @XmlAttribute(required = false)
  public String getPrevHref() {
    return prevHref;
  }

  @Nullable
  @XmlAttribute(required = false)
  public String getHref() {
    return href;
  }
}