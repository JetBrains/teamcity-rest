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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.user.User;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.impl.approval.ApprovableBuildManager;
import jetbrains.buildServer.serverSide.impl.approval.ApprovalRule;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

@ModelDescription("Represents user approval rule and its current status for the given build.")
@XmlRootElement(name = "userApprovalRule")
public class UserApprovalRuleStatus {
  @NotNull protected BuildPromotionEx myBuildPromotionEx;
  @NotNull protected ApprovalRule myApprovalRule;
  @NotNull protected Fields myFields;
  @NotNull protected BeanContext myBeanContext;
  @NotNull protected ApprovableBuildManager myApprovableBuildManager;

  public UserApprovalRuleStatus(
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

  public UserApprovalRuleStatus() {
  }

  @XmlElement(name = "user")
  public User getUser() {
    if (myFields.isIncluded("user", true, true)) {
      return new User(
        (SUser)myApprovalRule.getAuthorityHolder(),
        myFields.getNestedField("user", Fields.SHORT, Fields.SHORT),
        myBeanContext
      );
    }
    return null;
  }

  @XmlElement(name = "approved")
  public Boolean getApproved() {
    if (myFields.isIncluded("approved", true, true)) {
      return myApprovalRule.isMet(
        myApprovableBuildManager.getApprovedByUsers(myBuildPromotionEx)
      );
    }
    return null;
  }
}