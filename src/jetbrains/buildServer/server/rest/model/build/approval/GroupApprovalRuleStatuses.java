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

import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.impl.ApprovalRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("PublicField")
@XmlRootElement(name = "groupApprovals")
@ModelBaseType(ObjectType.LIST)
public class GroupApprovalRuleStatuses {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "groupApproval")
  public List<GroupApprovalRuleStatus> groupApprovalRuleStatuses;

  public GroupApprovalRuleStatuses() {
  }

  public GroupApprovalRuleStatuses(
    @NotNull BuildPromotionEx buildPromotionEx,
    @Nullable final List<ApprovalRule> rules,
    @NotNull final Fields fields,
    @NotNull final BeanContext beanContext
  ) {
    if (rules != null) {
      groupApprovalRuleStatuses = ValueWithDefault.decideDefault(
        fields.isIncluded("groupApproval", false, true),
        () -> {
          return rules.stream()
                      .map(rule -> new GroupApprovalRuleStatus(buildPromotionEx, rule, fields.getNestedField("groupApproval"), beanContext))
                      .collect(Collectors.toList());
        }
      );
      count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), rules.size());
    }
  }
}