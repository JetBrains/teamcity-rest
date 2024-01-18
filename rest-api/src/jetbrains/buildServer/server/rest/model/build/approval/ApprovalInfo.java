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

package jetbrains.buildServer.server.rest.model.build.approval;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.finder.impl.UserFinder;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.impl.approval.*;
import jetbrains.buildServer.serverSide.userChanges.CanceledInfo;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@XmlRootElement(name = "approvalInfo")
@ModelDescription("Represents approval status for this build, if applicable.")
public class ApprovalInfo {
  @NotNull private BuildPromotionEx myBuildPromotionEx;
  @NotNull private Fields myFields;
  @NotNull private BeanContext myBeanContext;
  @NotNull private ApprovableBuildManager myApprovableBuildManager;
  @NotNull private Boolean myHasApprovalRules;
  @Nullable private ApprovalBuildFeatureConfiguration myConfiguration;

  public ApprovalInfo(@NotNull final BuildPromotionEx buildPromotionEx,
                      @NotNull final Fields fields,
                      @NotNull final BeanContext beanContext) {
    myBuildPromotionEx = buildPromotionEx;
    myFields = fields;
    myBeanContext = beanContext;
    myApprovableBuildManager = beanContext.getSingletonService(ApprovableBuildManager.class);
    myHasApprovalRules = myApprovableBuildManager.hasApprovalRules(buildPromotionEx);
    if (myHasApprovalRules) {
      myConfiguration = myApprovableBuildManager.getApprovalConfiguration(myBuildPromotionEx);
    }
  }

  public ApprovalInfo() {
  }

  public enum ApprovalStatus {
    waitingForApproval, approved, timedOut, canceled;

    public static ApprovalStatus resolve(
      BuildPromotionEx buildPromotionEx,
      ApprovableBuildManager approvableBuildManager
    ) {
      if (approvableBuildManager.allApprovalRulesAreMet(buildPromotionEx)) {
        return approved;
      }

      SBuild associatedBuild = buildPromotionEx.getAssociatedBuild();
      if (associatedBuild != null) {
        CanceledInfo canceledInfo = associatedBuild.getCanceledInfo();
        if (canceledInfo != null && canceledInfo.isCanceledByUser()) {
          return canceled;
        }
      }

      if (approvableBuildManager.hasTimedOut(buildPromotionEx)) {
        return timedOut;
      }

      return waitingForApproval;
    }
  }

  @XmlAttribute(name = "status")
  public ApprovalStatus getStatus() {
    if (!myFields.isIncluded("status", true, true)) {
      return null;
    }

    return ApprovalStatus.resolve(myBuildPromotionEx, myApprovableBuildManager);
  }

  @XmlAttribute(name = "timeoutTimestamp")
  public String getTimeoutTimestamp() {
    Long timestamp = myApprovableBuildManager.getTimeout(myBuildPromotionEx);
    if (timestamp != null) {
      return ValueWithDefault.decideDefault(
        myFields.isIncluded("timeoutTimestamp"), () ->
          Util.formatTime(
            new Date(
              timestamp * 1000 // milliseconds for Date constructor
            )
          )
      );
    }
    return null;
  }

  @XmlAttribute(name = "configurationValid")
  public Boolean getConfigurationValid() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("configurationValid"), () ->
        myConfiguration != null ? myConfiguration.areApprovalRulesValid() : false
    );
  }

  @XmlAttribute(name = "canBeApprovedByCurrentUser")
  public Boolean getCanBeApprovedByCurrentUser() {
    if (getStatus() != ApprovalStatus.waitingForApproval) {
      return false;
    }
    SUser currentUser = myBeanContext.getSingletonService(UserFinder.class).getCurrentUser();
    if (myFields.isIncluded("canBeApprovedByCurrentUser", false, true)) {
      if (myApprovableBuildManager.areApprovalRulesValid(myBuildPromotionEx)) {
        return myApprovableBuildManager.isApprovableByUser(myBuildPromotionEx, currentUser);
      }
      return false;
    }
    return null;
  }

  @XmlElement(name = "userApprovals")
  public UserApprovalRuleStatuses getUserApprovalRuleStatuses() {
    if (myFields.isIncluded("userApprovals", true, true)) {
      if (!myHasApprovalRules) {
        return null;
      }
      try { // return empty list of rule statuses if user is not entitled to see build configuration settings
        myBeanContext.getServiceLocator().findSingletonService(PermissionChecker.class)
                     .checkProjectPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, myBuildPromotionEx.getProjectId());
      } catch (AuthorizationFailedException e) {
        return null;
      }

      try {
        List<ApprovalRule> userRules = myConfiguration
          .getApprovalRules() // asserted by myApprovalFeatureEnabled
          .stream()
          .filter(rule -> rule instanceof UserApprovalRule)
          .collect(Collectors.toList());
        return new UserApprovalRuleStatuses(
          myBuildPromotionEx,
          userRules,
          myFields.getNestedField("userApprovals", Fields.LONG, Fields.LONG),
          myBeanContext
        );
      } catch (InvalidApprovalRuleException e) {
        return null; // act as if there are no rules at all
      }

    }
    return null;
  }

  @XmlElement(name = "groupApprovals")
  public GroupApprovalRuleStatuses getGroupApprovalRuleStatuses() {
    if (myFields.isIncluded("groupApprovals", true, true)) {
      if (!myHasApprovalRules) {
        return null;
      }

      try { // return empty list of rule statuses if user is not entitled to see build configuration settings
        myBeanContext.getServiceLocator().findSingletonService(PermissionChecker.class)
                     .checkProjectPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, myBuildPromotionEx.getProjectId());
      } catch (AuthorizationFailedException e) {
        return null;
      }

      try {
        List<ApprovalRule> groupRules = myConfiguration
          .getApprovalRules() // asserted by myApprovalFeatureEnabled
          .stream()
          .filter(rule -> rule instanceof GroupApprovalRule)
          .collect(Collectors.toList());
        return new GroupApprovalRuleStatuses(
          myBuildPromotionEx,
          groupRules,
          myFields.getNestedField("groupApprovals", Fields.LONG, Fields.LONG),
          myBeanContext
        );
      } catch (InvalidApprovalRuleException e) {
        return null; // act as if there are no rules at all
      }
    }
    return null;
  }
}