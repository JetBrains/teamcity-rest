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

package jetbrains.buildServer.server.rest.model.build;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.finder.impl.UserFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.user.User;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.TagData;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.impl.auth.ServerAuthUtil;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 26.11.2014
 */
@SuppressWarnings("PublicField")
@XmlType(name = "tag")
@XmlRootElement(name = "tag")
@ModelDescription(
    value = "Represents a single build tag.",
    externalArticleLink = "https://www.jetbrains.com/help/teamcity/build-tag.html",
    externalArticleName = "Tagging Build"
)
public class Tag {
  @XmlAttribute public String name;
  @XmlAttribute(name = "private") public Boolean privateTag;

  /**
   * Is present only for private tags
   */
  @XmlElement public User owner;

  public Tag() {
  }

  public Tag(final @NotNull String tagName,
             final @Nullable jetbrains.buildServer.users.User owner,
             final @NotNull Fields fields,
             final @NotNull BeanContext beanContext) {
    this.name = ValueWithDefault.decideDefault(fields.isIncluded("name"), tagName);
    this.privateTag = ValueWithDefault.decideDefault(fields.isIncluded("private"), owner != null);
    this.owner = ValueWithDefault.decideDefault(
      fields.isIncluded("owner", false, true),
      () -> resolveUser((SUser) owner, fields.getNestedField("owner"), beanContext)
    );
  }

  @Nullable
  private static User resolveUser(@Nullable SUser owner, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    if(owner == null) {
      return null;
    }

    AuthorityHolder requestor = beanContext.getSingletonService(SecurityContext.class).getAuthorityHolder();
    // We don't want to reveal owners of private tags to everybody, see TW-50197.
    if(!ServerAuthUtil.canEditUser(requestor, owner)) {
      return null;
    }

    return new User(owner, fields, beanContext);
  }

  public TagData getFromPosted(final UserFinder userFinder) {
    if (name == null) {
      throw new BadRequestException("Tag name should not be empty");
    }

    if (owner != null) {
      if (privateTag != null && !privateTag){
        throw new BadRequestException("Owner is specified for not private tag");
      }
      return TagData.createPrivateTag(name, owner.getFromPosted(userFinder));
    }
    if (privateTag != null && privateTag){
      final SUser currentUser = userFinder.getCurrentUser();
      if (currentUser != null){
        return TagData.createPrivateTag(name, currentUser);
      }
      throw new BadRequestException("Owner is not specified for not private tag and there is no current user");
    }

    return TagData.createPublicTag(name);
  }
}
