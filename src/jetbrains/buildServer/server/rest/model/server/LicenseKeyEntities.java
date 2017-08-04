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

package jetbrains.buildServer.server.rest.model.server;

import com.google.common.collect.Ordering;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.LicenseKey;
import jetbrains.buildServer.serverSide.LicenseKeyData;
import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.collect.Ordering.natural;

/**
 * @author Yegor.Yarko
 *         Date: 01/07/2016
 */
@XmlRootElement(name = "licenseKeys")
@XmlType(name = "licenseKeys")
public class LicenseKeyEntities {
  @XmlElement(name = "licenseKey")
  public List<LicenseKeyEntity> licenseKeys;

  @XmlAttribute
  public Integer count;

  @XmlAttribute(required = false)
  @Nullable
  public String href;

  public LicenseKeyEntities() {
  }

  public LicenseKeyEntities(@NotNull final Collection<LicenseKey> licenseKeys, @Nullable final Collection<LicenseKey> activeLicenseKeys, @Nullable final String href,
                            @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    this.licenseKeys = ValueWithDefault.decideDefault(fields.isIncluded("licenseKey", false, true), () -> getLicenseKeys(licenseKeys, activeLicenseKeys, fields));
    this.href = href == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().transformRelativePath(href));
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), licenseKeys.size());
  }

  private List<LicenseKeyEntity> getLicenseKeys(final @NotNull Collection<LicenseKey> licenseKeys, final @Nullable Collection<LicenseKey> activeLicenseKeys, final @NotNull Fields fields) {
    Ordering<LicenseKeyData> licenseKeyDataComparator = Ordering
      .natural().reverse().nullsFirst().onResultOf((LicenseKeyData input) -> input == null ? null : input.getExpirationDate())
      .compound(Ordering.natural().reverse().nullsFirst().onResultOf((LicenseKeyData input) -> input == null ? null : input.getMaintenanceDueDate()));
    Ordering<LicenseKey> ordering = Ordering
      .natural().reverse().nullsLast().onResultOf((LicenseKey input) -> activeLicenseKeys == null || input == null ? null : activeLicenseKeys.contains(input))
      .compound(natural().reverse().onResultOf((LicenseKey item) -> item == null ? null : item.isValid()))
      .compound(licenseKeyDataComparator.onResultOf(item -> item == null ? null : item.getLicenseKeyData()));

    return CollectionsUtil.convertCollection(ordering.sortedCopy(licenseKeys), (source) ->
      new LicenseKeyEntity(source, activeLicenseKeys == null ? null : activeLicenseKeys.contains(source), fields.getNestedField("licenseKey", Fields.LONG, Fields.LONG)));
  }
}
