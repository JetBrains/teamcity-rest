/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.controllers.license.ServerLicenseBean;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.LicenseKey;
import jetbrains.buildServer.serverSide.LicenseKeyData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 05.05.2014
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "licenseKey")
@XmlType(name = "licenseKey")
public class LicenseKeyEntity {
  protected static final String LICENSE_TYPE_EVALUATION = "EVALUATION";
  protected static final String LICENSE_TYPE_EAP = "EAP";
  protected static final String LICENSE_TYPE_OPEN_SOURCE = "OPEN_SOURCE";
  protected static final String LICENSE_TYPE_COMMERCIAL = "COMMERCIAL";

  @XmlAttribute
  public Boolean valid;

  @XmlAttribute
  public Boolean active;

  @XmlAttribute
  public Boolean expired;

  @XmlAttribute
  public Boolean obsolete;

  @XmlAttribute
  public String type;

  @XmlAttribute
  public String expirationDate;

  @XmlAttribute
  public String maintenanceEndDate;

  @XmlAttribute
  public Integer serversCount;

  @XmlAttribute
  public Integer agentsCount;

  @XmlAttribute
  public Boolean agentsCountUnlimited;

  @XmlAttribute
  public Integer buildTypesCount;

  @XmlAttribute
  public Boolean buildTypesCountUnlimited;

  @XmlAttribute
  public String errorDetails;

  @XmlAttribute
  public String key;

  @XmlAttribute
  public String rawType;

  public LicenseKeyEntity() {
  }

  public LicenseKeyEntity(@NotNull final LicenseKey licenseKey, @Nullable final Boolean active, @NotNull final Fields fields) {
    valid = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("valid"), licenseKey.isValid());
    obsolete = ValueWithDefault.decideDefault(fields.isIncluded("obsolete"), licenseKey.isObsolete());
    this.active = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("active"), active);

    errorDetails = ValueWithDefault.decideDefault(fields.isIncluded("errorDetails"), licenseKey.getValidateError());

    key = ValueWithDefault.decideDefault(fields.isIncluded("key"), licenseKey.getKey());

    LicenseKeyData licenseKeyData = licenseKey.getLicenseKeyData();
    if (licenseKeyData != null) {
      type = ValueWithDefault.decideDefault(fields.isIncluded("type"), getLicenseType(licenseKey));

      rawType = ValueWithDefault.decideDefault(fields.isIncluded("rawType", false, false), String.valueOf(licenseKeyData.getLicenseType()));

      expirationDate = ValueWithDefault.decideDefault(fields.isIncluded("expirationDate"), Util.formatTime(licenseKeyData.getExpirationDate()));
      expired = ValueWithDefault.decideDefault(fields.isIncluded("expired"), licenseKeyData.isLicenseExpired());
      maintenanceEndDate = ValueWithDefault.decideDefault(fields.isIncluded("maintenanceEndDate"), Util.formatTime(licenseKeyData.getMaintenanceDueDate()));

      serversCount = ValueWithDefault.decideDefault(fields.isIncluded("serversCount"), licenseKey.isEnterpriseLicense() ? 1 : 0);

      final boolean unlimitedAgentsLicense = licenseKey.isUnlimitedAgentsLicense();
      agentsCountUnlimited = ValueWithDefault.decideDefault(fields.isIncluded("agentsCountUnlimited"), unlimitedAgentsLicense);
      if (!unlimitedAgentsLicense) {
        agentsCount = ValueWithDefault.decideDefault(fields.isIncluded("agentsCount"), licenseKeyData.getNumberOfAgents());
      }

      final boolean unlimitedBuildTypesLicense = licenseKey.isEnterpriseLicense();
      buildTypesCountUnlimited = ValueWithDefault.decideDefault(fields.isIncluded("buildTypesCountUnlimited"), unlimitedBuildTypesLicense);
      if (!unlimitedBuildTypesLicense) {
        buildTypesCount = ValueWithDefault.decideDefault(fields.isIncluded("buildTypesCount"), getNumberOfBuildTypes(licenseKey));
      }
    }
  }


  private Integer getNumberOfBuildTypes(final LicenseKey licenseKey) {
    final ServerLicenseBean.LicenseData licenseData = new ServerLicenseBean().new LicenseData(licenseKey);
    final String numberOfAgents = licenseData.getNumberOfConfigurations();
    if ("N/A".equals(numberOfAgents)) {
      return 0;
    }

    try {
      return Integer.valueOf(numberOfAgents);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private String getLicenseType(final LicenseKey licenseKey) {
    if (licenseKey.isEvaluationLicenseKey()) {
      return LICENSE_TYPE_EVALUATION;
    }

    if (licenseKey.isEAPLicenseKey()) {
      return LICENSE_TYPE_EAP;
    }

    if (licenseKey.isOpenSourceLicenseKey()) {
      return LICENSE_TYPE_OPEN_SOURCE;
    }

    return LICENSE_TYPE_COMMERCIAL;
  }
}
