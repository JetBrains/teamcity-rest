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

package jetbrains.buildServer.server.rest.model.server;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import java.util.*;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.LicenseKey;
import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public LicenseKeyEntities(@NotNull final Collection<LicenseKey> licenseKeys, @Nullable final Collection<LicenseKey> activeLicenseKeys, @Nullable final String href, @NotNull final Fields fields) {
    this.licenseKeys = ValueWithDefault.decideDefault(fields.isIncluded("licenseKey", false, true), () -> getLicenseKeys(licenseKeys, activeLicenseKeys, fields));
    this.href = ValueWithDefault.decideDefault(fields.isIncluded("href"), href);
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), licenseKeys.size());
  }

  private List<LicenseKeyEntity> getLicenseKeys(final @NotNull Collection<LicenseKey> licenseKeys, final @Nullable Collection<LicenseKey> activeLicenseKeys, final @NotNull Fields fields) {
    List<LicenseKey> originalList = new ArrayList<>(licenseKeys);
    Collections.sort(originalList, new Comparator<LicenseKey>() {
      @Override
      public int compare(final LicenseKey o1, final LicenseKey o2) {
        return ComparisonChain.start()
                              .compareTrueFirst(o1.isValid(), o2.isValid())
                              .compare(o1.getLicenseKeyData(), o2.getLicenseKeyData(),
                                       Ordering.natural().nullsLast().onResultOf(input -> input == null ? null : input.getLicenseType()))
                              .result();
      }
    });
    return CollectionsUtil.convertCollection(originalList, (source) ->
      new LicenseKeyEntity(source, activeLicenseKeys == null ? null : activeLicenseKeys.contains(source), fields.getNestedField("licenseKey", Fields.LONG, Fields.LONG)));
  }
}
