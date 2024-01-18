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

package jetbrains.buildServer.server.rest.data.pages.problems;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.responsibility.BuildProblemResponsibilityEntry;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.finder.DelegatingFinder;
import jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder;
import jetbrains.buildServer.server.rest.data.finder.syntax.CommonLocatorDimensions;
import jetbrains.buildServer.server.rest.data.locator.BooleanValue;
import jetbrains.buildServer.server.rest.data.locator.Dimension;
import jetbrains.buildServer.server.rest.data.locator.Syntax;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyInjectable;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.CurrentProblemsManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.mute.CurrentMuteInfo;
import jetbrains.buildServer.serverSide.mute.ProblemMutingService;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import static jetbrains.buildServer.server.rest.data.pages.problems.BuildProblemEntriesFinder.LocatorDefinition.*;

@JerseyInjectable
@Component
public class BuildProblemEntriesFinder extends DelegatingFinder<BuildProblemEntry> {
  private final CurrentProblemsManager myCurrentProblemsManager;
  private final ServiceLocator myServiceLocator;
  private final ProblemMutingService myProblemMutingService;

  public static class LocatorDefinition {
    public static final Dimension AFFECTED_PROJECT = Dimension.ofName("affectedProject")
                                                              .description("Problems with builds in all nested build types of the project.")
                                                              .syntax(Syntax.forLocator(LocatorName.PROJECT)).build();
    public static final Dimension CURRENTLY_INVESTIGATED = Dimension.ofName("currentlyInvestigated")
                                                                    .description("Problems which are investigated.")
                                                                    .syntax(new BooleanValue()).build();
    public static final Dimension BUILD_TYPE = Dimension.ofName("buildType")
                                                        .description("Problems with builds in a build type.")
                                                        .syntax(Syntax.forLocator(LocatorName.BUILD_TYPE))
                                                        .build();
    public static final Dimension CURRENTLY_MUTED = Dimension.ofName("currentlyMuted")
                                                             .description("Problems which are muted.")
                                                             .syntax(new BooleanValue()).build();
    public static final Dimension ASSIGNEE = Dimension.ofName("assignee")
                                                      .description("Investigated by a user.")
                                                      .syntax(Syntax.forLocator(LocatorName.USER)).build();
  }

  public BuildProblemEntriesFinder(@NotNull CurrentProblemsManager currentProblemsManager,
                                   @NotNull ProblemMutingService problemMutingService,
                                   @NotNull ServiceLocator serviceLocator) {
    myCurrentProblemsManager = currentProblemsManager;
    myProblemMutingService = problemMutingService;
    myServiceLocator = serviceLocator;


    setDelegate(new Builder().build());
  }

  private class Builder extends TypedFinderBuilder<BuildProblemEntry> {
    public Builder() {
      dimensionProjects(AFFECTED_PROJECT, myServiceLocator)
        .toItems(BuildProblemEntriesFinder.this::resolveProblemsByAffectedProject);

      dimensionBuildTypes(BUILD_TYPE, myServiceLocator)
        .toItems(BuildProblemEntriesFinder.this::resolveProblemsByBuildType);

      dimensionUsers(ASSIGNEE, myServiceLocator)
        .filter(BuildProblemEntriesFinder.this::filterByResponsibleUser);

      dimensionBoolean(CURRENTLY_INVESTIGATED)
        .filter(BuildProblemEntriesFinder.this::filterByInvestigated);

      dimensionBoolean(CURRENTLY_MUTED)
        .filter(BuildProblemEntriesFinder.this::filterByMuted);

      defaults(
        this::countIsNotSet,
        new NameValuePairs().add(CommonLocatorDimensions.PAGER_COUNT.getName(), "-1")
      );

      name(BuildProblemEntriesFinder.class.getSimpleName());
    }

    private boolean countIsNotSet(@NotNull Locator locator) {
      return !locator.isAnyPresent(CommonLocatorDimensions.PAGER_COUNT);
    }
  }

  private boolean filterByMuted(@NotNull Boolean currentlyMuted, @NotNull BuildProblemEntry problemEntry) {
    boolean muteIsPresent = problemEntry.getMuteInfos() != null;

    return currentlyMuted.equals(muteIsPresent);
  }

  private boolean filterByInvestigated(@NotNull Boolean currentlyInvestigated, @NotNull BuildProblemEntry problemEntry) {
    boolean responsibilityIsPresent = problemEntry.getProblem().getResponsibility() != null;

    return currentlyInvestigated.equals(responsibilityIsPresent);
  }

  private boolean filterByResponsibleUser(@NotNull List<SUser> sUsers, @NotNull BuildProblemEntry problemEntry) {
    SUser user = sUsers.get(0);
    if (user == null) {
      return false;
    }

    BuildProblemResponsibilityEntry responsibility = problemEntry.getProblem().getResponsibility();
    if (responsibility == null) {
      return false;
    }

    return responsibility.getResponsibleUser().getId() == user.getId();
  }

  @NotNull
  private List<BuildProblemEntry> resolveProblemsByAffectedProject(@NotNull List<SProject> projects) {
    SProject project = projects.get(0);
    List<BuildProblem> problems = myCurrentProblemsManager.getProblemsForProject(project).getBuildProblems();

    Map<Integer, CurrentMuteInfo> mutes = myProblemMutingService.getBuildProblemsCurrentMuteInfo(project);
    return problems.stream()
                   .map(p -> {
                     CurrentMuteInfo currentMuteInfo = mutes.get(p.getId());
                     if (currentMuteInfo == null) {
                       return new BuildProblemEntry(p, null);
                     }
                     return new BuildProblemEntry(p, currentMuteInfo.getProjectsMuteInfo().values());
                   })
                   .collect(Collectors.toList());
  }

  @NotNull
  private List<BuildProblemEntry> resolveProblemsByBuildType(@NotNull List<BuildTypeOrTemplate> buildTypes) {
    SBuildType buildType = buildTypes.get(0).getBuildType();
    if(buildType == null) {
      return Collections.emptyList();
    }

    // Inefficient, requires a better TeamCity api for this to be more efficient.
    List<BuildProblem> problems = myCurrentProblemsManager.getProblemsForProject(buildType.getProject()).getBuildProblems().stream()
                                                          .filter(problem -> buildType.getBuildTypeId().equals(problem.getBuildPromotion().getBuildTypeId()))
                                                          .collect(Collectors.toList());

    Map<Integer, CurrentMuteInfo> mutes = myProblemMutingService.getBuildProblemsCurrentMuteInfo(buildType.getProject());
    return problems.stream()
                   .map(p -> {
                     CurrentMuteInfo currentMuteInfo = mutes.get(p.getId());
                     if (currentMuteInfo == null) {
                       return new BuildProblemEntry(p, null);
                     }
                     return new BuildProblemEntry(p, currentMuteInfo.getProjectsMuteInfo().values());
                   })
                   .collect(Collectors.toList());
  }
}
