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

package jetbrains.buildServer.server.rest.model.server;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.controllers.license.ServerLicenseBean;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
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
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_DESCRIPTION, value = "Represents a license key details."))
public class LicenseKeyEntity {
  protected static final String LICENSE_TYPE_EVALUATION = "evaluation";
  protected static final String LICENSE_TYPE_EAP = "eap";
  protected static final String LICENSE_TYPE_OPEN_SOURCE = "open_source";
  protected static final String LICENSE_TYPE_COMMERCIAL = "commercial";

  /**
   * The key is recognized and can be used by this server version at this time (check "active" if it is actually used)
   */
  @XmlAttribute
  public Boolean valid;

  /**
   * The key is actually used by the server (considering other keys added)
   */
  @XmlAttribute
  public Boolean active;

  /**
   * The license key has expiration date and that has been elapsed
   */
  @XmlAttribute
  public Boolean expired;

  /**
   * The license cannot be used with this server version as the maintenance period (maintenanceEndDate) does not cover server's effective release date
   */
  @XmlAttribute
  public Boolean obsolete;


  @XmlAttribute
  public String expirationDate;

  @XmlAttribute
  public String maintenanceEndDate;

  @XmlAttribute
  public String type;

  @XmlAttribute
  public Integer servers;

  @XmlAttribute
  public Integer agents;

  @XmlAttribute
  public Boolean unlimitedAgents;

  @XmlAttribute
  public Integer buildTypes;

  @XmlAttribute
  public Boolean unlimitedBuildTypes;

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

      servers = ValueWithDefault.decideDefault(fields.isIncluded("servers"), licenseKey.isEnterpriseLicense() ? 1 : 0);

      final boolean unlimitedAgentsLicense = licenseKey.isUnlimitedAgentsLicense();
      unlimitedAgents = ValueWithDefault.decideDefault(fields.isIncluded("unlimitedAgents"), unlimitedAgentsLicense);
      if (!unlimitedAgentsLicense) {
        agents = ValueWithDefault.decideDefault(fields.isIncluded("agents"), licenseKeyData.getNumberOfAgents());
      }

      final boolean unlimitedBuildTypesLicense = licenseKey.isEnterpriseLicense();
      unlimitedBuildTypes = ValueWithDefault.decideDefault(fields.isIncluded("unlimitedBuildTypes"), unlimitedBuildTypesLicense);
      if (!unlimitedBuildTypesLicense) {
        buildTypes = ValueWithDefault.decideDefault(fields.isIncluded("buildTypes"), getNumberOfBuildTypes(licenseKey));
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

  /**
   * See also {@link jetbrains.buildServer.server.rest.model.server.LicensingData#getServerLicenseType(jetbrains.buildServer.serverSide.LicenseList)}
   */
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
