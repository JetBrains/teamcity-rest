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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.request.ServerRequest;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.LicenseKeysManager;
import jetbrains.buildServer.serverSide.LicenseList;
import jetbrains.buildServer.serverSide.LicensingPolicyEx;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 05.05.2014
 */
@SuppressWarnings({"PublicField", "WeakerAccess"})
@XmlRootElement(name = "licensingData")
@XmlType(name = "licensingData")
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_DESCRIPTION,
    value = "Represents license state details (available build configurations, agents, etc.)."))
public class LicensingData {
  @XmlAttribute
  public Boolean licenseUseExceeded;

  @XmlAttribute
  public Integer maxAgents;

  @XmlAttribute
  public Boolean unlimitedAgents;

  @XmlAttribute
  public Integer agentsLeft;

  @XmlAttribute
  public Integer maxBuildTypes;

  @XmlAttribute
  public Boolean unlimitedBuildTypes;

  @XmlAttribute
  public Integer buildTypesLeft;

  @XmlAttribute
  public String serverLicenseType;

  /**
   * Effective release date of the server (the date which is compared to license's maintenance end date)
   */
  @XmlAttribute
  public String serverEffectiveReleaseDate;


  @XmlElement(name = "licenseKeys")
  public LicenseKeyEntities licenseKeys;

  //todo: check getActiveLicensesNum() is visible in the keys list

  public LicensingData() {
  }

  public LicensingData(final @NotNull LicenseKeysManager licenseKeysManager, final @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    final LicenseList licenseList = licenseKeysManager.getLicenseList();

    licenseKeys = ValueWithDefault.decideDefault(fields.isIncluded("licenseKeys"),
                                                 () -> new LicenseKeyEntities(licenseList.getAllLicenses(), licenseList.getActiveLicenses(), ServerRequest.getLicenseKeysListHref(),
                                                                              fields.getNestedField("licenseKeys", Fields.SHORT, Fields.LONG), beanContext));

    final boolean unlimitedBuildTypes = licenseList.isUnlimitedBuildTypes();
    this.unlimitedBuildTypes = ValueWithDefault.decideDefault(fields.isIncluded("unlimitedBuildTypes"), unlimitedBuildTypes);
    if (!unlimitedBuildTypes) {
      maxBuildTypes = ValueWithDefault.decideDefault(fields.isIncluded("maxBuildTypes"), licenseList.getLicensedBuildTypesCount());
    }

    final boolean unlimitedAgents = licenseList.isUnlimitedAgents();
    this.unlimitedAgents = ValueWithDefault.decideDefault(fields.isIncluded("unlimitedAgents"), unlimitedAgents);
    if (!unlimitedAgents) {
      maxAgents = ValueWithDefault.decideDefault(fields.isIncluded("maxAgents"), licenseList.getLicensedAgentCount());
    }

    final LicensingPolicyEx licensingPolicy = licenseKeysManager.getLicensingPolicy();

    licenseUseExceeded = ValueWithDefault.decideDefault(fields.isIncluded("licenseUseExceeded"), licensingPolicy.isMaxNumberOfBuildTypesExceeded());

    serverLicenseType = ValueWithDefault.decideDefault(fields.isIncluded("serverLicenseType"), getServerLicenseType(licenseList));

    serverEffectiveReleaseDate = ValueWithDefault.decideDefault(fields.isIncluded("serverEffectiveReleaseDate"), Util.formatTime(licenseList.getReleaseDate()));

    if (licensingPolicy.getAgentsLicensesLeft() != -1) {
      agentsLeft = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("agentsLeft"), licensingPolicy.getAgentsLicensesLeft());
    }

    if (licensingPolicy.getBuildTypesLicensesLeft() != -1) {
      buildTypesLeft = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("buildTypesLeft"), licensingPolicy.getBuildTypesLicensesLeft());
    }
  }

  protected static final String SERVER_LICENSE_TYPE_ENTERPRISE = "enterprise";
  protected static final String SERVER_LICENSE_TYPE_PROFESSIONAL = "professional";
  protected static final String SERVER_LICENSE_TYPE_EVALUATION = LicenseKeyEntity.LICENSE_TYPE_EVALUATION;
  protected static final String SERVER_LICENSE_TYPE_EAP = LicenseKeyEntity.LICENSE_TYPE_EAP;
  protected static final String SERVER_LICENSE_TYPE_OPEN_SOURCE = LicenseKeyEntity.LICENSE_TYPE_OPEN_SOURCE;

  /**
   * See also {@link jetbrains.buildServer.server.rest.model.server.LicenseKeyEntity#getLicenseType(jetbrains.buildServer.serverSide.LicenseKey)}
   */
  private String getServerLicenseType(final LicenseList licenseList) {
    if (licenseList.isEvaluationMode()) {
      return SERVER_LICENSE_TYPE_EVALUATION;
    }

    if (licenseList.isEAPEvaluationMode()) {
      return SERVER_LICENSE_TYPE_EAP;
    }

    if (licenseList.isOpenSourceMode()) {
      return SERVER_LICENSE_TYPE_OPEN_SOURCE;
    }

    if (licenseList.hasEnterpriseLicense()) {
      return SERVER_LICENSE_TYPE_ENTERPRISE;
    }

    return SERVER_LICENSE_TYPE_PROFESSIONAL;
  }
}
