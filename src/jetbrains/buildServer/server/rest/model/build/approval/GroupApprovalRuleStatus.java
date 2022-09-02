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

package jetbrains.buildServer.server.rest.model.build.approval;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.group.Group;
import jetbrains.buildServer.server.rest.model.user.Users;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.impl.approval.ApprovableBuildManager;
import jetbrains.buildServer.serverSide.impl.approval.ApprovalRule;
import org.jetbrains.annotations.NotNull;

@ModelDescription("Represents group approval rule and its current status for the given build.")
@XmlRootElement(name = "groupApprovalRule")
public class GroupApprovalRuleStatus {
  @NotNull protected BuildPromotionEx myBuildPromotionEx;
  @NotNull protected ApprovalRule myApprovalRule;
  @NotNull protected Fields myFields;
  @NotNull protected BeanContext myBeanContext;
  @NotNull protected ApprovableBuildManager myApprovableBuildManager;

  public GroupApprovalRuleStatus(
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

  public GroupApprovalRuleStatus() {
  }

  @XmlElement(name = "group")
  public Group getRuleGroup() {
    if (myFields.isIncluded("group", true, true)) {
      return new Group(
        (SUserGroup)myApprovalRule.getAuthorityHolder(),
        myFields.getNestedField("group", Fields.SHORT, Fields.SHORT),
        myBeanContext
      );
    }
    return null;
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
        myFields.getNestedField("currentlyApprovedBy", Fields.SHORT, Fields.LONG),
        myBeanContext
      );
    }
    return null;
  }
}
