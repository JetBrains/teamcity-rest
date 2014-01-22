/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "groups")
@XmlType(name = "groups")
public class Groups {
  @XmlElement(name = "group")
  public List<GroupRef> groups;

  public Groups() {
  }

  public Groups(Collection<UserGroup> userGroups, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    groups = new ArrayList<GroupRef>(userGroups.size());
    for (UserGroup userGroup : userGroups) {
      groups.add(new GroupRef(userGroup, apiUrlBuilder));
    }
  }

  @NotNull
  public List<SUserGroup> getFromPosted(final ServiceLocator serviceLocator) {
    if (groups == null) {
      throw new BadRequestException("No groups elements is supplied for the groups list.");
    }
    return CollectionsUtil.convertCollection(groups, new Converter<SUserGroup, GroupRef>() {
      public SUserGroup createFrom(@NotNull final GroupRef source) {
        return source.getFromPosted(serviceLocator);
      }
    });
  }
}
