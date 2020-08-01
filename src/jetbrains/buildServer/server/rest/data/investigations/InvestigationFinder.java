/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.investigations;

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.BuildType;
import jetbrains.buildServer.responsibility.*;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.problem.ProblemFinder;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.data.problem.TestFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.buildType.ProblemTarget;
import jetbrains.buildServer.server.rest.model.problem.Resolution;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
@LocatorResource(value = LocatorName.INVESTIGATION, extraDimensions = {AbstractFinder.DIMENSION_LOOKUP_LIMIT, PagerData.START, PagerData.COUNT})
public class InvestigationFinder extends AbstractFinder<InvestigationWrapper> {
  @LocatorDimension("problem") private static final String PROBLEM_DIMENSION = "problem";
  @LocatorDimension("test") private static final String TEST_DIMENSION = "test";
  @LocatorDimension("assignmentProject") private static final String ASSIGNMENT_PROJECT = "assignmentProject";
  @LocatorDimension("affectedProject") private static final String AFFECTED_PROJECT = "affectedProject";
  @LocatorDimension("assignee") private static final String ASSIGNEE = "assignee";
  @LocatorDimension("sinceDate") private static final String SINCE_DATE = "sinceDate";
  @LocatorDimension("state") private static final String STATE = "state";
  @LocatorDimension("resolution") private static final String RESOLUTION = "resolution";
  @LocatorDimension("type") private static final String TYPE = "type";
  @LocatorDimension("reporter") private static final String REPORTER = "reporter";
  @LocatorDimension("buildType") private static final String BUILD_TYPE = "buildType";
  //todo: add removeMethod

  private final ProjectFinder myProjectFinder;
  private final BuildTypeFinder myBuildTypeFinder;
  private final ProblemFinder myProblemFinder;
  private final TestFinder myTestFinder;
  private final UserFinder myUserFinder;

  private final BuildTypeResponsibilityFacade myBuildTypeResponsibilityFacade;
  private final TestNameResponsibilityFacade myTestNameResponsibilityFacade;
  private final BuildProblemResponsibilityFacade myBuildProblemResponsibilityFacade;

  public InvestigationFinder(final ProjectFinder projectFinder,
                             final BuildTypeFinder buildTypeFinder,
                             final ProblemFinder problemFinder,
                             final TestFinder testFinder,
                             final UserFinder userFinder,
                             final BuildTypeResponsibilityFacade buildTypeResponsibilityFacade,
                             final TestNameResponsibilityFacade testNameResponsibilityFacade,
                             final BuildProblemResponsibilityFacade buildProblemResponsibilityFacade) {
    super(ASSIGNEE, REPORTER, TYPE, STATE, RESOLUTION, SINCE_DATE, ASSIGNMENT_PROJECT, AFFECTED_PROJECT, BUILD_TYPE, TEST_DIMENSION, PROBLEM_DIMENSION);
    myProjectFinder = projectFinder;
    myBuildTypeFinder = buildTypeFinder;
    myProblemFinder = problemFinder;
    myTestFinder = testFinder;
    myUserFinder = userFinder;
    myBuildTypeResponsibilityFacade = buildTypeResponsibilityFacade;
    myTestNameResponsibilityFacade = testNameResponsibilityFacade;
    myBuildProblemResponsibilityFacade = buildProblemResponsibilityFacade;
  }

  @NotNull
  public static String getLocator(@NotNull final SBuildType buildType) {
    return Locator.createEmptyLocator().setDimension(BUILD_TYPE, BuildTypeFinder.getLocator(buildType)).getStringRepresentation();
  }

  @NotNull
  public static String getLocator(@NotNull final ProblemWrapper problem) {
    return patchLocatorWithProblem(Locator.createEmptyLocator(), problem.getId().intValue()).getStringRepresentation();
  }

  @NotNull
  public static String getLocatorForProblem(final int problemId, @NotNull BuildProject project) {
    return patchLocator(patchLocatorWithProblem(Locator.createEmptyLocator(), problemId), project).getStringRepresentation();
  }

  @NotNull
  public static String getLocator(@NotNull final STest test) {
    return patchLocatorWithTest(Locator.createEmptyLocator(), test.getTestNameId()).getStringRepresentation();
  }

  @NotNull
  public static String getLocatorForTest(final long testNameId, @NotNull BuildProject project) {
    return patchLocator(patchLocatorWithTest(Locator.createEmptyLocator(), testNameId), project).getStringRepresentation();
  }


  @NotNull
  public static Locator patchLocatorWithProblem(@NotNull final Locator locator, final int problemId) {
    return locator.setDimension(PROBLEM_DIMENSION, ProblemFinder.getLocator(problemId));
  }

  @NotNull
  private static Locator patchLocatorWithTest(@NotNull final Locator locator, final long testNameId) {
    return locator.setDimension(TEST_DIMENSION, TestFinder.getTestLocator(testNameId));
  }

  @NotNull
  private static Locator patchLocator(@NotNull final Locator locator, @NotNull final BuildProject project) {
    return locator.setDimension(ASSIGNMENT_PROJECT, ProjectFinder.getLocator(project));
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final InvestigationWrapper investigationWrapper) {
    return InvestigationFinder.getLocator(investigationWrapper);
  }

  @NotNull
  public static String getLocator(final InvestigationWrapper investigation) {
    //.setDimension(TYPE, investigation.getType());
    if (investigation.isBuildType()) {
      //noinspection ConstantConditions
      return getLocator((SBuildType)investigation.getBuildTypeRE().getBuildType()); //TeamCity API issue: cast
    } else if (investigation.isProblem()) {
      //noinspection ConstantConditions
      return getLocatorForProblem(investigation.getProblemRE().getBuildProblemInfo().getId(), investigation.getProblemRE().getProject());
    } else if (investigation.isTest()) {
      //noinspection ConstantConditions
      return getLocatorForTest(investigation.getTestRE().getTestNameId(), investigation.getTestRE().getProject());
    } else {
      throw new OperationException("Unknown investigation type");
    }
  }

  @Override
  public InvestigationWrapper findSingleItem(@NotNull final Locator locator) {
    return null;
  }

  @NotNull
  @Override
  public ItemFilter<InvestigationWrapper> getFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<InvestigationWrapper> result = new MultiCheckerFilter<InvestigationWrapper>();

    final String investigatorDimension = locator.getSingleDimensionValue(ASSIGNEE);
    if (investigatorDimension != null) {
      @NotNull final User user = myUserFinder.getItem(investigatorDimension);
      result.add(new FilterConditionChecker<InvestigationWrapper>() {
        public boolean isIncluded(@NotNull final InvestigationWrapper item) {
          return user.equals(item.getResponsibleUser());
        }
      });
    }

    final String reporterDimension = locator.getSingleDimensionValue(REPORTER);
    if (reporterDimension != null) {
      @NotNull final User user = myUserFinder.getItem(reporterDimension);
      result.add(new FilterConditionChecker<InvestigationWrapper>() {
        public boolean isIncluded(@NotNull final InvestigationWrapper item) {
          return user.equals(item.getReporterUser());
        }
      });
    }

    final String typeDimension = locator.getSingleDimensionValue(TYPE);
    if (typeDimension != null) {
      if (ProblemTarget.getKnownTypesForInvestigation().stream().noneMatch(s -> typeDimension.equalsIgnoreCase(s))) {
        throw new BadRequestException("Error in dimension '" + TYPE + "': unknown value '" + typeDimension + "'. Known values: " +
                                      StringUtil.join(ProblemTarget.getKnownTypesForInvestigation(), ", "));
      }
      result.add(new FilterConditionChecker<InvestigationWrapper>() {
        public boolean isIncluded(@NotNull final InvestigationWrapper item) {
          return typeDimension.equalsIgnoreCase(ProblemTarget.getType(item));
        }
      });
    }

    final String stateDimension = locator.getSingleDimensionValue(STATE);
    if (stateDimension != null) {
      if (!InvestigationWrapper.getKnownStates().contains(stateDimension.toLowerCase())) {
        throw new BadRequestException("Error in dimension '" + STATE + "': unknown value '" + stateDimension + "'. Known values: " +
                                      StringUtil.join(InvestigationWrapper.getKnownStates(), ", "));
      }
      result.add(new FilterConditionChecker<InvestigationWrapper>() {
        public boolean isIncluded(@NotNull final InvestigationWrapper item) {
          return stateDimension.equalsIgnoreCase(item.getState().name());
        }
      });
    }

    final String resolutionDimension = locator.getSingleDimensionValue(RESOLUTION);
    if (resolutionDimension != null) {
      ResponsibilityEntry.RemoveMethod removeMethod = Resolution.getRemoveMethodForInvestigation(resolutionDimension);
      result.add(item -> removeMethod.equals(item.getRemoveMethod()));
    }

    final String assignmentProjectDimension = locator.getSingleDimensionValue(ASSIGNMENT_PROJECT);
    if (assignmentProjectDimension != null){
      @NotNull final SProject project = myProjectFinder.getItem(assignmentProjectDimension);
      result.add(new FilterConditionChecker<InvestigationWrapper>() {
        public boolean isIncluded(@NotNull final InvestigationWrapper item) {
          final BuildProject assignmentProject = item.getAssignmentProject();
          return assignmentProject != null && project.getProjectId().equals(assignmentProject.getProjectId());
        }
      });
    }

    final String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null){
      @NotNull final SProject project = myProjectFinder.getItem(affectedProjectDimension);
      result.add(new FilterConditionChecker<InvestigationWrapper>() {
        public boolean isIncluded(@NotNull final InvestigationWrapper item) {
          final BuildProject assignmentProject = item.getAssignmentProject();
          final BuildType assignmentBuildType = item.getAssignmentBuildType();
          final BuildProject buildTypeProject = assignmentBuildType != null ? myProjectFinder.findProjectByInternalId(assignmentBuildType.getProjectId()) : null;
          return (assignmentProject != null && ProjectFinder.isSameOrParent(project, assignmentProject)) ||
                 (buildTypeProject != null && ProjectFinder.isSameOrParent(project, buildTypeProject));
        }
      });
    }

    final String sinceDateDimension = locator.getSingleDimensionValue(SINCE_DATE);
    if (sinceDateDimension != null) {
      final Date date = DataProvider.getDate(sinceDateDimension);
      result.add(new FilterConditionChecker<InvestigationWrapper>() {
        public boolean isIncluded(@NotNull final InvestigationWrapper item) {
          return date.before(item.getTimestamp());
        }
      });
    }

//todo: add assignmentBuildType
    return result;
  }

  @NotNull
  @Override
  public ItemHolder<InvestigationWrapper> getPrefilteredItems(@NotNull final Locator locator) {
    final String problemDimension = locator.getSingleDimensionValue(PROBLEM_DIMENSION);
    if (problemDimension != null){
      final ProblemWrapper problem = myProblemFinder.getItem(problemDimension);
      return getItemHolder(problem.getInvestigations());
    }

    final String testDimension = locator.getSingleDimensionValue(TEST_DIMENSION);
    if (testDimension != null){
      final STest test = myTestFinder.getItem(testDimension);
      return getItemHolder(getInvestigationWrappers(test));
    }

    final String buildTypeDimension = locator.getSingleDimensionValue(BUILD_TYPE);
    if (buildTypeDimension != null){
      final SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeDimension, false);
      return getItemHolder(getInvestigationWrappersForBuildType(buildType));
    }

    @Nullable User user = null;
    final String investigatorDimension = locator.getSingleDimensionValue(ASSIGNEE);
    if (investigatorDimension != null) {
      user = myUserFinder.getItem(investigatorDimension);
    }

    final String assignmentProjectDimension = locator.getSingleDimensionValue(ASSIGNMENT_PROJECT);
    if (assignmentProjectDimension != null){
      @NotNull final SProject project = myProjectFinder.getItem(assignmentProjectDimension);
      return getItemHolder(getInvestigationWrappersForProject(project, user));
    }

    final String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null){
      @NotNull final SProject project = myProjectFinder.getItem(affectedProjectDimension);
      return getItemHolder(getInvestigationWrappersForProjectWithSubprojects(project, user));
    }

    if (user != null){
      return getItemHolder(getInvestigationWrappersForProjectWithSubprojects(myProjectFinder.getRootProject(), user));
    }
    locator.markUnused(ASSIGNEE);
    return getItemHolder(getInvestigationWrappersForProjectWithSubprojects(myProjectFinder.getRootProject(), null));
  }

  public List<InvestigationWrapper> getInvestigationWrappersForBuildType(final SBuildType buildType) {
    final ResponsibilityEntry responsibilityInfo = buildType.getResponsibilityInfo();
    final ResponsibilityEntry.State state = responsibilityInfo.getState();
    if (state.equals(ResponsibilityEntry.State.NONE)) {
      return Collections.<InvestigationWrapper>emptyList();
    } else {
      return Collections.singletonList(new InvestigationWrapper(getBuildTypeRE(buildType)));
    }
  }

  @NotNull
  private List<InvestigationWrapper> getInvestigationWrappersForProjectWithSubprojects(@NotNull final SProject project, @Nullable User user) {
    if (myProjectFinder.getRootProject().getExternalId().equals(project.getExternalId())){
      // this is a root project, use single call
      return getInvestigationWrappersInternal(null, user);
    }

    final ArrayList<InvestigationWrapper> result = new ArrayList<InvestigationWrapper>();
    result.addAll(getInvestigationWrappersForProject(project, user));

    //todo: TeamCity API: is there a dedicated wahy to do this?
    final List<SProject> subProjects = project.getProjects();
    for (SProject subProject : subProjects) {
      result.addAll(getInvestigationWrappersForProject(subProject, user));
    }
    return result;
  }

  private List<InvestigationWrapper> getInvestigationWrappersForProject(@NotNull final SProject project, @Nullable User user) {
    return getInvestigationWrappersInternal(project, user);
  }

  /**
   *
   * @param project if null, all projects are processed, if not - only the project passed
   */
  private List<InvestigationWrapper> getInvestigationWrappersInternal(@Nullable final SProject project, @Nullable User user) {
    final ArrayList<InvestigationWrapper> result = new ArrayList<InvestigationWrapper>();

    final String projectId = project == null ? null : project.getProjectId();

    final List<BuildTypeResponsibilityEntry> buildTypeResponsibilities = myBuildTypeResponsibilityFacade.getUserBuildTypeResponsibilities(user, projectId);
    result.addAll(CollectionsUtil.convertCollection(buildTypeResponsibilities, new Converter<InvestigationWrapper, BuildTypeResponsibilityEntry>() {
      public InvestigationWrapper createFrom(@NotNull final BuildTypeResponsibilityEntry source) {
        return new InvestigationWrapper(source);
      }
    }));

    final List<TestNameResponsibilityEntry> testResponsibilities = myTestNameResponsibilityFacade.getUserTestNameResponsibilities(user, projectId);
    result.addAll(CollectionsUtil.convertCollection(testResponsibilities, new Converter<InvestigationWrapper, TestNameResponsibilityEntry>() {
      public InvestigationWrapper createFrom(@NotNull final TestNameResponsibilityEntry source) {
        return new InvestigationWrapper(source);
      }
    }));

    final List<BuildProblemResponsibilityEntry> problemResponsibilities = myBuildProblemResponsibilityFacade.getUserBuildProblemResponsibilities(user, projectId);
    result.addAll(CollectionsUtil.convertCollection(problemResponsibilities, new Converter<InvestigationWrapper, BuildProblemResponsibilityEntry>() {
      public InvestigationWrapper createFrom(@NotNull final BuildProblemResponsibilityEntry source) {
        return new InvestigationWrapper(source);
      }
    }));

    //todo: sort!
    return result;
  }

  @NotNull
  public List<InvestigationWrapper> getInvestigationWrappers(@NotNull final STest item) {
    final List<TestNameResponsibilityEntry> responsibilities = item.getAllResponsibilities();
    final ArrayList<InvestigationWrapper> result = new ArrayList<InvestigationWrapper>(responsibilities.size());
    for (TestNameResponsibilityEntry responsibility : responsibilities) {
      result.add(new InvestigationWrapper(responsibility));
    }
    return result;
  }


  private boolean isInvestigationRelatedToProblem(@NotNull final InvestigationWrapper item, @NotNull final ProblemWrapper problem) {
    if (!item.isProblem()){
      return false;
    }
    @SuppressWarnings("ConstantConditions") @NotNull final BuildProblemResponsibilityEntry problemRE = item.getProblemRE();
    return problemRE.getBuildProblemInfo().getId() == problem.getId();
  }

  public BuildTypeResponsibilityEntry getBuildTypeRE(@NotNull final SBuildType buildType) {
    //todo: TeamCity API (MP): would be good to use buildType.getResponsibilityInfo() here
    final List<BuildTypeResponsibilityEntry> userBuildTypeResponsibilities = myBuildTypeResponsibilityFacade.getUserBuildTypeResponsibilities(null, null);
    for (BuildTypeResponsibilityEntry responsibility : userBuildTypeResponsibilities) {
      if (responsibility.getBuildType().equals(buildType)){
        return responsibility;
      }
    }
    throw new NotFoundException("Build type with id '" + buildType.getExternalId() + "' does not have associated investigation.");
  }
}
