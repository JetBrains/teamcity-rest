package jetbrains.buildServer.server.rest.model.build;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.user.Users;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.impl.ApprovableBuildManager;
import jetbrains.buildServer.serverSide.impl.ApprovalRule;
import org.jetbrains.annotations.NotNull;


@XmlType(propOrder = {"definition", "authorityHolder", "requiredApprovalsCount", "currentlyApprovedBy"})
@XmlRootElement(name = "approvalRule")
@ModelDescription("Represents approval rule and its current status for the given build.")
public class ApprovalRuleStatus {
  @NotNull private BuildPromotionEx myBuildPromotionEx;
  @NotNull private ApprovalRule myApprovalRule;
  @NotNull private Fields myFields;
  @NotNull private BeanContext myBeanContext;
  @NotNull private ApprovableBuildManager myApprovableBuildManager;

  public ApprovalRuleStatus(
    @NotNull final BuildPromotionEx buildPromotionEx,
    @NotNull final ApprovalRule approvalRule,
    @NotNull final Fields fields,
    @NotNull final BeanContext beanContext
  ) {
    myBuildPromotionEx = buildPromotionEx;
    myApprovalRule = approvalRule;
    myFields = fields;
    myBeanContext = beanContext;
    myApprovableBuildManager = myBeanContext.getSingletonService(ApprovableBuildManager.class);
  }

  public ApprovalRuleStatus() {
  }

  @XmlAttribute(name = "definition")
  public String getDefinition() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("definition"), () ->
        myApprovalRule.describeRule()
    );
  }

  @XmlAttribute(name = "authorityHolder")
  public String getAuthorityHolder() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("authorityHolder"), () ->
        myApprovalRule.describeAuthorityHolder()
    );
  }

  @XmlAttribute(name = "requiredApprovalsCount")
  public Integer getRequiredApprovalsCount() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("requiredApprovalsCount"), () ->
        myApprovalRule.approvalsCount()
    );
  }

  @XmlElement(name = "currentlyApprovedBy")
  public Users getCurrentlyApprovedBy() {
    if (myFields.isIncluded("currentlyApprovedBy", true, true)) {
      return new Users(
        myApprovalRule.matchingUsers(
          myApprovableBuildManager.getApprovedByUsers(myBuildPromotionEx)
        ),
        myFields.getNestedField("currentlyApprovedBy", Fields.LONG, Fields.LONG),
        myBeanContext
      );
    }
    return null;
  }
}
