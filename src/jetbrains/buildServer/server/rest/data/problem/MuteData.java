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

package jetbrains.buildServer.server.rest.data.problem;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.UserFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import jetbrains.buildServer.serverSide.mute.MuteScope;
import jetbrains.buildServer.serverSide.mute.ProblemMutingService;
import jetbrains.buildServer.serverSide.mute.UnmuteOptions;
import jetbrains.buildServer.serverSide.problems.BuildProblemInfo;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 * Date: 18/08/2017
 */
public class MuteData {
  @NotNull private final MuteScope myScope;
  @Nullable private final String myComment;
  @NotNull private final Collection<STest> myTests;
  @NotNull private final Collection<Long> myProblemIds;
  @Nullable private UnmuteOptions myUnmuteData;

  @NotNull private final SUser myCurrentUser;
  @NotNull private final ProblemMutingService myProblemMutingService;
  @NotNull private final ProjectManager myProjectManager;

  public MuteData(@NotNull final MuteScope scope,
                  @Nullable final String comment,
                  @NotNull final Collection<STest> tests,
                  @NotNull final Collection<Long> problemIds,
                  @NotNull final UnmuteOptions unmuteData,
                  @NotNull final ServiceLocator serviceLocator) {
    this (scope, comment, tests, problemIds, serviceLocator);
    myUnmuteData = unmuteData;
  }

  /**
   * Can be used only for following unmute
   */
  public MuteData(@NotNull final MuteScope scope,
                  @Nullable final String comment,
                  @NotNull final Collection<STest> tests,
                  @NotNull final Collection<Long> problemIds,
                  @NotNull final ServiceLocator serviceLocator) {
    myScope = scope;
    myComment = comment;
    myTests = tests;
    myProblemIds = problemIds;
    myUnmuteData = null;

    SUser currentUser = serviceLocator.getSingletonService(UserFinder.class).getCurrentUser();
    if (currentUser == null) {
      throw new BadRequestException("Cannot mute when there is no current user");
    }
    myCurrentUser = currentUser;

    myProblemMutingService = serviceLocator.getSingletonService(ProblemMutingService.class);
    myProjectManager = serviceLocator.getSingletonService(ProjectManager.class);
  }

  @NotNull
  public MuteInfo mute() {
    if (myUnmuteData == null) {
        throw new BadRequestException("Bad 'mute' entity: missing 'resolution'");
    }
    switch (myScope.getScopeType()) {
      case IN_PROJECT: {
        if (!myTests.isEmpty()) {
          return myProblemMutingService.muteTestsInProject(myCurrentUser, myComment, myUnmuteData.isUnmuteWhenFixed(), myUnmuteData.getUnmuteByTime(), getProject(), myTests);
        }
        if (!myProblemIds.isEmpty()) {
          return myProblemMutingService
            .muteProblemsInProject(myCurrentUser, myComment, myUnmuteData.isUnmuteWhenFixed(), myUnmuteData.getUnmuteByTime(), getProject(), getProblemInfos());
          //assuming only id is used from problemInfo, this is actually TeamCity API issue as the API gets ids but accepts objects
          //todo: all BuildProblemInfo fields are used in BuildProblemAuditId.fromBuildProblem - check here and below!
        }
        break;
      }

      case IN_CONFIGURATION: {
        if (!myTests.isEmpty()) {
          return myProblemMutingService.muteTestsInBuildTypes(myCurrentUser, myComment, myUnmuteData.isUnmuteWhenFixed(), myUnmuteData.getUnmuteByTime(), getBuildTypes(), myTests, true);
          //a question: may be withReducingScope should not be true here and below?
        }
        if (!myProblemIds.isEmpty()){
          return myProblemMutingService
            .muteProblemsInBuildTypes(myCurrentUser, myComment, myUnmuteData.isUnmuteWhenFixed(), myUnmuteData.getUnmuteByTime(), getBuildTypes(), getProblemInfos(), true);
        }
        break;
      }

      case IN_ONE_BUILD:
        throw new BadRequestException("Managing build-level mutes is not supported via this request");
    }

    throw new BadRequestException("No conditions matched: nothing to mute");
  }

  public void unmute() {
    switch (myScope.getScopeType()) {
      case IN_PROJECT: {
        if (!myTests.isEmpty()) {
          myProblemMutingService.unmuteTests(myCurrentUser, null, getProject(), myTests);
        }
        if (!myProblemIds.isEmpty()) {
          myProblemMutingService.unmuteProblems(myCurrentUser, null, getProject(), getProblemInfos());
        }
        return;
      }

      case IN_CONFIGURATION: {
        if (!myTests.isEmpty()) {
          getBuildTypes().forEach(buildType -> myProblemMutingService.unmuteTests(myCurrentUser, null, buildType, myTests));
        }
        if (!myProblemIds.isEmpty()){
          getBuildTypes().forEach(buildType -> myProblemMutingService.unmuteProblems(myCurrentUser, null, buildType, getProblemInfos()));
        }
        return;
      }

      case IN_ONE_BUILD:
        throw new BadRequestException("Managing build-level mutes is not supported via this request");
    }

    throw new BadRequestException("No conditions matched: nothing to mute");
  }


  @NotNull
  private List<BuildProblemInfo> getProblemInfos() {
    String rootProjectId = myProjectManager.getRootProject().getProjectId();
    //assuming only id is used from problemInfo, this is actually TeamCity API issue as the API gets ids but accepts objects
    return myProblemIds.stream().map(problemId -> ProblemWrapper.getBuildProblemInfo(problemId.intValue(), rootProjectId)).collect(Collectors.toList());
  }


  private SProject myCachedProject;

  @NotNull
  private SProject getProject() {
    if (myCachedProject == null) {
      myCachedProject = myProjectManager.findProjectById(myScope.getProjectId());
      if (myCachedProject == null) throw new BadRequestException("Cannot find project by internal id '" + myScope.getProjectId() + "'");
    }
    return myCachedProject;
  }

  private List<SBuildType> myCachedBuildTypes;
  @NotNull
  private List<SBuildType> getBuildTypes() {
    if (myCachedBuildTypes == null) {
      Collection<String> buildTypeIds = myScope.getBuildTypeIds();
      if (buildTypeIds == null) throw new BadRequestException("No buildTypes found while operation is build type related");
      myCachedBuildTypes = buildTypeIds.stream().map(buildTypeId -> myProjectManager.findBuildTypeById(buildTypeId)).filter(Objects::nonNull).collect(Collectors.toList());
    }
    return myCachedBuildTypes;
  }
}
