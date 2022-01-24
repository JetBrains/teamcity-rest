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

package jetbrains.buildServer.server.rest.model.audit;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.audit.ActionType;
import jetbrains.buildServer.serverSide.audit.AuditLogAction;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 * Date: 02/05/2018
 */
@XmlRootElement(name = "auditAction")
@XmlType(name = "auditAction")
@ModelDescription(
    value = "Represents an audit action.",
    externalArticleLink = "https://www.jetbrains.com/help/teamcity/tracking-user-actions.html",
    externalArticleName = "Audit"
)
public class AuditAction {
  /**
   * Note that name can change from one TeamCity version to another
   */
  @XmlAttribute
  public String name;

  @XmlAttribute
  public String id;

  /**
   * This is a text pattern which can be used to render human-friendly text. It regularly contains "{N}" strings to be replaces with human-friendly details of corresponding entity from auditEvent relatedEntities
   */
  @XmlElement
  public String pattern;

  /*@XmlElement public String text; //consider adding this with custom plain-text rendering of the entities*/

  public AuditAction() {
  }

  public AuditAction(@NotNull final AuditLogAction action, @NotNull final Fields fields, @NotNull final BeanContext context) {
    final ActionType actionType = action.getActionType();
    id = ValueWithDefault.decideDefault(fields.isIncluded("id", false, false), () -> String.valueOf(actionType.getDBId()));
    name = ValueWithDefault.decideDefault(fields.isIncluded("name"), () -> actionType.name().toLowerCase()); //todo: add a test that no action names are changed
    pattern = ValueWithDefault.decideDefault(fields.isIncluded("pattern", false, true), actionType::getDescription);

    /*
    text = ValueWithDefault.decideDefault(fields.isIncluded("text", false, false),
                                          () -> AuditUtil.formatAdditionalObjects(getLog4jObjectDescription(action),
                                                                                  actionType,
                                                                                  Stream.of(action.getObjects()).skip(1).map(ObjectWrapper::getObject).toArray()));
    */
  }

  /*
  @NotNull
  private String getLog4jObjectDescription(@NotNull final AuditLogAction action) {
    Object object = action.getObject();
    if (object == null) {
      return action.getObjectType().getLog4jDescriptionById(action.getObjectId());
    }
    else {
      return action.getObjectType().getLog4jDescription(object);
    }
  }

  */
}
