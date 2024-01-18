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

package jetbrains.buildServer.server.rest.model.group;

import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "groups")
@XmlType(name = "groups")
@ModelBaseType(ObjectType.LIST)
public class Groups {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "group")
  public List<Group> groups;

  public Groups() {
  }

  public Groups(Collection<UserGroup> userGroups, @NotNull final Fields fields, @NotNull final BeanContext context) {
    if (fields.isIncluded("group", false, true)) {
      groups = new ArrayList<Group>(userGroups.size());
      final Fields nestedFields = fields.getNestedField("group");
      for (UserGroup userGroup : userGroups) {
        groups.add(new Group((SUserGroup)userGroup, nestedFields, context)); //TeamCity API issue: cast
      }
    }
    count = userGroups == null ? null : ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), userGroups.size());
  }

  @NotNull
  public List<SUserGroup> getFromPosted(final ServiceLocator serviceLocator) {
    if (groups == null) {
      //no nested group element - empty collection
      return Collections.emptyList();
    }
    return CollectionsUtil.convertCollection(groups, new Converter<SUserGroup, Group>() {
      public SUserGroup createFrom(@NotNull final Group source) {
        return source.getFromPosted(serviceLocator);
      }
    });
  }
}