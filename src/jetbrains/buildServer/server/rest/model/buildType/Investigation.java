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

package jetbrains.buildServer.server.rest.model.buildType;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.model.Comment;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.problem.Resolution;
import jetbrains.buildServer.server.rest.model.user.User;
import jetbrains.buildServer.server.rest.request.InvestigationRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlType(name = "investigation")
@XmlRootElement(name = "investigation")
public class Investigation {
  protected static final String REST_BEANS_INVESTIGATIONS_COMPATIBILITY = "rest.beans.buildTypeInvestigationCompatibility";
  @XmlAttribute public String id;
  @XmlAttribute public String state;
  @XmlAttribute public String href;

  @XmlElement public User assignee;
  @XmlElement public Comment assignment;
  @XmlElement public ProblemScope scope;
  @XmlElement public ProblemTarget target;
  @XmlElement public Resolution resolution;

  /**
   * Used only in compatibility mode
   *
   * @deprecated
   */
  @XmlElement public User responsible;

  public Investigation() {
  }

  public Investigation(final @NotNull InvestigationWrapper investigation,
                       final @NotNull Fields fields,
                       final @NotNull BeanContext beanContext) {
    final ResponsibilityEntry.State stateOjbect = investigation.getState();
    state = ValueWithDefault.decideDefault(fields.isIncluded("state"), stateOjbect.name());
    if (stateOjbect.equals(ResponsibilityEntry.State.NONE)) {
      return;
    }

    id = ValueWithDefault.decideDefault(fields.isIncluded("id"), investigation.getId());
    href = ValueWithDefault.decideDefault(fields.isIncluded("href"), InvestigationRequest.getHref(investigation));

    target = ValueWithDefault.decideDefault(fields.isIncluded("target"), new ValueWithDefault.Value<ProblemTarget>() {
      public ProblemTarget get() {
        return new ProblemTarget(investigation, fields.getNestedField("target", Fields.NONE, Fields.LONG), beanContext);
      }
    });
    scope = ValueWithDefault.decideDefault(fields.isIncluded("scope"), new ValueWithDefault.Value<ProblemScope>() {
      public ProblemScope get() {
        return new ProblemScope(investigation, fields.getNestedField("scope", Fields.NONE, Fields.LONG), beanContext);
      }
    });
    assignee = ValueWithDefault.decideDefault(fields.isIncluded("assignee"), new ValueWithDefault.Value<User>() {
      public User get() {
        return new User(investigation.getResponsibleUser(), fields.getNestedField("assignee"), beanContext);
      }
    });

    assignment = ValueWithDefault.decideDefault(fields.isIncluded("assignment"), new ValueWithDefault.Value<Comment>() {
      public Comment get() {
        return new Comment(investigation.getReporterUser(), investigation.getTimestamp(), investigation.getComment(), fields.getNestedField("assignment", Fields.NONE, Fields.LONG),
                           beanContext);
      }
    });
    resolution = new Resolution(investigation.getRemoveMethod(), fields.getNestedField("resolution", Fields.NONE, Fields.LONG));

    //support for pre-8.1
    if (TeamCityProperties.getBoolean(Investigation.REST_BEANS_INVESTIGATIONS_COMPATIBILITY)) {
      responsible = assignee;
    }
  }
}
