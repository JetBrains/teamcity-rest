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

import java.util.Date;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.request.ServerRequest;
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
public class LicensingData {
  @XmlAttribute
  public Integer maxAgentsCount;

  @XmlAttribute
  public Boolean maxAgentsCountUnlimited;

  @XmlAttribute
  public Integer agentsLeftCount;

  @XmlAttribute
  public Integer maxBuildTypesCount;

  @XmlAttribute
  public Boolean maxBuildTypesCountUnlimited;

  @XmlAttribute
  public Integer buildTypesLeftCount;

  @XmlAttribute
  public String serverLicenseMode;

  @XmlAttribute
  public String serverReleaseDate;


  @XmlElement(name = "licenseKeys")
  public LicenseKeyEntities licenseKeys;

  //todo: check getActiveLicensesNum() is visible in the keys list

  public LicensingData() {
  }

  public LicensingData(final @NotNull LicenseKeysManager licenseKeysManager, final @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    final LicenseList licenseList = licenseKeysManager.getLicenseList();

    licenseKeys = ValueWithDefault.decideDefault(fields.isIncluded("licenseKeys"),
                                                 () -> new LicenseKeyEntities(licenseList.getAllLicenses(), licenseList.getActiveLicenses(), ServerRequest.getLicenseKeysListHref(),
                                                                              fields.getNestedField("licenseKeys", Fields.SHORT, Fields.LONG)));

    final boolean unlimitedBuildTypes = licenseList.isUnlimitedBuildTypes();
    maxBuildTypesCountUnlimited = ValueWithDefault.decideDefault(fields.isIncluded("maxBuildTypesCountUnlimited"), unlimitedBuildTypes);
    if (!unlimitedBuildTypes) {
      maxBuildTypesCount = ValueWithDefault.decideDefault(fields.isIncluded("maxBuildTypesCount"), licenseList.getLicensedBuildTypesCount());
    }

    final boolean unlimitedAgents = licenseList.isUnlimitedAgents();
    maxAgentsCountUnlimited = ValueWithDefault.decideDefault(fields.isIncluded("maxAgentsCountUnlimited"), unlimitedAgents);
    if (!unlimitedAgents) {
      maxAgentsCount = ValueWithDefault.decideDefault(fields.isIncluded("maxAgentsCount"), licenseList.getLicensedAgentCount());
    }

    final LicensingPolicyEx licensingPolicy = licenseKeysManager.getLicensingPolicy();

    serverLicenseMode = ValueWithDefault.decideDefault(fields.isIncluded("serverLicenseMode"), getMode(licenseList));

    serverReleaseDate = ValueWithDefault.decideDefault(fields.isIncluded("serverReleaseDate"), Util.formatTime(getReleaseDate(beanContext)));

    if (licensingPolicy.getAgentsLicensesLeft() != -1) {
      agentsLeftCount = ValueWithDefault.decideDefault(fields.isIncluded("agentsLeftCount"), licensingPolicy.getAgentsLicensesLeft());
    }

    if (licensingPolicy.getBuildTypesLicensesLeft() != -1) {
      buildTypesLeftCount = ValueWithDefault.decideDefault(fields.isIncluded("buildTypesLeftCount"), licensingPolicy.getBuildTypesLicensesLeft());
    }
  }

  private Date getReleaseDate(final BeanContext beanContext) {
    return null; //todo fix!  effective release date
//    return new ServerLicenseBean().getReleaseDate(); //should now use ReleaseDateHolder.getReleaseDate ?
  }

  private String getMode(final LicenseList licenseList) {
    if (licenseList.isEvaluationMode()) {
      return LicenseKeyEntity.LICENSE_TYPE_EVALUATION;
    }

    if (licenseList.isEAPEvaluationMode()) {
      return LicenseKeyEntity.LICENSE_TYPE_EAP;
    }

    if (licenseList.isOpenSourceMode()) {
      return LicenseKeyEntity.LICENSE_TYPE_OPEN_SOURCE;
    }

    return LicenseKeyEntity.LICENSE_TYPE_COMMERCIAL;
  }
}
