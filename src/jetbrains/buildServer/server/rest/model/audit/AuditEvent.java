/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.audit;

import com.intellij.openapi.util.text.StringUtil;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.RelatedEntities;
import jetbrains.buildServer.server.rest.model.RelatedEntity;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.user.User;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.audit.ActionType;
import jetbrains.buildServer.serverSide.audit.AuditLogAction;
import jetbrains.buildServer.serverSide.comments.Comment;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "auditEvent")
@XmlType(name = "auditEvent")
public class AuditEvent {
  @XmlAttribute
  public String id;

  //see also Comment
  @XmlElement
  public String timestamp;

  @XmlElement(name = "user")
  public User user;

  @XmlElement
  public String text;

  @XmlElement
  public AuditAction action;

  @XmlElement
  public RelatedEntities relatedEntities;

  public AuditEvent() {
  }

  public AuditEvent(@NotNull final AuditLogAction actionP, @NotNull final Fields fields, @NotNull final BeanContext context) {
    final Comment comment = actionP.getComment();
    id = ValueWithDefault.decideDefault(fields.isIncluded("id"), () -> String.valueOf(comment.getCommentId()));
    timestamp = ValueWithDefault.decideDefault(fields.isIncluded("timestamp"), Util.formatTime(comment.getTimestamp()));
    user = ValueWithDefault.decideDefault(fields.isIncluded("user"), () -> {
      if (comment.getUser() != null) return new User(comment.getUser(), fields.getNestedField("user", Fields.SHORT, Fields.SHORT), context);
      long userId = actionP.getUserId();
      return userId == -1 ? null : new User(userId, fields.getNestedField("user", Fields.SHORT, Fields.SHORT), context);
    });
    text = ValueWithDefault.decideDefault(fields.isIncluded("text"), () -> StringUtil.isEmpty(comment.getComment()) ? null : comment.getComment());

    final ActionType actionType = actionP.getActionType();

    action = ValueWithDefault.decideDefault(fields.isIncluded("action"), () ->
      new AuditAction(actionType, fields.getNestedField("action", Fields.SHORT, Fields.SHORT), context));

    relatedEntities = ValueWithDefault.decideDefault(fields.isIncluded("relatedEntities"), () ->
          new RelatedEntities(getEntities(actionP, context.getServiceLocator()), fields.getNestedField("relatedEntities", Fields.LONG, Fields.LONG), context));
  }

  @NotNull
  private List<RelatedEntity.Entity> getEntities(@NotNull final AuditLogAction auditLogAction, @NotNull final ServiceLocator serviceLocator) {
    return Arrays.stream(auditLogAction.getObjects()).map(objectWrapper -> {
      try {
        return RelatedEntity.Entity.getFrom(objectWrapper, serviceLocator);
      } catch (Exception e) {
        throw new OperationException("Error getting details of audit action with id " + auditLogAction.getComment().getCommentId() + ", object: " + objectWrapper.getObjectType() + "/" + objectWrapper.getObjectId(), e);
      }
    }).filter(Objects::nonNull).collect(Collectors.toList());
  }
}