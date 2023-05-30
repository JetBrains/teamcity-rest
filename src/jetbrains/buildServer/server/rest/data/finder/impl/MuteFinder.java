/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.finder.impl;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.BuildTypeDescriptor;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.locator.Dimension;
import jetbrains.buildServer.server.rest.data.locator.StubDimension;
import jetbrains.buildServer.server.rest.data.util.ItemFilter;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.TimeCondition;
import jetbrains.buildServer.server.rest.data.finder.AbstractFinder;
import jetbrains.buildServer.server.rest.data.finder.DelegatingFinder;
import jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder;
import jetbrains.buildServer.server.rest.data.problem.ProblemFinder;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.data.problem.TestFinder;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.buildType.ProblemTarget;
import jetbrains.buildServer.server.rest.model.problem.Resolution;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorDimensionDataType;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.mute.*;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * @author Yegor.Yarko
 *         Date: 09.08.17
 */
@LocatorResource(value = LocatorName.MUTE,
    extraDimensions = AbstractFinder.DIMENSION_ITEM,
    baseEntity = "Mute",
    examples = {
        "`project:<projectLocator>` — find muted problem under project found by `projectLocator`.",
        "`type:test` — find last 100 muted tests."
    }
)
@JerseyContextSingleton
@Component("restMuteFinder")
public class MuteFinder extends DelegatingFinder<MuteInfo> {
  @LocatorDimension(value = "id", dataType = LocatorDimensionDataType.INTEGER) 
  private static final Dimension ID = new StubDimension("id");
  @LocatorDimension(value = "affectedProject", format = LocatorName.PROJECT, notes = "Project (direct or indirect parent) locator.")
  private static final Dimension AFFECTED_PROJECT = new StubDimension("affectedProject");
  @LocatorDimension(value = "project", format = LocatorName.PROJECT, notes = "Project (direct parent) locator.")
  private static final Dimension PROJECT = new StubDimension("project"); //differs from investigation: assignmentProject
  @LocatorDimension(value = "creationDate", dataType = LocatorDimensionDataType.TIMESTAMP, notes = "yyyyMMddTHHmmss+ZZZZ")
  private static final Dimension CREATION_DATE = new StubDimension("creationDate");  //differs from investigation: sinceDate
  @LocatorDimension(value = "unmuteDate", dataType = LocatorDimensionDataType.TIMESTAMP, notes = "yyyyMMddTHHmmss+ZZZZ")
  private static final Dimension UNMUTE_DATE = new StubDimension("unmuteDate");  //differs from investigation: sinceDate
  @LocatorDimension(value = "reporter", notes = "User who muted this test.")
  private static final Dimension REPORTER = new StubDimension("reporter"); //todo: review naming?
  @LocatorDimension(value = "type", allowableValues = "test,problem,anyProblem,unknown")
  private static final Dimension TYPE = new StubDimension("type"); // target
  @LocatorDimension(value = "resolution", allowableValues = "manually,whenFixed,atTime")
  private static final Dimension RESOLUTION = new StubDimension("resolution");
  @LocatorDimension(value = "test", format = LocatorName.TEST, notes = "Test locator.")
  private static final Dimension TEST = new StubDimension("test");
  @LocatorDimension(value = "problem", format = LocatorName.PROBLEM, notes = "Problem locator.")
  private static final Dimension PROBLEM = new StubDimension("problem");


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

  @NotNull
  public static String getLocator(@NotNull final STest test) {
    return Locator.getStringLocator(TEST, TestFinder.getTestLocator(test));
  }

  @NotNull
  public static String getLocator(@NotNull final ProblemWrapper problem) {
    return Locator.getStringLocator(PROBLEM, ProblemFinder.getLocator(problem));
  }

  @NotNull
  public static String getLocator(final MuteInfo item) {
    return Locator.getStringLocator(ID, String.valueOf(item.getId()));
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
        buildTypeIds.stream().map(projectManager::findBuildTypeById).filter(Objects::nonNull).map(BuildTypeDescriptor::getProjectId).collect(Collectors.toSet())
                    .forEach(pId -> myPermissionChecker.checkProjectPermission(Permission.VIEW_PROJECT, pId)); //todo: should actually filter out data on MuteInfo
      }
      Long buildId = scope.getBuildId();
      if (buildId != null) {
        //actually, this should never happen todo: check this
        BuildPromotion buildPromotion = findPromotionByBuildId(buildId);
        if (buildPromotion != null) {
          myPermissionChecker.checkPermission(Permission.VIEW_PROJECT, buildPromotion);
        }
      }
    } catch (AuthorizationFailedException | AccessDeniedException e) {
      return false;
    }
    return true;
  }

  /*
  @NotNull
  public static String getLocator(@NotNull final SBuildType buildType) {
    return Locator.createEmptyLocator().setDimension(BUILD_TYPE, BuildTypeFinder.getLocator(buildType)).getStringRepresentation();
  }

  @NotNull
  public static String getLocatorForProblem(final int problemId, @NotNull BuildProject project) {
    return InvestigationFinder.getLocatorForProblem(problemId, project);
  }

  @NotNull
  public static String getLocatorForTest(final long testNameId, @NotNull BuildProject project) {
    return InvestigationFinder.getLocatorForTest(testNameId, project);
  }
  */

  @NotNull
  private Stream<MuteInfo> getMuteInfosForProject(@NotNull final SProject project) {
    return Stream.concat(getProblemsMutes(project), getTestsMutes(project)).sorted(Comparator.comparing(MuteInfo::getId));
  }

  @Nullable
  private BuildPromotion findPromotionByBuildId(@NotNull final Long buildId) {
    SBuild buildInstanceById = myServiceLocator.getSingletonService(BuildsManager.class).findBuildInstanceById(buildId);
    return buildInstanceById == null ? null : buildInstanceById.getBuildPromotion();
  }

  @NotNull
  private MuteInfo findMuteById(@NotNull final Integer id) {
    Optional<MuteInfo> result = getMuteInfosForProject(myProjectFinder.getRootProject()).filter(muteInfo -> id.equals(muteInfo.getId())).findAny(); //todo: not optimal at all
    if (result.isPresent()) return result.get();
    throw new NotFoundException("No mute with id '" + id + "' found");

    /* this returns the original mutes state, so does not work (TW-53393)
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
    */
  }

  private class MuteFinderBuilder extends TypedFinderBuilder<MuteInfo> {

    MuteFinderBuilder() {
      name("MuteFinder");
      singleDimension(dimension -> {
        // no dimensions found, assume it's id
        try {
          Long value = Long.valueOf(dimension);
          return Collections.singletonList(findMuteById(value.intValue()));
        } catch (NumberFormatException nfe) {
          throw new LocatorProcessException("Invalid single dimension value: '" + dimension + "'. Expected a number.");
        }
      });

      dimensionLong(ID).description("internal mute id")
                       .filter((value, item) -> value.equals(item.getId().longValue()))
                       .toItems(dimension -> Collections.singletonList(findMuteById(dimension.intValue())));

      dimensionTests(TEST, myServiceLocator).description("test for which mute is assigned").valueForDefaultFilter(muteInfo -> new HashSet<>(muteInfo.getTests()));
                                            //.toItems(dimension -> dimension.stream().
                                            //  flatMap(sTest -> {
                                            //    HashMap<Integer, MuteInfoWrapper> result = new HashMap<>();
                                            //    String rootProjectId = myProjectFinder.getRootProject().getProjectId();
                                            //    CurrentMuteInfo currentMuteInfo = myProblemMutingService.getTestCurrentMuteInfo(rootProjectId, sTest.getTestNameId()); //this does not return all the muting data, only for the tests passed
                                            //    if (currentMuteInfo == null) return Stream.empty();
                                            //    getActualCurrentMuteTests(sTest.getTestNameId(), currentMuteInfo, result);
                                            //    return result.values().stream();
                                            //  }).collect(Collectors.toList()));

      dimensionProblems(PROBLEM, myServiceLocator).description("problem for which mute is assigned").
        filter((problemWrappers, item) -> problemWrappers.stream().anyMatch(problemWrapper -> item.getBuildProblemIds().contains(problemWrapper.getId().intValue())));
                                                    //.toItems(dimension -> dimension.stream().
                                                    //  flatMap(problem -> {
                                                    //    HashMap<Integer, MuteInfoWrapper> result = new HashMap<>();
                                                    //    String rootProjectId = myProjectFinder.getRootProject().getProjectId();
                                                    //    CurrentMuteInfo currentMuteInfo =
                                                    //      myProblemMutingService.getBuildProblemCurrentMuteInfo(rootProjectId, problem.getId().intValue());  //this does not return all the muting data, only for the problem passed
                                                    //    if (currentMuteInfo == null) return Stream.empty();
                                                    //    getActualCurrentMuteProblems(problem.getId().intValue(), currentMuteInfo, result);
      //    return result.values().stream();
      //  }).collect(Collectors.toList()));

      dimensionProjects(AFFECTED_PROJECT, myServiceLocator).description("project affected by the mutes")
                                                           .filter((projects, item) -> {
                                                             final SProject assignmentProject = item.getProject();
                                                             return (assignmentProject != null && ProjectFinder.isSameOrParent(projects, assignmentProject));
                                                           })
                                                           .toItems(dimension -> dimension.stream().flatMap(MuteFinder.this::getMuteInfosForProject).collect(Collectors.toList()));

      dimensionProjects(PROJECT, myServiceLocator).description("project in which mute is assigned")
                                                  .valueForDefaultFilter(muteInfo -> {
                                                    if (canView(muteInfo)) {
                                                      return Collections.singleton(muteInfo.getProject());
                                                    } else {
                                                      return Collections.emptySet();
                                                    }
                                                  }); //todo: add toItems?

      dimensionTimeCondition(CREATION_DATE, myTimeCondition).description("mute creation time")
                                                            .filter((timeCondition, item) -> timeCondition.matches(item.getMutingTime()));
      dimensionTimeCondition(UNMUTE_DATE, myTimeCondition).description("automatic unmute time")
                                                          .filter((timeCondition, item) -> {
                                                            Date unmuteTime = item.getAutoUnmuteOptions().getUnmuteByTime();
                                                            return unmuteTime != null && timeCondition.matches(unmuteTime);
                                                          });
      dimensionUsers(REPORTER, myServiceLocator).description("muting user")
                                                .filter((users, item) -> users.stream().map(User::getId).collect(Collectors.toSet()).contains(item.getMutingUserId()));
      dimensionFixedText(TYPE, ProblemTarget.getKnownTypesForMute()).description("what is muted").valueForDefaultFilter(ProblemTarget::getType)
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
        valueForDefaultFilter(muteInfo -> String.valueOf(Resolution.ResolutionType.getType(muteInfo.getAutoUnmuteOptions())));

      multipleConvertToItemHolder(DimensionCondition.ALWAYS, dimensions -> ItemHolder.of(getMuteInfosForProject(myProjectFinder.getRootProject())));

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

      locatorProvider(MuteFinder::getLocator);
//      containerSetProvider(() -> new TreeSet<MuteInfo>(Comparator.comparing(muteInfo -> muteInfo.getId()))); //todo: implement sorting here
    }
  }

  @NotNull
  private Stream<MuteInfo> getTestsMutes(final @NotNull SProject project) {
    /* this returns the original mutes state, so does not work (TW-53393)
    return myProblemMutingService.getTestsCurrentMuteInfo(project).values().stream().flatMap(currentMute -> getMutes(currentMute)).distinct(); //check is distinct can be reimplemented to be more effective here
    */
    Map<Integer, MuteInfoWrapper> result = new TreeMap<>();
    Map<Long, CurrentMuteInfo> testsCurrentMuteInfo = myProblemMutingService.getTestsCurrentMuteInfo(project);
    for (Map.Entry<Long, CurrentMuteInfo> currentMuteEntry : testsCurrentMuteInfo.entrySet()) {
      getActualCurrentMuteTests(currentMuteEntry.getKey(), currentMuteEntry.getValue(), result);
    }
    return result.values().stream().map(muteInfo -> muteInfo);
  }

  @NotNull
  private Stream<MuteInfo> getProblemsMutes(final @NotNull SProject project) {
    /* this returns the original mutes state, so does not work (TW-53393)
    return myProblemMutingService.getBuildProblemsCurrentMuteInfo(project).values().stream().flatMap(currentMute -> getMutes(currentMute)).distinct();  //check is distinct can be reimplemented to be more effective here
    */
    Map<Integer, MuteInfoWrapper> result = new TreeMap<>();
    Map<Integer, CurrentMuteInfo> testsCurrentMuteInfo = myProblemMutingService.getBuildProblemsCurrentMuteInfo(project);
    for (Map.Entry<Integer, CurrentMuteInfo> currentMuteEntry : testsCurrentMuteInfo.entrySet()) {
      getActualCurrentMuteProblems(currentMuteEntry.getKey(), currentMuteEntry.getValue(), result);
    }
    return result.values().stream().map(muteInfo -> muteInfo);
  }

  /*
  @NotNull
  private Stream<MuteInfo> getMutes(@Nullable final CurrentMuteInfo currentMuteInfo) {
    if (currentMuteInfo == null) return Stream.empty();
    return Stream.concat(currentMuteInfo.getProjectsMuteInfo().values().stream(),
                         currentMuteInfo.getMuteInfoGroups().keySet().stream());
  }
  */

  private void getActualCurrentMuteTests(@NotNull final Long testNameId, @NotNull final CurrentMuteInfo currentMute, @NotNull final Map<Integer, MuteInfoWrapper> result) {
    for (Map.Entry<SProject, MuteInfo> muteInfoEntry : currentMute.getProjectsMuteInfo().entrySet()) {
      //ignoring project - should be the same as in MuteInfo
      getWrapped(result, muteInfoEntry.getValue()).addTest(testNameId);
    }
    for (Map.Entry<SBuildType, MuteInfo> muteInfoEntry : currentMute.getBuildTypeMuteInfo().entrySet()) {
      getWrapped(result, muteInfoEntry.getValue()).addBuildType(muteInfoEntry.getKey().getInternalId()).addTest(testNameId);
    }
  }

  private void getActualCurrentMuteProblems(@NotNull final Integer problemId, @NotNull final CurrentMuteInfo currentMute, @NotNull final Map<Integer, MuteInfoWrapper> result) {
    for (Map.Entry<SProject, MuteInfo> muteInfoEntry : currentMute.getProjectsMuteInfo().entrySet()) {
      //ignoring project - should be the same as in MuteInfo
      getWrapped(result, muteInfoEntry.getValue()).addProblem(problemId);
    }
    for (Map.Entry<SBuildType, MuteInfo> muteInfoEntry : currentMute.getBuildTypeMuteInfo().entrySet()) {
      getWrapped(result, muteInfoEntry.getValue()).addBuildType(muteInfoEntry.getKey().getInternalId()).addProblem(problemId);
    }
  }

  private MuteInfoWrapper getWrapped(@NotNull final Map<Integer, MuteInfoWrapper> cache, @NotNull final MuteInfo mute) {
    MuteInfoWrapper cached = cache.get(mute.getId());
    if (cached == null) {
      cached = new MuteInfoWrapper(mute);
      cache.put(mute.getId(), cached);
    }
    return cached;
  }

  /**
   * Overrides tests, problems and build types to represent the actual current data, not the data at the moment of the mute creation
   */
  class MuteInfoWrapper implements MuteInfo {
    @NotNull private final MuteInfo myMuteInfo;
    @NotNull private final Set<Long> myTestNameIds = new TreeSet<>();
    @NotNull private final Collection<Integer> myBuildProblemIds = new TreeSet<>();
    @NotNull private final Collection<String> myBuildTypeIds = new TreeSet<>();

    public MuteInfoWrapper(@NotNull final MuteInfo muteInfo) {
      myMuteInfo = muteInfo;
    }

    public MuteInfoWrapper addTest(@NotNull Long testNameId) {
      myTestNameIds.add(testNameId);
      return this;
    }

    public MuteInfoWrapper addProblem(@NotNull Integer problemId) {
      myBuildProblemIds.add(problemId);
      return this;
    }

    public MuteInfoWrapper addBuildType(@NotNull String buildTypeInternalId) {
      myBuildTypeIds.add(buildTypeInternalId);
      return this;
    }

    @Override
    @NotNull
    public Integer getId() {
      return myMuteInfo.getId();
    }

    @Override
    @NotNull
    public String getProjectId() {
      return myMuteInfo.getProjectId();
    }

    @Override
    @Nullable
    public SProject getProject() {
      return myMuteInfo.getProject();
    }

    @Override
    public long getMutingUserId() {
      return myMuteInfo.getMutingUserId();
    }

    @Override
    @Nullable
    public User getMutingUser() {
      return myMuteInfo.getMutingUser();
    }

    @Override
    @NotNull
    public Date getMutingTime() {
      return myMuteInfo.getMutingTime();
    }

    @Override
    @Nullable
    public String getMutingComment() {
      return myMuteInfo.getMutingComment();
    }

    @Override
    @NotNull
    public MuteScope getScope() {
      MuteScope scope = myMuteInfo.getScope();
      return new MuteScope() {
        @Override
        @NotNull
        public ScopeType getScopeType() {
          return scope.getScopeType();
        }

        @Override
        @Nullable
        public String getProjectId() {
          return scope.getProjectId();
        }

        @Override
        @Nullable
        public Collection<String> getBuildTypeIds() {
          return myBuildTypeIds;
        }

        @Override
        @Nullable
        public Long getBuildId() {
          return scope.getBuildId();
        }
      };
    }

    @Override
    @NotNull
    public Collection<Long> getTestNameIds() {
      return myTestNameIds;
    }

    @Override
    @NotNull
    public Collection<STest> getTests() {
      // see MuteInfoRecord.getTests()
      STestManager testManager = myLowLevelMutingService.getTestManager();
      List<STest> tests = new ArrayList<>(myTestNameIds.size());
      Map<Long, STest> foundTests = testManager.createTests(myTestNameIds, getProjectId());

      for (Long id: myTestNameIds) {
        final STest test = foundTests.get(id);
        if (test != null)
          tests.add(test);
      }
      return tests;
    }

    @Override
    @NotNull
    public Collection<Integer> getBuildProblemIds() {
      return myBuildProblemIds;
    }

    @Override
    @NotNull
    public UnmuteOptions getAutoUnmuteOptions() {
      return myMuteInfo.getAutoUnmuteOptions();
    }
  }
}
