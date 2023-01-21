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

package jetbrains.buildServer.server.rest.model.user;

import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.finder.impl.PermissionAssignmentFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 * Date: 18/09/2017
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "permissionAssignments")
@XmlType(name = "permissionAssignments")
@ModelBaseType(ObjectType.LIST)
public class PermissionAssignments {
  @XmlElement(name = "permissionAssignment")
  public List<PermissionAssignment> myPermissionAssignments;

  @XmlAttribute
  public Integer count;

  public PermissionAssignments() {
  }

  public PermissionAssignments(@NotNull final AuthorityHolder authorityHolder,
                               @Nullable final String locator,
                               @NotNull final Fields fields,
                               @NotNull BeanContext beanContext) {
    PermissionAssignmentFinder permissionAssignmentFinder = new PermissionAssignmentFinder(authorityHolder, beanContext.getServiceLocator());
    String locatorFromFields = fields.getLocator();
    String finalLocator = locatorFromFields != null ? locatorFromFields : locator;

    myPermissionAssignments = ValueWithDefault.decideDefault(
      fields.isIncluded("permissionAssignment"),
      () -> {
        Fields assignmentFields = fields.getNestedField("permissionAssignment");
        return permissionAssignmentFinder.getItems(finalLocator).getEntries()
          .stream()
          .map(p -> new PermissionAssignment(p, assignmentFields, beanContext))
          .collect(Collectors.toList());
      }
    );

    count = ValueWithDefault.decideIncludeByDefault(
      fields.isIncluded("count"),
      () -> myPermissionAssignments != null ? myPermissionAssignments.size() : permissionAssignmentFinder.getItems(finalLocator).getEntries().size()
    );
  }
}
