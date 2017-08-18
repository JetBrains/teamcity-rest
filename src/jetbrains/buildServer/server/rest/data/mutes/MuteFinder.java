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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.buildType.ProblemTarget;
import jetbrains.buildServer.server.rest.model.problem.Resolution;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.mute.*;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.server.rest.data.TypedFinderBuilder.Dimension;

/**
 * @author Yegor.Yarko
 *         Date: 09.08.17
 */
public class MuteFinder extends DelegatingFinder<MuteInfo> {
  private static final Dimension<Long> ID = new Dimension<>("id");
  private static final Dimension<List<SProject>> AFFECTED_PROJECT = new Dimension<>("affectedProject");
  private static final Dimension<List<SProject>> PROJECT = new Dimension<>("project"); //differs from investigation: assignmentProject
  private static final Dimension<TimeCondition.ParsedTimeCondition> CREATION_DATE = new Dimension<>("creationDate");  //differs from investigation: sinceDate
  private static final Dimension<TimeCondition.ParsedTimeCondition> UNMUTE_DATE = new Dimension<>("unmuteDate");  //differs from investigation: sinceDate
  private static final Dimension<List<SUser>> REPORTER = new Dimension<>("reporter"); //todo: review naming?
  private static final Dimension<String> TYPE = new Dimension<>("type"); // target
  private static final Dimension<String> RESOLUTION = new Dimension<>("resolution");

  //private static final String PROBLEM_DIMENSION = "problem";
  //private static final String TEST_DIMENSION = "test";

  //private static final String BUILD_TYPE = "buildType"; //todo: add assignmentBuildType

  private final ProjectFinder myProjectFinder;

  @NotNull private final TimeCondition myTimeCondition;
  @NotNull private final PermissionChecker myPermissionChecker;
  private final ProblemMutingService myProblemMutingService;
  private final LowLevelProblemMutingServiceImpl myLowLevelMutingService;
  private final ServiceLocator myServiceLocator;


  public MuteFinder(@NotNull final ProjectFinder projectFinder,
                    @NotNull final TimeCondition timeCondition,
                    @NotNull final PermissionChecker permissionChecker,
                    @NotNull final ProblemMutingService problemMutingService,
                    @NotNull final LowLevelProblemMutingServiceImpl levelMutingService,
                    @NotNull final ServiceLocator serviceLocator) {
    myProjectFinder = projectFinder;
    myTimeCondition = timeCondition;
    myPermissionChecker = permissionChecker;
    myProblemMutingService = problemMutingService;
    myLowLevelMutingService = levelMutingService;
    myServiceLocator = serviceLocator;
    setDelegate(new MuteFinderBuilder().build());
  }

  private class MuteFinderBuilder extends TypedFinderBuilder<MuteInfo> {

    MuteFinderBuilder() {
      singleDimension(dimension -> {
        // no dimensions found, assume it's id
        return Collections.singletonList(findMuteById(getLong(dimension).intValue()));
      });

      dimensionLong(ID).description("internal mute id")
                       .filter((value, item) -> value.equals(item.getId().longValue()))
                       .toItems(dimension -> Collections.singletonList(findMuteById(dimension.intValue())));
      dimensionProjects(AFFECTED_PROJECT, myServiceLocator).description("project affected by the mutes")
                                                           .filter((projects, item) -> {
                                                             final SProject assignmentProject = item.getProject();
                                                             return (assignmentProject != null && ProjectFinder.isSameOrParent(projects, assignmentProject));
                                                           }).toItems(dimension -> dimension.stream().flatMap(p -> getMuteInfosForProject(p)).collect(Collectors.toList()));

      dimensionProjects(PROJECT, myServiceLocator).description("project in which mute is assigned").valueForDefaultFilter(muteInfo -> Collections.singleton(muteInfo.getProject())); //todo: add toItems?

      dimensionTimeCondition(CREATION_DATE, myTimeCondition).description("mute creation time")
                                                   .filter((timeCondition, item) -> timeCondition.matches(item.getMutingTime()));
      dimensionTimeCondition(UNMUTE_DATE, myTimeCondition).description("automatic unmute time")
                                                   .filter((timeCondition, item) -> {
                                                     Date unmuteTime = item.getAutoUnmuteOptions().getUnmuteByTime();
                                                     return unmuteTime != null && timeCondition.matches(unmuteTime);
                                                   });
      dimensionUsers(REPORTER, myServiceLocator).description("muting user")
                                                .filter((users, item) -> users.stream().map(u -> u.getId()).collect(Collectors.toSet()).contains(item.getMutingUserId()));
      dimensionFixedText(TYPE, ProblemTarget.getKnownTypesForMute()).description("what is muted").valueForDefaultFilter(muteInfo -> ProblemTarget.getType(muteInfo))
                                                                    .toItems(dimension -> {
                                                                      switch (dimension) {
                                                                        case ProblemTarget.TEST_TYPE:
                                                                          return getTestsMutes(myProjectFinder.getRootProject()).collect(Collectors.toList()); //todo: add project
                                                                        case ProblemTarget.PROBLEM_TYPE:
                                                                          return getProblemsMutes(myProjectFinder.getRootProject()).collect(Collectors.toList());
                                                                      }
                                                                      throw new OperationException("Unexpected mute type '" + dimension + "'");
                                                                    });
      dimensionFixedText(RESOLUTION, Resolution.getKnownTypesForMute()).description("unmute condition").
        valueForDefaultFilter(muteInfo -> Resolution.getType(muteInfo.getAutoUnmuteOptions()));

      multipleConvertToItemHolder(DimensionCondition.ALWAYS, dimensions -> FinderDataBinding.getItemHolder(getMuteInfosForProject(myProjectFinder.getRootProject())));

      filter(DimensionCondition.ALWAYS, dimensions -> new ItemFilter<MuteInfo>() {
        @Override
        public boolean shouldStop(@NotNull final MuteInfo item) {
          return false;
        }

        @Override
        public boolean isIncluded(@NotNull final MuteInfo item) {
          return canView(item);
        }
      });

      defaults(DimensionCondition.ALWAYS, new NameValuePairs().add(PagerData.COUNT, String.valueOf(Constants.getDefaultPageItemsCount())));

      locatorProvider(muteInfo -> getLocator(muteInfo));
//      containerSetProvider(() -> new HashSet<SUser>()); //todo: sorting here!
    }
  }

  private boolean canView(@NotNull final MuteInfo item) {
    try {
      MuteScope scope = item.getScope();
      String projectId = scope.getProjectId();
      if (projectId != null) {
        myPermissionChecker.checkProjectPermission(Permission.VIEW_PROJECT, scope.getProjectId());
      }

      Collection<String> buildTypeIds = scope.getBuildTypeIds();
      if (buildTypeIds != null) {
        ProjectManager projectManager = myServiceLocator.getSingletonService(ProjectManager.class);
        buildTypeIds.stream().map(id -> projectManager.findBuildTypeById(id)).filter(Objects::nonNull).map(buildType -> buildType.getProjectId()).collect(Collectors.toSet()).forEach(pId -> myPermissionChecker.checkProjectPermission(Permission.VIEW_PROJECT, pId)); //todo: should actually filter out data on MuteInfo
      }
      Long buildId = scope.getBuildId();
      if (buildId != null) {
        //actually, this should never happen todo: check this
        BuildPromotion buildPromotion = findPromotionByBuildId(buildId);
        if (buildPromotion != null) {
          myPermissionChecker.checkPermission(Permission.VIEW_PROJECT, buildPromotion);
        }
      }
    } catch (AuthorizationFailedException e) {
      return false;
    }
    return true;
  }

  @Nullable
  private BuildPromotion findPromotionByBuildId(@NotNull final Long buildId) {
    SBuild buildInstanceById = myServiceLocator.getSingletonService(BuildsManager.class).findBuildInstanceById(buildId);
    return buildInstanceById  == null ? null : buildInstanceById.getBuildPromotion();
  }

  @NotNull
  private MuteInfo findMuteById(@NotNull final Integer id) {
    String projectId = myProjectFinder.getRootProject().getProjectId();

    Collection<Long> mutedTestNameIds = myLowLevelMutingService.retrieveMuteTests(id);
    Optional<MuteInfo> result = mutedTestNameIds.stream().flatMap(testNameId -> getMutes(myProblemMutingService.getTestCurrentMuteInfo(projectId, testNameId)))
                                             .filter(muteInfo -> id.equals(muteInfo.getId())).findAny();
    if (result.isPresent()) return result.get();

    Collection<Integer> mutedProblemIds = myLowLevelMutingService.retrieveMuteProblems(id);
    result = mutedProblemIds.stream().flatMap(problemId -> getMutes(myProblemMutingService.getBuildProblemCurrentMuteInfo(projectId, problemId)))
                                            .filter(muteInfo -> id.equals(muteInfo.getId())).findAny();
    if (result.isPresent()) return result.get();

    throw new NotFoundException("No mute with id '" + id + "' found");
  }

  /*
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
  */


  @NotNull
  public static String getLocator(final MuteInfo item) {
    return Locator.getStringLocator(ID.name, String.valueOf(item.getId()));
  }


  @NotNull
  private Stream<MuteInfo> getMuteInfosForProject(@NotNull final SProject project) {
    return Stream.concat(getProblemsMutes(project), getTestsMutes(project));
  }

  @NotNull
  private Stream<MuteInfo> getTestsMutes(final @NotNull SProject project) {
    return myProblemMutingService.getTestsCurrentMuteInfo(project).values().stream().flatMap(currentMute -> getMutes(currentMute));
  }

  @NotNull
  private Stream<MuteInfo> getProblemsMutes(final @NotNull SProject project) {
    return myProblemMutingService.getBuildProblemsCurrentMuteInfo(project).values().stream().flatMap(currentMute -> getMutes(currentMute));
  }

  @NotNull
  private Stream<MuteInfo> getMutes(@Nullable final CurrentMuteInfo currentMuteInfo) {
    if (currentMuteInfo == null) return Stream.empty();
    return Stream.concat(currentMuteInfo.getProjectsMuteInfo().values().stream(),
                         currentMuteInfo.getMuteInfoGroups().keySet().stream());
  }
}
