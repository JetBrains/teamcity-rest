package jetbrains.buildServer.server.rest.model.build;

import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.impl.ApprovableBuildManager;
import jetbrains.buildServer.serverSide.impl.ApprovalBuildFeatureConfiguration;
import jetbrains.buildServer.serverSide.impl.ApprovalRule;
import org.jetbrains.annotations.NotNull;


@XmlType(propOrder = {"status", "timeoutTimestamp", "configurationValid", "approvalRuleStatuses"})
@XmlRootElement(name = "approvalInfo")
@ModelDescription("Represents approval status for this build, if applicable.")
public class ApprovalInfo {
  @NotNull private BuildPromotionEx myBuildPromotionEx;
  @NotNull private Fields myFields;
  @NotNull private BeanContext myBeanContext;
  @NotNull private ApprovableBuildManager myApprovableBuildManager;
  @NotNull private Optional<SBuildFeatureDescriptor> myDescriptor;

  public ApprovalInfo(@NotNull final BuildPromotionEx buildPromotionEx,
                      @NotNull final Fields fields,
                      @NotNull final BeanContext beanContext) {
    myBuildPromotionEx = buildPromotionEx;
    myFields = fields;
    myBeanContext = beanContext;
    myApprovableBuildManager = beanContext.getSingletonService(ApprovableBuildManager.class);
    myDescriptor = myApprovableBuildManager.getApprovalFeature(myBuildPromotionEx);
  }

  public ApprovalInfo() {
  }

  public enum ApprovalStatus {
    notApproved, approved, timedOut
  }

  @XmlAttribute(name = "status")
  public ApprovalStatus getStatus() {
    if (!myFields.isIncluded("status", true, true)) {
      return null;
    }

    if (myApprovableBuildManager.hasTimedOut(myBuildPromotionEx)) {
      return ApprovalStatus.timedOut;
    }

    if (myApprovableBuildManager.allApprovalRulesAreMet(myBuildPromotionEx)) {
      return ApprovalStatus.approved;
    }

    return ApprovalStatus.notApproved;
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
    ApprovalBuildFeatureConfiguration configuration = myApprovableBuildManager.getApprovalBuildFeatureConfiguration(myDescriptor);
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("configurationValid"), () ->
        configuration != null ? configuration.areApprovalRulesValid() : false
    );
  }

  @XmlElement(name = "approvalRuleStatuses")
  public ApprovalRuleStatuses getApprovalRuleStatuses() {
    if (myFields.isIncluded("approvalRuleStatuses", true, true)) {
      try { // return empty list of rule statuses if user is not entitled to see build configuration settings
        myBeanContext.getServiceLocator().findSingletonService(PermissionChecker.class)
                     .checkProjectPermission(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, myBuildPromotionEx.getProjectId());
      } catch (AuthorizationFailedException e) {
        return null;
      }

      if (myDescriptor.isPresent()) {
        try {
          return new ApprovalRuleStatuses(
            myBuildPromotionEx,
            myApprovableBuildManager.getApprovalBuildFeatureConfiguration(myDescriptor).getApprovalRules(), // asserted by descriptor.isPresent
            myFields.getNestedField("approvalRuleStatuses", Fields.LONG, Fields.LONG),
            myBeanContext
          );
        } catch (ApprovalBuildFeatureConfiguration.InvalidApprovalRuleException e) {
          return null; // act as if there are no rules at all
        }
      }
    }
    return null;
  }
}
