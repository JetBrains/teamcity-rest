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

package jetbrains.buildServer.server.rest.data.problem;

import com.google.common.collect.ComparisonChain;
import java.util.*;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.responsibility.TestNameResponsibilityEntry;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.problem.scope.TestScopeFilter;
import jetbrains.buildServer.server.rest.data.problem.scope.TestScopeFilterProducer;
import jetbrains.buildServer.server.rest.data.util.AggregatingItemHolder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrence;
import jetbrains.buildServer.server.rest.request.BuildRequest;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorDimensionDataType;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.fieldInclusion.FieldInclusionChecker;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.mute.CurrentMuteInfo;
import jetbrains.buildServer.serverSide.tests.TestHistory;
import jetbrains.buildServer.tests.TestName;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.web.util.SessionUser;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.serverSide.BuildStatisticsOptions.ALL_TESTS_NO_DETAILS;

/**
 * @author Yegor.Yarko
 *         Date: 17.11.13
 */
@LocatorResource(value = LocatorName.TEST_OCCURRENCE,
    extraDimensions = {AbstractFinder.DIMENSION_ID, AbstractFinder.DIMENSION_LOOKUP_LIMIT, PagerData.START, PagerData.COUNT, AbstractFinder.DIMENSION_ITEM},
    baseEntity = "TestOccurrence",
    examples = {
        "`currentlyInvestigated:true` — find last 100 test occurrences which are being currently investigated.",
        "`build:<buildLocator>` — find test occurrences under build found by `buildLocator`."
    }
)
public class TestOccurrenceFinder extends AbstractFinder<STestRun> {
  @LocatorDimension(value = "build", format = LocatorName.BUILD, notes = "Build locator.")
  public static final String BUILD = "build";
  @LocatorDimension(value = "test", format = LocatorName.TEST, notes = "Test locator.")
  private static final String TEST = "test";
  @LocatorDimension("name") private static final String NAME = "name"; //value condition for the test's name
  @LocatorDimension(value = "buildType", format = LocatorName.BUILD_TYPE, notes = "Build type locator.")
  private static final String BUILD_TYPE = "buildType";
  @LocatorDimension(value = "affectedProject", format = LocatorName.PROJECT, notes = "Project (direct or indirect parent) locator.")
  public static final String AFFECTED_PROJECT = "affectedProject";
  @LocatorDimension(value = "currentlyFailing", dataType = LocatorDimensionDataType.BOOLEAN, notes = "Is currently failing.")
  public static final String CURRENT = "currentlyFailing";
  @LocatorDimension(value = "status", allowableValues = "unknown,normal,warning,failure,error")
  public static final String STATUS = "status";
  @LocatorDimension("branch") private static final String BRANCH = "branch";
  @LocatorDimension(value = "ignored", dataType = LocatorDimensionDataType.BOOLEAN, notes = "Is ignored.")
  public static final String IGNORED = "ignored";
  @LocatorDimension(value = "currentlyInvestigated", dataType = LocatorDimensionDataType.BOOLEAN, notes = "Is currently investigated.")
  public static final String CURRENTLY_INVESTIGATED = "currentlyInvestigated";
  @LocatorDimension(value = "muted", dataType = LocatorDimensionDataType.BOOLEAN, notes = "Is muted.")
  public static final String MUTED = "muted";
  @LocatorDimension("newFailure") public static final String NEW_FAILURE = "newFailure";
  @LocatorDimension(value = "includePersonal", dataType = LocatorDimensionDataType.BOOLEAN)
  public static final String INCLUDE_PERSONAL = "includePersonal";

  /** Experimental dimension, allowed values = "active,fixed,givenUp,none"
   * Potential replacement for "currentlyInvestigated" dimension.
   */
  public static final String INVESTIGATION_STATE = "investigationState";

  /** Experimental dimension, defines scope filter **/
  public static final String SCOPE = "scope";

  /** Internal dimension, indicates that test runs must be returned from any builds regardles of them being personal or not.*/
  public static final String INCLUDE_ALL_PERSONAL = "includeAllPersonal";

  /** Internal dimension, stores id of the user who is making request. <br/>
   * See also {@link TestOccurrenceFinder#getPersonalBuildsFilter(Locator)},
   * {@link jetbrains.buildServer.server.rest.request.TestOccurrenceRequest#patchLocatorForPersonalBuilds(String, HttpServletRequest)  TestOccurrenceRequest.patchLocatorForPersonalBuilds}
   */
  public static final String PERSONAL_FOR_USER = "personalForUser";
  protected static final String EXPAND_INVOCATIONS = "expandInvocations"; //experimental
  protected static final String INVOCATIONS = "invocations"; //experimental
  protected static final String ORDER = "orderBy"; //highly experimental

  private static final SortTestRunsByNewComparator TEST_RUN_COMPARATOR = new SortTestRunsByNewComparator();

  // Data for requests with these TestOccurrence fields can be retrieved from ShortStatistics.
  private static final Set<String> FASTPATH_ALLOWED_FIELDS = new HashSet<String>(Arrays.asList("id", "href", "name", "status", "duration", "runOrder", "build", "test"));

  @NotNull private final TestFinder myTestFinder;
  @NotNull private final BuildFinder myBuildFinder;
  @NotNull private final BuildTypeFinder myBuildTypeFinder;
  @NotNull private final ProjectFinder myProjectFinder;

  @NotNull private final TestHistory myTestHistory;
  @NotNull private final CurrentProblemsManager myCurrentProblemsManager;
  @NotNull private final BranchFinder myBranchFinder;
  @NotNull private final TestScopeFilterProducer myTestScopeFilterProducer;

  public TestOccurrenceFinder(final @NotNull TestFinder testFinder,
                              final @NotNull BuildFinder buildFinder,
                              final @NotNull BuildTypeFinder buildTypeFinder,
                              final @NotNull ProjectFinder projectFinder,
                              final @NotNull TestHistory testHistory,
                              final @NotNull CurrentProblemsManager currentProblemsManager,
                              final @NotNull BranchFinder branchFinder,
                              final @NotNull TestScopeFilterProducer testScopeFilterProducer) {
    super(DIMENSION_ID, TEST, NAME, BUILD_TYPE, BUILD, AFFECTED_PROJECT, CURRENT, STATUS, BRANCH, IGNORED, MUTED, CURRENTLY_MUTED, CURRENTLY_INVESTIGATED, NEW_FAILURE, INCLUDE_PERSONAL, INCLUDE_ALL_PERSONAL);
    setHiddenDimensions(EXPAND_INVOCATIONS, INVOCATIONS);
    setHiddenDimensions(ORDER); //highly experiemntal
    setHiddenDimensions(PERSONAL_FOR_USER);
    setHiddenDimensions(INVESTIGATION_STATE); // highly experimental
    myTestFinder = testFinder;
    myBuildFinder = buildFinder;
    myBuildTypeFinder = buildTypeFinder;
    myProjectFinder = projectFinder;
    myTestHistory = testHistory;
    myCurrentProblemsManager = currentProblemsManager;
    myBranchFinder = branchFinder;
    myTestScopeFilterProducer = testScopeFilterProducer;
  }

  @Override
  public Long getDefaultPageItemsCount() {
    return (long)Constants.getDefaultPageItemsCount();
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final STestRun sTestRun) {
    return TestOccurrenceFinder.getTestRunLocator(sTestRun);
  }

  public static String getTestRunLocator(final @NotNull STestRun testRun) {
    return Locator.createEmptyLocator().setDimension(DIMENSION_ID, String.valueOf(testRun.getTestRunId())).
      setDimension(BUILD, BuildRequest.getBuildLocator(testRun.getBuild())).getStringRepresentation();
  }

  /** Ensures we don't include personal builds by default (except when build locator is provided) and sets an internal dimension with user id. */
  @Contract("!null, _ -> !null; _, !null -> !null")
  public static String patchLocatorForPersonalBuilds(@Nullable String locator, @Nullable HttpServletRequest request) {
    if(locator == null || request == null) {
      return locator;
    }

    Locator patchedLocator = new Locator(locator);
    if(patchedLocator.isAnyPresent(INCLUDE_ALL_PERSONAL)) {
      // We do not want somebody to set this dimension explicitely.
      throw new BadRequestException(String.format("%s dimension is not supported.", INCLUDE_ALL_PERSONAL));
    }

    patchedLocator.setDimensionIfNotPresent(INCLUDE_PERSONAL, Locator.BOOLEAN_FALSE);

    SUser user = SessionUser.getUser(request);
    if(user != null) {
      patchedLocator.setDimension(PERSONAL_FOR_USER, Long.toString(user.getId()));
    }

    return patchedLocator.getStringRepresentation();
  }

  public PagingItemFilter<STestRun> getPagingInvocationsFilter(@NotNull Fields invocationField) {
    Locator allowingAllPersonal = Locator.createEmptyLocator().setDimension(TestOccurrenceFinder.INCLUDE_ALL_PERSONAL, Locator.BOOLEAN_TRUE);
    String completeLocator = Locator.merge(allowingAllPersonal.getStringRepresentation(), invocationField.getLocator());

    ItemFilter<STestRun> filter = getFilter(completeLocator);

    return getPagingFilter(new Locator(completeLocator), filter);
  }

  public static String getTestRunLocator(final @NotNull STest test) {
    return Locator.createEmptyLocator().setDimension(TEST, TestFinder.getTestLocator(test)).getStringRepresentation();
  }

  public static String getTestRunLocator(final @NotNull SBuild build) {
    return Locator.createEmptyLocator().setDimension(BUILD, BuildRequest.getBuildLocator(build)).getStringRepresentation();
  }

  @LocatorDimension("currentlyMuted") private static final String CURRENTLY_MUTED = "currentlyMuted";

  @Override
  @Nullable
  public STestRun findSingleItem(@NotNull final Locator locator) {
    /*
    if (locator.isSingleValue()) {
      Long idDimension = locator.getSingleValueAsLong();
      if (idDimension != null) {
        STestRun item = findTestByTestRunId(idDimension);
        if (item != null) {
          return item;
        }
        throw new NotFoundException("No test run with id '" + idDimension + "' found.");
      }
    }
    */

    // dimension-specific item search

    Long idDimension = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (idDimension != null) {
      // Always return a test run from a personal build when requested by id.
      if(!locator.isSingleValue()) {
        locator.setDimensionIfNotPresent(INCLUDE_ALL_PERSONAL, Locator.BOOLEAN_TRUE);
      }

      String buildDimension = locator.getSingleDimensionValue(BUILD);
      if (buildDimension != null) {
        List<BuildPromotion> builds = myBuildFinder.getBuilds(null, buildDimension).myEntries;
        if (builds.size() != 1) {
          return null;
        }
        SBuild build = builds.get(0).getAssociatedBuild();
        if (build == null) {
          throw new NotFoundException("No running/finished build found by locator '" + buildDimension + "'");
        }
        STestRun item = findTestByTestRunId(idDimension, build);
        if (item != null) {
          if ((long)item.getTestRunId() == idDimension) {
            return processInvocationExpansion(item, locator.getSingleDimensionValueAsBoolean(EXPAND_INVOCATIONS));
          }
          if (!(item instanceof MultiTestRun)) return null;
          for (STestRun testRun : getInvocations(item)) {
            if ((long)testRun.getTestRunId() == idDimension) {
              return processInvocationExpansion(testRun, locator.getSingleDimensionValueAsBoolean(EXPAND_INVOCATIONS));
            }
          }
        }
        throw new NotFoundException("No test run with id '" + idDimension + "' found in build with id " + build.getBuildId());
      }
      throw new BadRequestException("Cannot find test by " + DIMENSION_ID + " only, make sure to specify " + BUILD + " locator.");
    }

    return null;
  }

  @NotNull
  @Override
  public ItemHolder<STestRun> getPrefilteredItems(@NotNull final Locator locator) {
    return getSortedItemHolder(getPrefilteredItemsInternal(locator), getComparator(locator));
  }

  @NotNull
  public static Set<STestRun> getCurrentOccurrences(@NotNull final SProject affectedProject, @NotNull final CurrentProblemsManager currentProblemsManager) {
    final CurrentProblems currentProblems = currentProblemsManager.getProblemsForProject(affectedProject);
    final Map<TestName, List<STestRun>> failingTests = currentProblems.getFailingTests();
    final Map<TestName, List<STestRun>> mutedTestFailures = currentProblems.getMutedTestFailures();
    final TreeSet<STestRun> result = new TreeSet<>(TEST_RUN_COMPARATOR);
    //todo: check whether STestRun is OK to put into the set
    for (List<STestRun> testRuns : failingTests.values()) {
      result.addAll(testRuns);
    }
    for (List<STestRun> testRuns : mutedTestFailures.values()) {
      result.addAll(testRuns);
    }
    return result;
  }

  @NotNull
  private static <T> ItemHolder<T> getSortedItemHolder(@NotNull final ItemHolder<T> baseHolder, @Nullable final Comparator<T> comparator) {
    if (comparator == null) return baseHolder;

    ArrayList<T> items = new ArrayList<>(100);
    baseHolder.process(items::add);
    try {
      return NamedThreadFactory.executeWithNewThreadName("Sorting " + items.size() + " items", () -> {
        items.sort(comparator);
        return getItemHolder(items);
      });
    } catch (Exception e) {
      ExceptionUtil.rethrowAsRuntimeException(e);
      return null;
    }
  }

  @NotNull
  public TreeSet<STestRun> createContainerSet() {
    return new TreeSet<>((o1, o2) -> ComparisonChain.start()
        .compare(o1.getBuildId(), o2.getBuildId())
        .compare(o1.getTestRunId(), o2.getTestRunId())
        .result());
  }

  /**
   * Filters out personal builds by default. Uses dimension {@link #PERSONAL_FOR_USER} to filter out personal builds of other users if needed.
   */
  @NotNull
  private Filter<STestRun> getPersonalBuildsFilter(@NotNull final Locator locator) {
    boolean includePersonal = locator.getSingleDimensionValueAsStrictBoolean(INCLUDE_PERSONAL, false);
    boolean includeAllPersonal = locator.getSingleDimensionValueAsStrictBoolean(INCLUDE_ALL_PERSONAL, false);

    boolean keepMy = includePersonal || includeAllPersonal;
    boolean keepAll = includeAllPersonal;

    String userIdStr = locator.getSingleDimensionValue(PERSONAL_FOR_USER);

    if(keepAll) {
      return testRun -> true;
    }

    if (keepMy && userIdStr != null) {
      Long user = Long.parseLong(userIdStr);
      // Personal test run always has an owner
      return testRun -> !testRun.getBuild().isPersonal() || user.equals(testRun.getBuild().getOwner().getId());
    }

    return testRun -> !testRun.getBuild().isPersonal();
  }

  @NotNull
  private Filter<STestRun> getBranchFilter(@Nullable final String branchLocator) {
    Filter<STestRun> defaultFilter = tr -> true;

    if (branchLocator == null) {
      return defaultFilter;
    }
    BranchFinder.BranchFilterDetails branchFilterDetails;
    try {
      branchFilterDetails = myBranchFinder.getBranchFilterDetails(branchLocator);
    } catch (LocatorProcessException e) {
      // unparsable locator - support previous behavior with simple equals matching, just match in case-insensitive way
      return testRun -> {
        SBuild build = testRun.getBuild();
        Branch branch = build.getBuildPromotion().getBranch();
        return branch != null && branchLocator.equalsIgnoreCase(branch.getName());
      };
    }
    if (branchFilterDetails.isAnyBranch()) {
      return defaultFilter;
    }
    return testRun -> {
      SBuild build = testRun.getBuild();
      return branchFilterDetails.isIncluded(build.getBuildPromotion());
    };
  }

  @NotNull
  private STestRun processInvocationExpansion(@NotNull final STestRun item, @Nullable final Boolean expandInvocations) {
    if (expandInvocations == null || !expandInvocations) {
      return item;
    }
    return getInvocations(item).iterator().next();
  }

  @NotNull
  private Collection<STestRun> getInvocations(@NotNull final STestRun item) {
    if (!(item instanceof MultiTestRun)) return Collections.singletonList(item);
    MultiTestRun compositeRun = (MultiTestRun)item;
    return compositeRun.getTestRuns();
  }

  @NotNull
  private ItemHolder<STestRun> getPrefilteredItemsInternal(@NotNull final Locator locator) {
    String buildDimension = locator.getSingleDimensionValue(BUILD);
    if (buildDimension != null) {
      // Always include test runs from personal builds when there is a build locator.
      locator.setDimension(INCLUDE_ALL_PERSONAL, Locator.BOOLEAN_TRUE);

      List<BuildPromotion> builds = myBuildFinder.getBuilds(null, buildDimension).myEntries;

      Boolean expandInvocations = locator.getSingleDimensionValueAsBoolean(EXPAND_INVOCATIONS);  //getting the dimension early in order not to get "dimension is unknown" for it in case of early exit
      String testDimension = locator.getSingleDimensionValue(TEST);
      if (testDimension == null) {
        AggregatingItemHolder<STestRun> result = new AggregatingItemHolder<>();
        for (BuildPromotion build : builds) {
          SBuild associatedBuild = build.getAssociatedBuild();
          if (associatedBuild != null) {
            result.add(getPossibleExpandedTestsHolder(getBuildStatistics(associatedBuild, locator).getAllTests(), expandInvocations));
          }
        }
        return result;
      }

      final PagedSearchResult<STest> tests = myTestFinder.getItems(testDimension);
      final ArrayList<STestRun> result = new ArrayList<>();
      for (BuildPromotion build : builds) {
        SBuild associatedBuild = build.getAssociatedBuild();
        if (associatedBuild != null) {
          Set<Long> allTestNameIds = tests.myEntries.stream().map(STest::getTestNameId).collect(Collectors.toSet());
          final List<STestRun> allTests = getBuildStatistics(associatedBuild, locator).getAllTests();
          for (STestRun item : allTests) {
            if (allTestNameIds.contains(item.getTest().getTestNameId())) {
              result.add(item);
            }
          }
        }
      }
      return getPossibleExpandedTestsHolder(result, expandInvocations);
    }

    // Do not return tets runs form all personal builds.
    // Will still include test runs from personal for specific user if requested.
    if(!locator.isSingleValue()) {
      locator.setDimension(INCLUDE_ALL_PERSONAL, Locator.BOOLEAN_FALSE);
    }

    String testDimension = locator.getSingleDimensionValue(TEST);
    if (testDimension != null) {
      final PagedSearchResult<STest> tests = myTestFinder.getItems(testDimension);

      String buildTypeDimension = locator.getSingleDimensionValue(BUILD_TYPE);
      if (buildTypeDimension != null) {
        final SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeDimension, false);
        final ArrayList<STestRun> result = new ArrayList<>();
        for (STest test : tests.myEntries) {
          result.addAll(getTestHistory(test, buildType, locator));
        }
        return getPossibleExpandedTestsHolder(result, locator.getSingleDimensionValueAsBoolean(EXPAND_INVOCATIONS));
      }

      final ArrayList<STestRun> result = new ArrayList<>();
      final SProject affectedProject = getAffectedProject(locator);
      for (STest test : tests.myEntries) {
        result.addAll(getTestHistory(test, affectedProject, locator));
      }
      return getPossibleExpandedTestsHolder(result, locator.getSingleDimensionValueAsBoolean(EXPAND_INVOCATIONS));
    }

    Boolean currentDimension = locator.lookupSingleDimensionValueAsBoolean(CURRENT);
    if (currentDimension != null && currentDimension) {
      locator.markUsed(Collections.singleton(CURRENT));
      return getPossibleExpandedTestsHolder(getCurrentOccurrences(getAffectedProject(locator), myCurrentProblemsManager),
          locator.getSingleDimensionValueAsBoolean(EXPAND_INVOCATIONS));
    }

    Boolean currentlyMutedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_MUTED);
    if (currentlyMutedDimension != null && currentlyMutedDimension) {
      final SProject affectedProject = getAffectedProject(locator);
      final Set<STest> currentlyMutedTests = myTestFinder.getCurrentlyMutedTests(affectedProject);
      final ArrayList<STestRun> result = new ArrayList<>();
      for (STest test : currentlyMutedTests) {
        result.addAll(getTestHistory(test, affectedProject, locator));
      }
      return getPossibleExpandedTestsHolder(result, locator.getSingleDimensionValueAsBoolean(EXPAND_INVOCATIONS));
    }

    ArrayList<String> exampleLocators = new ArrayList<>();
    exampleLocators.add(Locator.getStringLocator(DIMENSION_ID, "XXX"));
    exampleLocators.add(Locator.getStringLocator(BUILD, "XXX"));
    exampleLocators.add(Locator.getStringLocator(TEST, "XXX"));
    exampleLocators.add(Locator.getStringLocator(CURRENT, "true", AFFECTED_PROJECT, "XXX"));
    exampleLocators.add(Locator.getStringLocator(CURRENTLY_MUTED, "true", AFFECTED_PROJECT, "XXX"));
    throw new BadRequestException(
        "Unsupported test occurrence locator '" + locator.getStringRepresentation() + "'. Try one of locator dimensions: " + DataProvider.dumpQuoted(exampleLocators));
  }

  @NotNull
  private SProject getAffectedProject(@NotNull final Locator locator) {
    String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null) {
      return myProjectFinder.getItem(affectedProjectDimension);
    } else {
      return myProjectFinder.getRootProject();
    }
  }

  @NotNull
  private List<STestRun> getTestHistory(final STest test, final SProject affectedProject, @NotNull final Locator locator) {
    return MultiTestRun.mergeByTestName(myTestHistory.getTestHistory(test.getTestNameId(), affectedProject, getBranchFilter(locator.getSingleDimensionValue(BRANCH))));
    //consider reporting not found if no tests found and the branch does not exist
  }

  @NotNull
  private List<STestRun> getTestHistory(final STest test, final SBuildType buildType, @NotNull final Locator locator) {
    return MultiTestRun.mergeByTestName(myTestHistory.getTestHistory(test.getTestNameId(), buildType.getBuildTypeId(), getBranchFilter(locator.getSingleDimensionValue(BRANCH))));
    //consider reporting not found if no tests found and the branch does not exist
  }

  @NotNull
  private ItemHolder<STestRun> getPossibleExpandedTestsHolder(@NotNull final Iterable<STestRun> tests, @Nullable final Boolean expandInvocations) {
    if (expandInvocations == null || !expandInvocations) {
      return getItemHolder(tests);
    }
    return processor -> {
      for (STestRun entry : tests) {
        if (!(entry instanceof MultiTestRun)) {
          processor.processItem(entry);
        } else {
          for (STestRun nestedTestRun : getInvocations(entry)) {
            processor.processItem(nestedTestRun);
          }
        }
      }
    };
  }

  @NotNull
  @Override
  public ItemFilter<STestRun> getFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<STestRun> result = new MultiCheckerFilter<>();

    if (locator.isUnused(DIMENSION_ID)){
      Long testRunId = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
      // If someone voluntarily excluded personal build when searching by test run id, then assume that is exactly what they want.
      // Otherwise, return a test run in a personal build by default.
      locator.setDimensionIfNotPresent(INCLUDE_PERSONAL, "true");
      if (testRunId != null) {
        result.add(item -> testRunId.intValue() == item.getTestRunId());
      }
    }

    Status status = Util.resolveNull(locator.getSingleDimensionValue(STATUS), TestOccurrence::getStatusFromPosted);
    if (status != null) {
      result.add(item -> status.equals(item.getStatus()));
    }

    final Boolean ignoredDimension = locator.getSingleDimensionValueAsBoolean(IGNORED);
    if (ignoredDimension != null) {
      result.add(item -> FilterUtil.isIncludedByBooleanFilter(ignoredDimension, item.isIgnored()));
    }

    final String nameDimension = locator.getSingleDimensionValue(NAME);
    if (nameDimension != null) {
      ValueCondition nameCondition = ParameterCondition.createValueConditionFromPlainValueOrCondition(nameDimension);
      result.add(item -> nameCondition.matches(item.getTest().getName().getAsString()));
    }

    if (locator.getUnusedDimensions().contains(TEST)) {
      String testDimension = locator.getSingleDimensionValue(TEST);
      if (testDimension != null) {
        final PagedSearchResult<STest> tests = myTestFinder.getItems(testDimension);
        final HashSet<Long> testNameIds = new HashSet<>();
        for (STest test : tests.myEntries) {
          testNameIds.add(test.getTestNameId());
        }
        result.add(item -> testNameIds.contains(item.getTest().getTestNameId()));
      }
    }

    if (locator.isUnused(BUILD)) {
      String buildDimension = locator.getSingleDimensionValue(BUILD);
      if (buildDimension != null) {
        // Include test runs from personal build if user is looking for one specific build.
        boolean searchByBuildId = new Locator(buildDimension).isAnyPresent(BuildFinder.DIMENSION_ID);
        locator.setDimensionIfNotPresent(INCLUDE_PERSONAL, Boolean.toString(searchByBuildId));

        List<BuildPromotion> builds = myBuildFinder.getBuilds(null, buildDimension).myEntries; //todo: use buildPromotionFinder, use filter; drop personal builds filtering in test history
        result.add(item -> builds.contains(item.getBuild().getBuildPromotion()));
      }
    }

    if (locator.isUnused(BRANCH)) {
      String branchDimension = locator.getSingleDimensionValue(BRANCH);
      if (branchDimension != null) {
        Filter<STestRun> branchFilter = getBranchFilter(branchDimension);
        result.add(branchFilter::accept);
      }
    }

    if (locator.isUnused(BUILD_TYPE)) {
      String buildTypeDimension = locator.getSingleDimensionValue(BUILD_TYPE);
      if (buildTypeDimension != null) {
        final SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeDimension, false);
        result.add(item -> item.getBuild().getBuildTypeId().equals(buildType.getInternalId()));
      }
    }

    final Boolean currentlyInvestigatedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_INVESTIGATED);
    if (currentlyInvestigatedDimension != null) {
      result.add(item -> {
        //todo: check investigation in affected Project/buildType only, if set
        return FilterUtil.isIncludedByBooleanFilter(currentlyInvestigatedDimension, isCurrentlyInvestigated(item));
      });
    }

    final String investigationState = locator.getSingleDimensionValue(INVESTIGATION_STATE);
    if(investigationState != null) {
      result.add(hasInvestigationStateFilter(investigationState));
    }

    final Boolean currentlyMutedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_MUTED);
    if (currentlyMutedDimension != null) { //it is important to filter even if prefiltered items processed the tests as that does not consider mute scope
      result.add(item -> { //todo: TeamCity API (MP): is there an API way to figure out there is a mute for a STestRun ?
        //todo: check mute in affected Project/buildType only, if set
        return FilterUtil.isIncludedByBooleanFilter(currentlyMutedDimension, isCurrentlyMuted(item));
      });
    }

    final Boolean newFailure = locator.getSingleDimensionValueAsBoolean(NEW_FAILURE);
    if (newFailure != null) {
      //when newFailure is specified, do not match non-failed tests. This matches the logic of newFailure attribute presence in TestOccurrence
      result.add(item -> item.getStatus().isFailed() && FilterUtil.isIncludedByBooleanFilter(newFailure, item.isNewFailure()));
    }

    final Boolean muteDimension = locator.getSingleDimensionValueAsBoolean(MUTED);
    if (muteDimension != null) {
      result.add(item -> FilterUtil.isIncludedByBooleanFilter(muteDimension, item.isMuted()));
    }

    if (locator.isUnused(CURRENT)) {
      final Boolean currentDimension = locator.getSingleDimensionValueAsBoolean(CURRENT);
      if (currentDimension != null) {
        //todo: is this the same as the test occurring in current problems???
        result.add(item -> FilterUtil.isIncludedByBooleanFilter(currentDimension , !item.isFixed()));
      }
    }

    final String scopeDimension = locator.getSingleDimensionValue(SCOPE);
    if(scopeDimension != null) {
      final TestScopeFilter filter = myTestScopeFilterProducer.createFromLocatorString(scopeDimension);
      result.add(item -> filter.test(item));
    }

    if (locator.getUnusedDimensions().contains(INVOCATIONS)) {
      final String dimensionValue = locator.getSingleDimensionValue(INVOCATIONS);
      if (dimensionValue != null) {
        result.add(item -> {
          Collection<STestRun> testRuns = getInvocations(item);
          FinderSearchMatcher<STestRun> matcher =
              new FinderSearchMatcher<>(dimensionValue, new DelegatingAbstractFinder<STestRun>(TestOccurrenceFinder.this) {
                @Nullable
                @Override
                public STestRun findSingleItem(@NotNull final Locator locator1) {
                  return null;
                }

                @NotNull
                @Override
                public ItemHolder<STestRun> getPrefilteredItems(@NotNull final Locator locator1) {
                  return getItemHolder(testRuns);
                }
              });
          return matcher.matches(null);
        });
      }
    }

    // Exclude test runs form personal builds by default to , if not included by a special cases above.
    Filter<STestRun> personalBuildsFilter = getPersonalBuildsFilter(locator);
    result.add(personalBuildsFilter::accept);

    return result;
  }

  public boolean isCurrentlyMuted(@NotNull final STestRun item) {  //todo: TeamCity API (MP): is there an API way to figure out there is an investigation for a STestRun ?
    final CurrentMuteInfo currentMuteInfo = item.getTest().getCurrentMuteInfo();
    if (currentMuteInfo == null){
      return false;
    }
    final SBuildType buildType = item.getBuild().getBuildType();
    if (buildType == null){
      return false; //might need to log this
    }

    if (currentMuteInfo.getBuildTypeMuteInfo().containsKey(buildType)) return true;

    final Set<SProject> projects = currentMuteInfo.getProjectsMuteInfo().keySet();
    return ProjectFinder.isSameOrParent(projects, buildType.getProject());
  }

  /**
   * Checks whether response can be built only using ShortStatistics without fetching test occurrences given locator and fields. If so, get this statistics.
   * In addition, check if test runs in a returned statistics require filtering.
   * @return ShortStatistics with a post filtering flag if there is enough data to produce the response, TestOccurrencesCachedInfo.empty() otherwise.
   */
  @NotNull
  public TestOccurrencesCachedInfo tryGetCachedInfo(@Nullable final String locatorText, @Nullable final String fieldsText) {
    if(locatorText == null || fieldsText == null)
      return TestOccurrencesCachedInfo.empty();

    Locator locator = Locator.locator(locatorText);
    Fields fields = new Fields(fieldsText);
    boolean postFilteringRequired = false;

    boolean needsActualOccurrence = fields.isIncluded("testOccurrence", false, false);
    if(needsActualOccurrence) {
      Status status = Util.resolveNull(locator.lookupSingleDimensionValue(STATUS), TestOccurrence::getStatusFromPosted);
      if(status == null || status != Status.FAILURE) {
        return TestOccurrencesCachedInfo.empty();
      }

      Fields occurrenceFields = fields.getNestedField("testOccurrence");
      FieldInclusionChecker checker = FieldInclusionChecker.getForClass(TestOccurrence.class);
      if(!FASTPATH_ALLOWED_FIELDS.containsAll(checker.getAllPotentiallyIncludedFields(occurrenceFields))) {
        return TestOccurrencesCachedInfo.empty();
      }

      // let's just do it always if we need items
      postFilteringRequired = true;
    }

    String buildDimension = locator.getSingleDimensionValue(BUILD);
    if(buildDimension == null)
      return TestOccurrencesCachedInfo.empty();

    if(locator.getSingleDimensionValueAsStrictBoolean(EXPAND_INVOCATIONS, false)) {
      // Expand invocations requires additional logic
      return TestOccurrencesCachedInfo.empty();
    }

    List<BuildPromotion> buildPromotions = myBuildFinder.getBuilds(null, buildDimension).myEntries;
    if(buildPromotions.size() != 1) {
      // If there is not a single build to the criteria,
      return TestOccurrencesCachedInfo.empty();
    }

    SBuild build = buildPromotions.get(0).getAssociatedBuild();
    if(build == null)
      return TestOccurrencesCachedInfo.empty();

    // let's not construct a filter if we already know that we want to filter anyways
    if(!postFilteringRequired) {
      // If any kind of filter is defined then post filtering is necessary
      MultiCheckerFilter<STestRun> filter = (MultiCheckerFilter<STestRun>)getFilter(locator);
      // Personal builds filter is always there
      postFilteringRequired = filter.getSubFiltersCount() > 1;
    }

    return new TestOccurrencesCachedInfo(build.getShortStatistics(), postFilteringRequired);
  }

  @NotNull
  public static BuildStatistics getBuildStatistics(@NotNull final SBuild build, @Nullable final Locator locator) {
    //  This is different from build.getFullStatistics() in the following ways:
    //  - stacktrace are not pre-loaded (loads all them into memory), but will be retrieved in a lazy fashion
    //  - compilation errors are not loaded (not necessary)

    //ideally, need to check what will be used in the response and request only those details
    int optionsMask = TeamCityProperties.getInteger("rest.request.testOccurrences.buildStatOpts.default",
        0);
    boolean loadAllTests = TeamCityProperties.getBoolean("rest.request.testOccurrences.loadAllTestsForBuild");
    if (locator == null || loadAllTests || FilterUtil.isIncludingBooleanFilter(locator.lookupSingleDimensionValueAsBoolean(IGNORED))) { //todo: consider not loading ignored tests if only failed are requested via status:FAILURE (if that does not change the result)
      optionsMask |= BuildStatisticsOptions.IGNORED_TESTS;
    }

    if (locator == null || loadAllTests || Util.resolveNull(locator.lookupSingleDimensionValue(STATUS), TestOccurrence::getStatusFromPosted) != Status.FAILURE) {
      optionsMask |= BuildStatisticsOptions.PASSED_TESTS;
    }

    return build.getBuildStatistics(
        new BuildStatisticsOptions(optionsMask, TeamCityProperties.getInteger("rest.request.testOccurrences.buildStatOpts.maxNumberOfTestsStacktracesToLoad", 0)));
  }

  @Nullable
  private STestRun findTestByTestRunId(@NotNull final Long testRunId, @NotNull final SBuild build) {
    //todo: TeamCity API (MP) how to implement this without build?
    //todo: TeamCity API: if stacktraces are not loaded,should I then load them somehow to get them for the returned STestRun (see TestOccurrence)
    return build.getBuildStatistics(ALL_TESTS_NO_DETAILS).findTestByTestRunId(testRunId);
  }

  @NotNull
  private FilterConditionChecker<STestRun> hasInvestigationStateFilter(@NotNull String investigationState) {
    switch (investigationState) {
      case "active":
        return item -> hasInvestigationState(item, ResponsibilityEntry.State.TAKEN);
      case "givenUp":
        return item -> hasInvestigationState(item, ResponsibilityEntry.State.GIVEN_UP);
      case "fixed":
        return item -> hasInvestigationState(item, ResponsibilityEntry.State.FIXED);
      case "none":
        return item -> hasInvestigationState(item, ResponsibilityEntry.State.NONE);
    }
    throw new LocatorProcessException("Invalid value of dimension " + INVESTIGATION_STATE + ".");
  }

  private boolean hasInvestigationState(@NotNull final STestRun item, @NotNull ResponsibilityEntry.State state) {
    //todo: TeamCity API (MP): is there an API way to figure out there is an investigation for a STestRun ?
    final List<TestNameResponsibilityEntry> testResponsibilities = item.getTest().getAllResponsibilities();

    if(state.equals(ResponsibilityEntry.State.NONE)) {
      for (TestNameResponsibilityEntry testResponsibility : testResponsibilities) {
        final SBuildType buildType = item.getBuild().getBuildType();
        if (buildType != null) {
          if (ProjectFinder.isSameOrParent(testResponsibility.getProject(), buildType.getProject())) {
            return false;
          }
        }
      }

      return true;
    }

    for (TestNameResponsibilityEntry testResponsibility : testResponsibilities) {
      final SBuildType buildType = item.getBuild().getBuildType();
      if (buildType != null) {
        if (testResponsibility.getState().equals(state) && ProjectFinder.isSameOrParent(testResponsibility.getProject(), buildType.getProject())) {
          return true;
        }
      }
    }

    return false;
  }

  public boolean isCurrentlyInvestigated(@NotNull final STestRun item) {
    return hasInvestigationState(item, ResponsibilityEntry.State.TAKEN);
  }

  @Nullable
  private static Comparator<STestRun> getComparator(@NotNull final Locator locator) {
    String orderBy = locator.getSingleDimensionValue(ORDER);
    if (orderBy == null) return null;
    return SUPPORTED_ORDERS.getComparator(orderBy);
  }

  private static class DelegatingAbstractFinder<T> extends AbstractFinder<T> {
    private final AbstractFinder<T> myDelegate;

    public DelegatingAbstractFinder(final AbstractFinder<T> delegate) {
      myDelegate = delegate;
    }

    @NotNull
    @Override
    public String[] getKnownDimensions() {
      return myDelegate.getKnownDimensions();
    }

    @NotNull
    @Override
    public String[] getHiddenDimensions() {
      return myDelegate.getHiddenDimensions();
    }

    @Nullable
    @Override
    public Locator.DescriptionProvider getLocatorDescriptionProvider() {
      return myDelegate.getLocatorDescriptionProvider();
    }

    @Nullable
    @Override
    public T findSingleItem(@NotNull final Locator locator) {
      return myDelegate.findSingleItem(locator);
    }

    @NotNull
    @Override
    public ItemHolder<T> getPrefilteredItems(@NotNull final Locator locator) {
      return myDelegate.getPrefilteredItems(locator);
    }

    @NotNull
    @Override
    public ItemFilter<T> getFilter(@NotNull final Locator locator) {
      return myDelegate.getFilter(locator);
    }

    @NotNull
    @Override
    public String getItemLocator(@NotNull final T item) {
      return myDelegate.getItemLocator(item);
    }

    @Nullable
    @Override
    public Set<T> createContainerSet() {
      return myDelegate.createContainerSet();
    }
  }

  private static final Orders<STestRun> SUPPORTED_ORDERS = new Orders<STestRun>() //see TestOccurrence for names
      .add("name", Comparator.comparing(tr -> tr.getTest().getName().getAsString(), String.CASE_INSENSITIVE_ORDER))
      .add("duration", Comparator.comparingInt(STestRun::getDuration))
      .add("runOrder", Comparator.comparingInt(STestRun::getOrderId))
      .add("status", Comparator.comparing(tr -> tr.getStatus().getPriority()))
      .add("newFailure", Comparator.comparing(STestRun::isNewFailure)) //even more experimental than entire sorting feature
      .add("buildStartDate", Comparator.comparing(tr -> tr.getBuild().getStartDate()));
}
