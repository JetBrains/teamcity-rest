/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.BuildType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.responsibility.ResponsibilityEntryEx;
import jetbrains.buildServer.responsibility.ResponsibilityFacadeEx;
import jetbrains.buildServer.server.rest.data.TypedFinderBuilder;
import jetbrains.buildServer.server.rest.data.UserFinder;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationFinder;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Comment;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.problem.Resolution;
import jetbrains.buildServer.server.rest.model.user.User;
import jetbrains.buildServer.server.rest.request.InvestigationRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STest;
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
    try {
      href = ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().transformRelativePath(InvestigationRequest.getHref(investigation)));
    } catch (Exception e) {
      //ignore: InvestigationFinder.getLocator can throw exception
    }

    target = ValueWithDefault.decideDefault(fields.isIncluded("target", false), new ValueWithDefault.Value<ProblemTarget>() {
      public ProblemTarget get() {
        return new ProblemTarget(investigation, fields.getNestedField("target", Fields.NONE, Fields.LONG), beanContext);
      }
    });
    scope = ValueWithDefault.decideDefault(fields.isIncluded("scope", false), new ValueWithDefault.Value<ProblemScope>() {
      public ProblemScope get() {
        return new ProblemScope(investigation, fields.getNestedField("scope", Fields.NONE, Fields.LONG), beanContext);
      }
    });
    assignee = ValueWithDefault.decideDefault(fields.isIncluded("assignee", false), new ValueWithDefault.Value<User>() {
      public User get() {
        return new User(investigation.getResponsibleUser(), fields.getNestedField("assignee"), beanContext);
      }
    });

    assignment = ValueWithDefault.decideDefault(fields.isIncluded("assignment", false), new ValueWithDefault.Value<Comment>() {
      public Comment get() {
        return new Comment(investigation.getReporterUser(), investigation.getTimestamp(), investigation.getComment(), fields.getNestedField("assignment", Fields.NONE, Fields.LONG),
                           beanContext);
      }
    });
    resolution = ValueWithDefault
      .decideDefault(fields.isIncluded("assignment", false), new Resolution(investigation.getRemoveMethod(), fields.getNestedField("resolution", Fields.NONE, Fields.LONG)));

    //support for pre-8.1
    if (TeamCityProperties.getBoolean(Investigation.REST_BEANS_INVESTIGATIONS_COMPATIBILITY)) {
      responsible = new User(investigation.getResponsibleUser(), Fields.SHORT, beanContext);
    }
  }

  @NotNull
  public List<InvestigationWrapper> getFromPostedAndApply(@NotNull final ServiceLocator serviceLocator, final boolean allowMultipleResult) {
    checkIsValid();

    ResponsibilityEntry entry = new ResponsibilityEntryEx(TypedFinderBuilder.getEnumValue(state, ResponsibilityEntry.State.class),
                                                            assignee.getFromPosted(serviceLocator.getSingletonService(UserFinder.class)),
                                                            serviceLocator.getSingletonService(UserFinder.class).getCurrentUser(),
                                                            new Date(),
                                                            assignment == null || assignment.getTextFromPosted() == null ? "" : assignment.getTextFromPosted(),
                                                            resolution.getFromPosted());

    ResponsibilityFacadeEx responsibilityFacade = serviceLocator.getSingletonService(ResponsibilityFacadeEx.class);

    InvestigationFinder investigationFinder = serviceLocator.findSingletonService(InvestigationFinder.class);
    assert investigationFinder != null;
    List<InvestigationWrapper> resultEntries = new ArrayList<>(1);

    if (target.anyProblem != null && target.anyProblem) {
      List<BuildType> buildTypesFromPosted = scope.getBuildTypesFromPosted(serviceLocator);
      if (!allowMultipleResult && buildTypesFromPosted.size() > 1) {
        throw new OnlySingleEntitySupportedException("Invalid 'scope' entity: for this request only single buildType is supported within 'buildTypes' entity");
      }
      for (BuildType buildType : buildTypesFromPosted) {
        responsibilityFacade.setBuildTypeResponsibility(buildType, entry);
        resultEntries.add(investigationFinder.getItem(InvestigationFinder.getLocator((SBuildType)buildType)));
      }
    } else {
      if (scope.buildTypes != null) {
        throw new BadRequestException("Invalid 'investigation' entity: Invalid 'scope' entity: 'buildTypes' should not be specified for not buildType-level investigation");
      }

      SProject project = scope.getProjectFromPosted(serviceLocator);

      List<STest> tests = target.getTestsFromPosted(serviceLocator);
      if (!tests.isEmpty()) {
        if (!allowMultipleResult && tests.size() > 1) {
          throw new OnlySingleEntitySupportedException("Invalid 'target' entity: for this request only single test is supported within 'tests' entity");
        }
        responsibilityFacade.setTestNameResponsibility(
          tests.stream().map(sTest -> sTest.getName()).distinct().collect(Collectors.toList()),
          project.getProjectId(),
          entry);
        tests.stream().map(test -> investigationFinder //only one item should be found in the project
          .getItem(InvestigationFinder.getLocatorForTest(test.getTestNameId(), project))).distinct().forEachOrdered(resultEntries::add);
      }

      List<Long> problems = target.getProblemsFromPosted(serviceLocator);
      if (!problems.isEmpty()) {
        if (!allowMultipleResult && problems.size() > 1) {
          throw new OnlySingleEntitySupportedException("Invalid 'target' entity: for this request only single problem is supported within 'problems' entity");
        }
        responsibilityFacade.setBuildProblemResponsibility(
          problems.stream().distinct().map(problemId -> ProblemWrapper.getBuildProblemInfo(problemId.intValue(), project.getProjectId())).collect(Collectors.toList()), //seems like only id is used inside
          project.getProjectId(),
          entry);
        problems.stream().distinct().map(problemId -> investigationFinder //only one item should be found in the project
          .getItem(InvestigationFinder.getLocatorForProblem(problemId.intValue(), project))).forEachOrdered(resultEntries::add);
      }
    }

    if (!allowMultipleResult && resultEntries.size() != 1) {
      throw new BadRequestException("Invalid 'investigation' entity: Invalid 'target' entity: found " + resultEntries.size() + " result entities, while exactly one is required");
    }

    return resultEntries;
  }

  private void checkIsValid() {
    if (resolution == null) {
      throw new BadRequestException("Invalid 'investigation' entity: 'resolution' should be specified");
    }
    if (scope == null) {
      throw new BadRequestException("Invalid 'investigation' entity: 'scope' should be specified");
    }
    if (target == null) {
      throw new BadRequestException("Invalid 'investigation' entity: 'target' should be specified");
    }
    if (assignee == null) {
      throw new BadRequestException("Invalid 'investigation' entity: 'assignee' should be specified");
    }
    if (state == null) {
      throw new BadRequestException("Invalid 'investigation' entity: 'state' should be specified");
    }
    try {
      target.checkIsValid();
    } catch (BadRequestException e) {
      throw new BadRequestException("Invalid 'investigation' entity: " + e.getMessage());
    }
  }

  public static class OnlySingleEntitySupportedException extends BadRequestException {
    public OnlySingleEntitySupportedException(final String message) {
      super(message);
    }
  }
}
