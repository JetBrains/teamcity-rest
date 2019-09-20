/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "cloudImages")
@XmlType(name = "cloudImages")
public class CloudImages {
  @XmlElement(name = "cloudImage")
  public List<CloudImage> cloudImages;

  @XmlAttribute
  public Integer count;

  @XmlAttribute(required = false) @Nullable public String nextHref;
  @XmlAttribute(required = false) @Nullable public String prevHref;
  @XmlAttribute(required = false) @Nullable public String href;

  public CloudImages() {}

  public CloudImages(final @NotNull CachingValue<List<jetbrains.buildServer.clouds.CloudImage>> items, final PagerData pagerData, @NotNull final Fields fields, @NotNull final BeanContext context) {
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
}
