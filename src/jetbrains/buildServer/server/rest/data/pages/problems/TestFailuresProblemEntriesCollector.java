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

import java.util.*;
import java.util.Map.Entry;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.responsibility.TestNameResponsibilityFacade;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.finder.impl.ProjectFinder;
import jetbrains.buildServer.server.rest.data.finder.impl.UserFinder;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.data.problem.Orders;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.projects.ProjectUtil;
import jetbrains.buildServer.serverSide.mute.CurrentMuteInfo;
import jetbrains.buildServer.serverSide.mute.ProblemMutingService;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import static jetbrains.buildServer.server.rest.data.locator.definition.PageableLocatorDefinition.*;
import static jetbrains.buildServer.server.rest.data.pages.problems.TestFailuresProblemEntriesLocatorDefinition.*;

@JerseyContextSingleton
@Component
public class TestFailuresProblemEntriesCollector {
  static final Orders<TestFailuresProblemEntry> SUPPORTED_ORDERS = new Orders<TestFailuresProblemEntry>()
    .add("newFailure", Comparator.comparing(e -> e.isNewFailure()))
    .add("failureCount", Comparator.comparing(e -> {
      Set<SBuildType> failingBuildTypes = e.getFailingBuildTypes();
      if (failingBuildTypes == null) {
        return 0;
      }
      return failingBuildTypes.size();
    }))
    .add("name", Comparator.comparing(tr -> tr.getTest().getName().getShortName(), String.CASE_INSENSITIVE_ORDER));

  private static final long DEFAULT_PAGE_SIZE = 100;

  /*
  TODO:

   - flaky (add this later)
   - count of the sub requests (investigations, mutes, etc)
   */
  private final STestManager myTestManager;
  private final ProjectFinder myProjectFinder;
  private final ProblemMutingService myMutingService;
  private final TestNameResponsibilityFacade myTestNameResponsibilityFacade;
  private final UserFinder myUserFinder;
  private final CurrentProblemsManager myCurrentProblemsManager;

  public TestFailuresProblemEntriesCollector(@NotNull ProjectFinder projectFinder,
                                             @NotNull UserFinder userFinder,
                                             @NotNull TestNameResponsibilityFacade testNameResponsibilityFacade,
                                             @NotNull ProblemMutingService problemMutingService,
                                             @NotNull CurrentProblemsManager currentProblemsManager,
                                             @NotNull STestManager testsManager) {
    myProjectFinder = projectFinder;
    myUserFinder = userFinder;
    myTestManager = testsManager;
    myMutingService = problemMutingService;
    myCurrentProblemsManager = currentProblemsManager;
    myTestNameResponsibilityFacade = testNameResponsibilityFacade;
  }

  @NotNull
  public PagedSearchResult<TestFailuresProblemEntry> getItems(@NotNull Locator locator) {
    if (!locator.isAnyPresent(AFFECTED_PROJECT)) {
      throw new BadRequestException(String.format("Dimension '%s' must be set.", AFFECTED_PROJECT.getName()));
    }
    if (!Boolean.TRUE.equals(locator.lookupSingleDimensionValueAsBoolean(CURRENTLY_INVESTIGATED)) && locator.isAnyPresent(ASSIGNEE)) {
      // assignee is set, but currently investigated is false or any, does not make a lot of sense.
      throw new BadRequestException(String.format("When '%s' is set, then '%s' must be set to 'true'.", ASSIGNEE.getName(), CURRENTLY_INVESTIGATED.getName()));
    }

    SProject project = myProjectFinder.getItem(locator.getSingleDimensionValue(AFFECTED_PROJECT));
    SUser investigator = null;
    if(locator.isUnused(ASSIGNEE)) {
      investigator = myUserFinder.getItem(locator.getSingleDimensionValue(ASSIGNEE));
    }

    // Set defaults
    if(!locator.isAnyPresent(CURRENTLY_FAILING)) {
      locator.setDimension(CURRENTLY_FAILING, "true");
    }
    if(!locator.isAnyPresent(ORDER_BY)) {
      locator.setDimension(ORDER_BY, "newFailure");
    }

    boolean returnOnlyFailing    = Boolean.TRUE.equals(locator.lookupSingleDimensionValueAsBoolean(CURRENTLY_FAILING));
    boolean returnOnlyNotFailing = Boolean.FALSE.equals(locator.lookupSingleDimensionValueAsBoolean(CURRENTLY_FAILING));
    boolean returnOnlyMuted    = Boolean.TRUE.equals(locator.lookupSingleDimensionValueAsBoolean(CURRENTLY_MUTED));
    boolean returnOnlyNotMuted = Boolean.FALSE.equals(locator.getSingleDimensionValueAsBoolean(CURRENTLY_MUTED));
    boolean returnOnlyInvestigated    = Boolean.TRUE.equals(locator.lookupSingleDimensionValueAsBoolean(CURRENTLY_INVESTIGATED));
    boolean returnOnlyNotInvestigated = Boolean.FALSE.equals(locator.getSingleDimensionValueAsBoolean(CURRENTLY_INVESTIGATED));


    Map<Long, List<InvestigationWrapper>> investigations = new HashMap<>();
    investigations.putAll(getInvestigations(project, investigator));

    Map<Long, List<SingleTestMuteInfoView>> mutes = new HashMap<>();
    mutes.putAll(getMutes(project));

    Map<Long, List<STestRun>> failingTests = new HashMap<>();
    failingTests.putAll(getFailingTests(project));

    Set<Long> allTestNames = new HashSet<>();
    allTestNames.addAll(investigations.keySet());
    allTestNames.addAll(mutes.keySet());
    allTestNames.addAll(failingTests.keySet());

    LongFunction<STest> testResolver = getTestResolver(project);
    Stream.Builder<TestFailuresProblemEntry> streamBuilder = Stream.builder();
    for (Long testNameId : allTestNames) {
      TestFailuresProblemEntry entry = new TestFailuresProblemEntry(testResolver);
      if (returnOnlyMuted && !mutes.containsKey(testNameId) || returnOnlyNotMuted && mutes.containsKey(testNameId)) {
        continue;
      }
      if (returnOnlyInvestigated && !investigations.containsKey(testNameId) || returnOnlyNotInvestigated && investigations.containsKey(testNameId)) {
        continue;
      }
      if (returnOnlyFailing && !failingTests.containsKey(testNameId) || returnOnlyNotFailing && failingTests.containsKey(testNameId)) {
        continue;
      }

      if (failingTests.containsKey(testNameId)) {
        entry.setRecentFailures(failingTests.get(testNameId));
      }

      if (investigations.containsKey(testNameId)) {
        entry.setInvestigations(investigations.get(testNameId));
      }

      if (mutes.containsKey(testNameId)) {
        entry.setMutes(mutes.get(testNameId));
      }
      entry.setTestNameId(testNameId);
      streamBuilder.add(entry);
    }

    Stream<TestFailuresProblemEntry> result = streamBuilder.build();
    if (locator.isAnyPresent(ORDER_BY)) {
      result = result.sorted(SUPPORTED_ORDERS.getComparator(locator.getSingleDimensionValue(ORDER_BY)));
    }

    long start = locator.getSingleDimensionValueAsLong(PAGER_START, 0L);
    if(start < 0) {
      throw new BadRequestException(String.format("'%s' must have non-negative value.", PAGER_START.getName()));
    }
    result = result.skip(start);

    long count = locator.getSingleDimensionValueAsLong(PAGER_COUNT, DEFAULT_PAGE_SIZE);
    if(count < 0) {
      throw new BadRequestException(String.format("'%s' must have non-negative value.", PAGER_COUNT.getName()));
    }
    result = result.limit(count);

    return new PagedSearchResult<>(result.collect(Collectors.toList()), start, (int) count);
  }

  @NotNull
  private LongFunction<STest> getTestResolver(@NotNull SProject project) {
    return testNameId -> myTestManager.findTest(testNameId, project.getProjectId());
  }

  @NotNull
  private Map<Long, List<SingleTestMuteInfoView>> getMutes(@NotNull SProject project) {
    return myMutingService.getTestsCurrentMuteInfo(project).entrySet().stream()
                          .collect(Collectors.toMap(
                            Entry::getKey,
                            entry -> transformMutes(entry.getKey(), entry.getValue()))
                          );
  }

  @NotNull
  private List<SingleTestMuteInfoView> transformMutes(@NotNull Long testNameId, @NotNull CurrentMuteInfo cmi) {
    return cmi.getProjectsMuteInfo().values().stream()
              .map(mi -> new SingleTestMuteInfoView(mi, testNameId))
              .collect(Collectors.toList());
  }

  @NotNull
  private Map<Long, List<STestRun>> getFailingTests(@NotNull SProject project) {
    CurrentProblems currentProblems = myCurrentProblemsManager.getProblemsForProject(project);

    boolean excludeArchived = !project.isArchived();
    Map<Long, List<STestRun>> result = new HashMap<>();
    for(List<STestRun> failingTests : currentProblems.getFailingTests().values()) {
      List<STestRun> testRuns = failingTests;

      if(excludeArchived) {
        testRuns = testRuns.stream()
                           .filter(tr -> {
                             SProject p = ((TestEx)tr.getTest()).getProject();
                             return p != null && !p.isArchived();
                           })
                           .collect(Collectors.toList());
      }

      if(testRuns.isEmpty()) {
        continue;
      }

      long testNameId = testRuns.get(0).getTest().getTestNameId();
      result.put(testNameId, testRuns);
    }

    return result;
  }

  @NotNull
  private Map<Long, List<InvestigationWrapper>> getInvestigations(@NotNull SProject project, @Nullable SUser assignee) {
    Set<String> projects = ProjectUtil.getProjectParentsAndChildrenInternalIds(project);

    return myTestNameResponsibilityFacade.getUserTestNameResponsibilities(assignee, projects).stream()
                                         .filter(tre -> tre.getState().isActive())
                                         .map(InvestigationWrapper::new)
                                         .collect(Collectors.groupingBy(iw -> iw.getTestRE().getTestNameId()));
  }
}
