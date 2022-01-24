/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.request.ServerRequest;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.LicenseKeysManager;
import jetbrains.buildServer.serverSide.LicenseList;
import jetbrains.buildServer.serverSide.LicensingPolicyEx;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 05.05.2014
 */
@SuppressWarnings({"PublicField", "WeakerAccess"})
@XmlRootElement(name = "licensingData")
@XmlType(name = "licensingData")
@ModelDescription("Represents license state details (available build configurations, agents, etc.).")
public class LicensingData {
  @XmlAttribute
  public Boolean licenseUseExceeded;

  @XmlAttribute
  public Integer maxAgents;

  @XmlAttribute
  public Boolean unlimitedAgents;

  private Integer agentsLeft;

  @XmlAttribute
  public Integer maxBuildTypes;

  @XmlAttribute
  public Boolean unlimitedBuildTypes;

  @XmlAttribute
  public Integer buildTypesLeft;

  @XmlAttribute
  public LicenseType serverLicenseType;

  /**
   * Effective release date of the server (the date which is compared to license's maintenance end date)
   */
  @XmlAttribute
  public String serverEffectiveReleaseDate;


  @XmlElement(name = "licenseKeys")
  public LicenseKeyEntities licenseKeys;

  //todo: check getActiveLicensesNum() is visible in the keys list

  private Fields myFields;

  public LicensingData() {
  }

  public LicensingData(final @NotNull LicenseKeysManager licenseKeysManager, final @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    myFields = fields;

    initLicenceListDependantFields(licenseKeysManager, fields, beanContext);
    initLicensingPolicyDependantFields(licenseKeysManager, fields, beanContext);
  }

  private void initLicenceListDependantFields(@NotNull LicenseKeysManager licenseKeysManager, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    LicenseList licenseList;
    try {
      licenseList = licenseKeysManager.getLicenseList();

    } catch (Throwable ignored) {
      // Leave all fields uninitialized
      return;
    }

    this.licenseKeys = ValueWithDefault.decideDefault(
      fields.isIncluded("licenseKeys"),
      () -> new LicenseKeyEntities(licenseList.getAllLicenses(), licenseList.getActiveLicenses(), ServerRequest.getLicenseKeysListHref(), fields.getNestedField("licenseKeys", Fields.SHORT, Fields.LONG), beanContext)
    );

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

    serverLicenseType = ValueWithDefault.decideDefault(fields.isIncluded("serverLicenseType"), getServerLicenseType(licenseList));

    serverEffectiveReleaseDate = ValueWithDefault.decideDefault(fields.isIncluded("serverEffectiveReleaseDate"), Util.formatTime(licenseList.getReleaseDate()));
  }

  private void initLicensingPolicyDependantFields(@NotNull LicenseKeysManager licenseKeysManager, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    final LicensingPolicyEx licensingPolicy = licenseKeysManager.getLicensingPolicy();
    final PermissionChecker permissionChecker = beanContext.getSingletonService(PermissionChecker.class);

    licenseUseExceeded = ValueWithDefault.decideDefaultIgnoringAccessDenied(
      fields.isIncluded("licenseUseExceeded"),
      () -> {
        permissionChecker.checkGlobalPermission(Permission.MANAGE_SERVER_LICENSES);
        return licensingPolicy.isMaxNumberOfBuildTypesExceeded();
      }
    );

    agentsLeft = ValueWithDefault.decideIncludeByDefault(
      myFields.isIncluded("agentsLeft"),
      () -> {
        if(permissionChecker.hasGlobalPermission(Permission.MANAGE_SERVER_LICENSES) || permissionChecker.hasGlobalPermission(Permission.VIEW_AGENT_DETAILS))
          return licensingPolicy.getAgentsLicensesLeft();

        return null;
      }
    );

    buildTypesLeft = ValueWithDefault.decideDefaultIgnoringAccessDenied(
      fields.isIncluded("buildTypesLeft"),
      () -> {
        permissionChecker.checkGlobalPermission(Permission.MANAGE_SERVER_LICENSES);
        int result = licensingPolicy.getBuildTypesLicensesLeft();
        return result == -1 ? null : result;
      }
    );
  }

  @Nullable
  @XmlAttribute(name = "agentsLeft")
  public Integer getAgentsLeft() {
    return ValueWithDefault.decideIncludeByDefault(myFields.isIncluded("agentsLeft"), agentsLeft);
  }

  public void setAgentsLeft(@Nullable Integer agentsLeft) {
    this.agentsLeft = agentsLeft;
  }

  protected static final String SERVER_LICENSE_TYPE_ENTERPRISE = "enterprise";
  protected static final String SERVER_LICENSE_TYPE_PROFESSIONAL = "professional";
  protected static final String SERVER_LICENSE_TYPE_EVALUATION = LicenseKeyEntity.LICENSE_TYPE_EVALUATION;
  protected static final String SERVER_LICENSE_TYPE_EAP = LicenseKeyEntity.LICENSE_TYPE_EAP;
  protected static final String SERVER_LICENSE_TYPE_OPEN_SOURCE = LicenseKeyEntity.LICENSE_TYPE_OPEN_SOURCE;

  /**
   * See also {@link jetbrains.buildServer.server.rest.model.server.LicenseKeyEntity#getLicenseType(jetbrains.buildServer.serverSide.LicenseKey)}
   */
  private LicenseType getServerLicenseType(final LicenseList licenseList) {
    if (licenseList.isEvaluationMode()) {
      return LicenseType.evaluation;
    }

    if (licenseList.isEAPEvaluationMode()) {
      return LicenseType.eap;
    }

    if (licenseList.isOpenSourceMode()) {
      return LicenseType.open_source;
    }

    if (licenseList.hasEnterpriseLicense()) {
      return LicenseType.enterprise;
    }

    return LicenseType.professional;
  }
}
