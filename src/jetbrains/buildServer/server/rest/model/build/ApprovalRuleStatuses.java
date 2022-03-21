package jetbrains.buildServer.server.rest.model.build;

import jetbrains.buildServer.server.rest.data.BranchData;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.impl.ApprovalRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("PublicField")
@XmlRootElement(name = "approvalRuleStatuses")
@ModelBaseType(ObjectType.LIST)
public class ApprovalRuleStatuses {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "approvalRuleStatus")
  public List<ApprovalRuleStatus> approvalRuleStatuses;

  public ApprovalRuleStatuses() {
  }

  public ApprovalRuleStatuses(
    @NotNull BuildPromotionEx buildPromotionEx,
    @Nullable final List<ApprovalRule> rules,
    @NotNull final Fields fields,
    @NotNull final BeanContext beanContext
  ) {
    if (rules != null) {
      approvalRuleStatuses = ValueWithDefault.decideDefault(
        fields.isIncluded("approvalRuleStatus"),
        () -> {
          Fields branchFields = fields.getNestedField("approvalRuleStatus");
          return rules.stream()
                      .map(rule -> new ApprovalRuleStatus(buildPromotionEx, rule, branchFields, beanContext))
                      .collect(Collectors.toList());
        }
      );
      count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), rules.size());
    }
  }
}