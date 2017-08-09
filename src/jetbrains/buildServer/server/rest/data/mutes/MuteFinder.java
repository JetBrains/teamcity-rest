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

package jetbrains.buildServer.server.rest.data.mutes;

import com.intellij.openapi.util.text.StringUtil;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationFinder;
import jetbrains.buildServer.server.rest.data.problem.ProblemFinder;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.data.problem.TestFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.buildType.ProblemTarget;
import jetbrains.buildServer.server.rest.model.problem.Resolution;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.mute.CurrentMuteInfo;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import jetbrains.buildServer.serverSide.mute.ProblemMutingService;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 09.08.17
 */
public class MuteFinder extends AbstractFinder<MuteInfo> {
  private static final String TYPE = "type"; // target
  private static final String PROBLEM_DIMENSION = "problem";
  private static final String TEST_DIMENSION = "test";

  private static final String ASSIGNMENT_PROJECT = "assignmentProject";  //todo: review naming?
  private static final String BUILD_TYPE = "buildType";
  private static final String AFFECTED_PROJECT = "affectedProject";

  private static final String SINCE_DATE = "sinceDate";
  private static final String REPORTER = "reporter";  //todo: review naming?
  private static final String RESOLUTION = "resolution";

  private final ProjectFinder myProjectFinder;
  private final BuildTypeFinder myBuildTypeFinder;
  private final ProblemFinder myProblemFinder;
  private final TestFinder myTestFinder;
  private final UserFinder myUserFinder;

  private final ProblemMutingService myProblemMutingService;


  public MuteFinder(final ProjectFinder projectFinder,
                    final BuildTypeFinder buildTypeFinder,
                    final ProblemFinder problemFinder,
                    final TestFinder testFinder,
                    final UserFinder userFinder,
                    final ProblemMutingService problemMutingService) {
    super(DIMENSION_ID, REPORTER, TYPE, RESOLUTION, SINCE_DATE, ASSIGNMENT_PROJECT, AFFECTED_PROJECT, BUILD_TYPE, TEST_DIMENSION, PROBLEM_DIMENSION);
    myProjectFinder = projectFinder;
    myBuildTypeFinder = buildTypeFinder;
    myProblemFinder = problemFinder;
    myTestFinder = testFinder;
    myUserFinder = userFinder;
    myProblemMutingService = problemMutingService;
  }

  @NotNull
  public static String getLocator(@NotNull final SBuildType buildType) {
    return Locator.createEmptyLocator().setDimension(BUILD_TYPE, BuildTypeFinder.getLocator(buildType)).getStringRepresentation();
  }

  @NotNull
  public static String getLocator(@NotNull final ProblemWrapper problem) {
    return InvestigationFinder.getLocator(problem);
  }

  @NotNull
  public static String getLocatorForProblem(final int problemId, @NotNull BuildProject project) {
    return InvestigationFinder.getLocatorForProblem(problemId, project);
  }

  @NotNull
  public static String getLocator(@NotNull final STest test) {
    return InvestigationFinder.getLocator(test);
  }

  @NotNull
  public static String getLocatorForTest(final long testNameId, @NotNull BuildProject project) {
    return InvestigationFinder.getLocatorForTest(testNameId, project);
  }


  @NotNull
  @Override
  public String getItemLocator(@NotNull final MuteInfo investigationWrapper) {
    return MuteFinder.getLocator(investigationWrapper);
  }

  @NotNull
  public static String getLocator(final MuteInfo item) {
    return Locator.getStringLocator(DIMENSION_ID, String.valueOf(item.getId()));
  }

  @Override
  public MuteInfo findSingleItem(@NotNull final Locator locator) {
    return null;
  }

  @NotNull
  @Override
  public ItemFilter<MuteInfo> getFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<MuteInfo> result = new MultiCheckerFilter<MuteInfo>();

    final Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);  //todo: ineffective!!!  implement findSingleItem instead
    if (id != null) {
      Integer intId = id.intValue();
      result.add(new FilterConditionChecker<MuteInfo>() {
        public boolean isIncluded(@NotNull final MuteInfo item) {
          return intId.equals(item.getId());
        }
      });
    }

    final String reporterDimension = locator.getSingleDimensionValue(REPORTER);
    if (reporterDimension != null) {
      @NotNull final User user = myUserFinder.getItem(reporterDimension);
      result.add(new FilterConditionChecker<MuteInfo>() {
        public boolean isIncluded(@NotNull final MuteInfo item) {
          return user.equals(item.getMutingUser());
        }
      });
    }

    final String typeDimension = locator.getSingleDimensionValue(TYPE);
    if (typeDimension != null) {
      if (!ProblemTarget.getKnownTypesForMute().contains(typeDimension.toLowerCase())) {
        throw new BadRequestException("Error in dimension '" + TYPE + "': unknown value '" + typeDimension + "'. Known values: " +
                                      StringUtil.join(ProblemTarget.getKnownTypesForMute(), ", "));
      }
      result.add(new FilterConditionChecker<MuteInfo>() {
        public boolean isIncluded(@NotNull final MuteInfo item) {
          return typeDimension.equalsIgnoreCase(ProblemTarget.getType(item));
        }
      });
    }

    final String stateDimension = locator.getSingleDimensionValue(RESOLUTION);
    if (stateDimension != null) {
      if (!Resolution.getKnownTypesForMute().contains(stateDimension)) {
        throw new BadRequestException("Error in dimension '" + RESOLUTION + "': unknown value '" + stateDimension + "'. Known values: " +
                                      StringUtil.join(Resolution.getKnownTypesForMute(), ", "));
      }
      result.add(new FilterConditionChecker<MuteInfo>() {
        public boolean isIncluded(@NotNull final MuteInfo item) {
          return stateDimension.equalsIgnoreCase(Resolution.getType(item.getAutoUnmuteOptions()));
        }
      });
    }

    final String assignmentProjectDimension = locator.getSingleDimensionValue(ASSIGNMENT_PROJECT);
    if (assignmentProjectDimension != null){
      @NotNull final SProject project = myProjectFinder.getItem(assignmentProjectDimension);
      result.add(new FilterConditionChecker<MuteInfo>() {
        public boolean isIncluded(@NotNull final MuteInfo item) {
          return project.getProjectId().equals(item.getProjectId());
        }
      });
    }

    final String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null){
      @NotNull final SProject project = myProjectFinder.getItem(affectedProjectDimension);
      result.add(new FilterConditionChecker<MuteInfo>() {
        public boolean isIncluded(@NotNull final MuteInfo item) {
          final BuildProject assignmentProject = item.getProject();
          return (assignmentProject != null && ProjectFinder.isSameOrParent(project, assignmentProject));
        }
      });
    }

    final String sinceDateDimension = locator.getSingleDimensionValue(SINCE_DATE);
    if (sinceDateDimension != null) {
      final Date date = DataProvider.getDate(sinceDateDimension);
      result.add(new FilterConditionChecker<MuteInfo>() {
        public boolean isIncluded(@NotNull final MuteInfo item) {
          return date.before(item.getMutingTime());
        }
      });
    }
//todo: add filtering by unmuteTime

//todo: add assignmentBuildType
    return result;
  }

  @NotNull
  @Override
  public ItemHolder<MuteInfo> getPrefilteredItems(@NotNull final Locator locator) {
    /*
    final String problemDimension = locator.getSingleDimensionValue(PROBLEM_DIMENSION);
    if (problemDimension != null){
      final ProblemWrapper problem = myProblemFinder.getItem(problemDimension);
      return getItemHolder(problem.getMutes());
    }

    final String testDimension = locator.getSingleDimensionValue(TEST_DIMENSION);
    if (testDimension != null){
      final STest test = myTestFinder.getItem(testDimension);
      return getItemHolder(getMuteInfos(test));
    }

    final String buildTypeDimension = locator.getSingleDimensionValue(BUILD_TYPE);
    if (buildTypeDimension != null){
      final SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeDimension, false);
      return getItemHolder(getMuteInfosForBuildType(buildType));
    }

    @Nullable User user = null;
    final String investigatorDimension = locator.getSingleDimensionValue(ASSIGNEE);
    if (investigatorDimension != null) {
      user = myUserFinder.getItem(investigatorDimension);
    }

    final String assignmentProjectDimension = locator.getSingleDimensionValue(ASSIGNMENT_PROJECT);
    if (assignmentProjectDimension != null){
      @NotNull final SProject project = myProjectFinder.getItem(assignmentProjectDimension);
      return getItemHolder(getMuteInfosForProject(project, user));  //todo: filter by this specific project
    }

    */

    SProject affectedProject;
    final String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null){
      affectedProject = myProjectFinder.getItem(affectedProjectDimension);
    } else {
      affectedProject = myProjectFinder.getRootProject();
    }

    return getItemHolder(getMuteInfosForProject(affectedProject));
  }

  private List<MuteInfo> getMuteInfosForProject(@NotNull final SProject project) {
    Stream<MuteInfo> result = Stream.empty();

    Map<Integer, CurrentMuteInfo> currentProblemMuteInfo = myProblemMutingService.getBuildProblemsCurrentMuteInfo(project);//todo: review scope, see javadoc
    result = Stream.concat(result, currentProblemMuteInfo.values().stream().flatMap(currentMute -> currentMute.getProjectsMuteInfo().values().stream()));
    result = Stream.concat(result, currentProblemMuteInfo.values().stream().flatMap(currentMute -> currentMute.getMuteInfoGroups().keySet().stream()));

    Map<Long, CurrentMuteInfo> currentTestMuteInfo = myProblemMutingService.getTestsCurrentMuteInfo(project);//todo: review scope, see javadoc
    result = Stream.concat(result, currentTestMuteInfo.values().stream().flatMap(currentMute -> currentMute.getProjectsMuteInfo().values().stream()));
    result = Stream.concat(result, currentTestMuteInfo.values().stream().flatMap(currentMute -> currentMute.getMuteInfoGroups().keySet().stream()));

    //todo: sort?
    return result.collect(Collectors.toList());
  }

  /*
  @NotNull
  public List<MuteInfo> getMuteInfos(@NotNull final STest item) {
    final CurrentMuteInfo responsibilities = item.getCurrentMuteInfo();
    final ArrayList<MuteInfo> result = new ArrayList<MuteInfo>(responsibilities.size());
    for (TestNameResponsibilityEntry responsibility : responsibilities) {
      result.add(new MuteInfo(responsibility));
    }
    return result;
  }


  private boolean isMuteRelatedToProblem(@NotNull final MuteInfo item, @NotNull final ProblemWrapper problem) {
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
    throw new NotFoundException("Build type with id '" + buildType.getExternalId() + "' does not have associated mute.");
  }
  */
}
