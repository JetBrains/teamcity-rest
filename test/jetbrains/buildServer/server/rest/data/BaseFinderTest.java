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

package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.util.Pair;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.artifacts.RevisionRules;
import jetbrains.buildServer.log.Loggable;
import jetbrains.buildServer.responsibility.ResponsibilityFacadeEx;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationFinder;
import jetbrains.buildServer.server.rest.data.mutes.MuteFinder;
import jetbrains.buildServer.server.rest.data.problem.ProblemFinder;
import jetbrains.buildServer.server.rest.data.problem.ProblemOccurrenceFinder;
import jetbrains.buildServer.server.rest.data.problem.TestFinder;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.data.problem.scope.TestScopeFilterProducer;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.ArtifactDependencyFactory;
import jetbrains.buildServer.serverSide.CurrentProblemsManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.TestName2Index;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusProvider;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusReportLocator;
import jetbrains.buildServer.serverSide.identifiers.VcsRootIdentifiersManagerImpl;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.mute.LowLevelProblemMutingService;
import jetbrains.buildServer.serverSide.mute.LowLevelProblemMutingServiceImpl;
import jetbrains.buildServer.serverSide.mute.ProblemMutingService;
import jetbrains.buildServer.serverSide.problems.BuildProblemManager;
import jetbrains.buildServer.serverSide.versionedSettings.VersionedSettingsManager;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.vcs.impl.VcsManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;

/**
 * Created by yaegor on 13/06/2015.
 */
public abstract class BaseFinderTest<T> extends BaseServerTestCase{
  protected static final String ARTIFACT_DEP_FILE_NAME = "aaa";
  private Finder<T> myFinder;
  protected VcsManagerImpl myVcsManager;
  protected PermissionChecker myPermissionChecker;

  protected ProjectFinder myProjectFinder;
  protected AgentFinder myAgentFinder;
  protected BuildTypeFinder myBuildTypeFinder;
  protected VcsRootFinder myVcsRootFinder;
  protected VcsRootInstanceFinder myVcsRootInstanceFinder;
  protected UserFinder myUserFinder;
  protected TestFinder myTestFinder;
  protected BuildPromotionFinder myBuildPromotionFinder;
  protected BuildFinder myBuildFinder;
  protected ProblemFinder myProblemFinder;
  protected ProblemOccurrenceFinder myProblemOccurrenceFinder;
  protected TestOccurrenceFinder myTestOccurrenceFinder;
  protected InvestigationFinder myInvestigationFinder;
  protected MuteFinder myMuteFinder;
  protected AgentPoolFinder myAgentPoolFinder;
  protected QueuedBuildFinder myQueuedBuildFinder;
  protected BranchFinder myBranchFinder;
  protected ChangeFinder myChangeFinder;
  protected UserGroupFinder myGroupFinder;
  protected TimeCondition myTimeCondition;

  static public BeanContext getBeanContext(final ServiceLocator serviceLocator) {
    final ApiUrlBuilder apiUrlBuilder = new ApiUrlBuilder(path -> path);
    final BeanFactory beanFactory = new BeanFactory(null);

    return new BeanContext(beanFactory, serviceLocator, apiUrlBuilder);
  }

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    initFinders();
  }

  protected void initFinders() {
    myVcsManager = myFixture.getVcsManager();
    myFixture.addService(myVcsManager);
    myFixture.addService(myProjectManager);
    myPermissionChecker = new PermissionChecker(myServer.getSecurityContext(), myProjectManager);
    myFixture.addService(myPermissionChecker);

    myTimeCondition = new TimeCondition(myFixture);
    myFixture.addService(myTimeCondition);

    myProjectFinder = new ProjectFinder(myProjectManager, myPermissionChecker, myServer);
    myFixture.addService(myProjectFinder);

    myAgentFinder = new AgentFinder(myAgentManager, myFixture);
    myFixture.addService(myAgentFinder);

    myAgentPoolFinder = new AgentPoolFinder(myFixture.getAgentPoolManager(), myAgentFinder, myFixture);
    myFixture.addService(myAgentPoolFinder);

    myBuildTypeFinder = new BuildTypeFinder(myProjectManager, myProjectFinder, myAgentFinder, myPermissionChecker, myServer);
    myFixture.addService(myBuildTypeFinder);

    final VcsRootIdentifiersManagerImpl vcsRootIdentifiersManager = myFixture.getSingletonService(VcsRootIdentifiersManagerImpl.class);

    myVcsRootFinder = new VcsRootFinder(myVcsManager, myProjectFinder, myBuildTypeFinder, myProjectManager,
                                        vcsRootIdentifiersManager,
                                        myPermissionChecker);
    myFixture.addService(myVcsRootFinder);

    myVcsRootInstanceFinder = new VcsRootInstanceFinder(myVcsRootFinder, myVcsManager, myProjectFinder, myBuildTypeFinder, myProjectManager,
                                                        myFixture.getSingletonService(VersionedSettingsManager.class),
                                                        myTimeCondition, myPermissionChecker, myServer);
    myFixture.addService(myVcsRootInstanceFinder);

    myGroupFinder = new UserGroupFinder(getUserGroupManager());
    myFixture.addService(myGroupFinder);
    myUserFinder = new UserFinder(getUserModelEx(), myGroupFinder, myProjectFinder, myTimeCondition,
                                  myFixture.getRolesManager(), myPermissionChecker, myServer.getSecurityContext(), myServer);
    myFixture.addService(myUserFinder);

    myBranchFinder = new BranchFinder(myBuildTypeFinder, myFixture);

    myBuildPromotionFinder = new BuildPromotionFinder(myFixture.getBuildPromotionManager(), myFixture.getBuildQueue(), myServer, myVcsRootFinder, myProjectFinder,
                                                      myBuildTypeFinder, myUserFinder, myAgentFinder, myBranchFinder, myTimeCondition, myPermissionChecker, null, myFixture);
    myFixture.addService(myBuildPromotionFinder);

    myBuildFinder = new BuildFinder(myServer, myBuildTypeFinder, myProjectFinder, myUserFinder, myBuildPromotionFinder, myAgentFinder);
    myFixture.addService(myBuildFinder);

    final TestName2Index testName2Index = myFixture.getSingletonService(TestName2Index.class);
    final ProblemMutingService problemMutingService = myFixture.getSingletonService(ProblemMutingService.class);
    myTestFinder = new TestFinder(myProjectFinder, myBuildTypeFinder, myBuildPromotionFinder,
                                  myFixture.getTestManager(), testName2Index, myFixture.getCurrentProblemsManager(), problemMutingService);
    myFixture.addService(myTestFinder);

    TestScopeFilterProducer filterProducer = new TestScopeFilterProducer(myBuildTypeFinder);
    final CurrentProblemsManager currentProblemsManager = myServer.getSingletonService(CurrentProblemsManager.class);
    myTestOccurrenceFinder = new TestOccurrenceFinder(myTestFinder, myBuildFinder, myBuildTypeFinder, myProjectFinder, myFixture.getTestsHistory(), currentProblemsManager, myBranchFinder, filterProducer);
    myFixture.addService(myTestOccurrenceFinder);

    final BuildProblemManager buildProblemManager = myFixture.getSingletonService(BuildProblemManager.class);
    myProblemFinder = new ProblemFinder(myProjectFinder, myBuildPromotionFinder, buildProblemManager, myProjectManager, myFixture, problemMutingService);
    myFixture.addService(myProblemFinder);
    myProblemOccurrenceFinder = new ProblemOccurrenceFinder(myProjectFinder, myBuildFinder, myProblemFinder, buildProblemManager, myProjectManager, myFixture);
    myFixture.addService(myProblemOccurrenceFinder);

    final ResponsibilityFacadeEx responsibilityFacade = myFixture.getResponsibilityFacadeEx();
    myInvestigationFinder = new InvestigationFinder(myProjectFinder, myBuildTypeFinder, myProblemFinder, myTestFinder, myUserFinder,
                                                    responsibilityFacade, responsibilityFacade, responsibilityFacade);
    myFixture.addService(myInvestigationFinder);

    myMuteFinder = new MuteFinder(myProjectFinder, myTimeCondition, myPermissionChecker, problemMutingService,
                                  (LowLevelProblemMutingServiceImpl)myFixture.getSingletonService(LowLevelProblemMutingService.class), myFixture);
    myFixture.addService(myMuteFinder);

    myQueuedBuildFinder =
      new QueuedBuildFinder(myServer.getQueue(), myProjectFinder, myBuildTypeFinder, myUserFinder, myAgentFinder, myAgentPoolFinder, myFixture.getBuildPromotionManager(), myServer);
    myFixture.addService(myQueuedBuildFinder);

    myChangeFinder = new ChangeFinder(myProjectFinder, myBuildFinder, myBuildPromotionFinder, myBuildTypeFinder, myVcsRootFinder, myVcsRootInstanceFinder, myUserFinder,
                                      myVcsManager, myFixture.getVcsHistory(), myBranchFinder, myFixture, myPermissionChecker);
    myFixture.addService(myChangeFinder);
    myFixture.addService(new HealthItemFinder(myFixture.getSingletonService(HealthStatusProvider.class), myFixture.getSingletonService(HealthStatusReportLocator.class), myFixture));
  }

  public void setFinder(@NotNull Finder<T> finder){
    myFinder = finder;
  }

  public Finder<T> getFinder() {
    return myFinder;
  }

  public void check(@Nullable final String locator, T... items) {
    check(locator, getEqualsMatcher(), myFinder, items);
  }

  @NotNull
  public<S> Matcher<S, S> getEqualsMatcher() {
    return Object::equals;
  }

  public void checkCounts(@Nullable final String locator, int resultsCount, int maxProcessedCounts) {
    PagedSearchResult<T> result = getFinder().getItems(locator);
    assertEquals("Wrong number of found items", resultsCount, result.myActualCount);
    if (result.myActuallyProcessedCount != null && result.myActuallyProcessedCount > maxProcessedCounts) {
      fail("Wrong number of processed items: " + result.myActuallyProcessedCount + ", while not more than " + maxProcessedCounts + " is expected");
    }
  }

  public <S> void check(@Nullable final String locator, @NotNull Matcher<S, T> matcher, S... items) {
    check(locator, matcher, getFinder(), items);
  }

  private <S> void check_helper(@Nullable final String locator, @NotNull Matcher<S, T> matcher, S[] items) {
    check(locator, matcher, getFinder(), items);
  }

  public <S, R> void check(@Nullable final String locator, @NotNull Matcher<S, R> matcher, @NotNull final Finder<R> finder, S... items) {
    check(locator, matcher, BaseFinderTest::getDescription, BaseFinderTest::getDescription, finder, items);
  }

  public <S, R> void check(@Nullable final String locator, @NotNull Matcher<S, R> matcher,
                           @NotNull DescriptionProvider <R> loggerActual, @NotNull DescriptionProvider <S> loggerExpected, @NotNull final Finder<R> finder, S... items) {
    check(locator, loggerActual, loggerExpected, finder, getDefaultMatchStrategy(locator, matcher, items), items);
  }

  public <S, R> void check(@Nullable final String locator, @NotNull DescriptionProvider<R> loggerActual, @NotNull DescriptionProvider<S> loggerExpected,
                           @NotNull final Finder<R> finder, @NotNull final CollectionsMatchStrategy<S, R> strategy, S... items) {
    final List<R> result = finder.getItems(locator).myEntries;
    final String expected = getDescription(Arrays.asList(items), loggerExpected);
    final String actual = getDescription(result, loggerActual);
    assertEquals("For itemS locator \"" + locator + "\"\n" +
                 "Expected:\n" + expected + "\n\n" +
                 "Actual:\n" + actual, items.length, result.size());

    strategy.matchCollection(items, result);

    //check single item retrieve
    if (locator != null) {
      strategy.matchSingle(items, () -> finder.getItem(locator));
    }
  }

  class Checker<S, I> {
    @NotNull private final Function<String, String> myLocatorCreator;
    @NotNull private final Function<I, S> myMapper;
    @NotNull private final Matcher<S, T> myMatcher;

    Checker(@NotNull final Function<String, String> locatorCreator, @NotNull final Function<I, S> mapper, @NotNull Matcher<S, T> matcher) {
      myLocatorCreator = locatorCreator;
      myMapper = mapper;
      myMatcher = matcher;
    }
    public void check(@Nullable final String locatorPart, I... items) {
      check_helper(myLocatorCreator.apply(locatorPart), myMatcher, (S[])Arrays.stream(items).map(myMapper).toArray());
    }
  }

  protected SArtifactDependency addArtifactDependency(final SBuildType dependent, final SBuildType dependOn) {
    SArtifactDependency dep = myFixture.getSingletonService(ArtifactDependencyFactory.class).createArtifactDependency(dependOn, ARTIFACT_DEP_FILE_NAME, RevisionRules.LAST_FINISHED_RULE);
    dependent.addArtifactDependency(dep);
    return dep;
  }

  public interface Matcher<S, T> {
    boolean matches(@NotNull S s, @NotNull T t);
  }

  public interface DescriptionProvider<S> {
    String describe(@NotNull S s);
  }

  @NotNull
  protected <S, R> OrderedMatcherStrategy<S, R> getDefaultMatchStrategy(@Nullable final String locator,
                                                                        @NotNull final Matcher<S, R> equalsMatcher,
                                                                        @NotNull final S[] items) {
    return new OrderedMatcherStrategy<>(equalsMatcher, p -> "Wrong item found for locator \"" + locator + "\" at position " + (p.getSecond() + 1) + "/" + items.length + "\n" +
                                                            "Expected:\n" + Arrays.toString(items) + "\n" +
                                                            "\nActual:\n" + p.first
      , p -> {
      if (p.first == null) {
        return "No items should be found by locator \"" + locator + "\", but found: " + ((DescriptionProvider<R>)BaseFinderTest::getDescription).describe(p.second);
      } else {
        return "While searching for single item with locator \"" + locator + "\"\n" +
               "Expected: " + BaseFinderTest.getDescription(p.first) + "\n" +
               "Actual: " + BaseFinderTest.getDescription(p.second);
      }
    });
  }

  protected static <U> String getDescription(final U singleResult) {
    if (singleResult instanceof Loggable){
      return LogUtil.describeInDetail(((Loggable)singleResult));
    }
    return LogUtil.describe(singleResult);
  }

  public <E extends Throwable> void checkExceptionOnItemsSearch(final Class<E> exception, final String multipleSearchLocator) {
    checkException(exception, () -> myFinder.getItems(multipleSearchLocator), "searching for itemS with locator \"" + multipleSearchLocator + "\"");
  }

  public <E extends Throwable> void checkExceptionOnItemSearch(final Class<E> exception, final String singleSearchLocator) {
    checkException(exception, () -> myFinder.getItem(singleSearchLocator), "searching for item with locator \"" + singleSearchLocator + "\"");
  }

  @NotNull
  public static <E extends Throwable> E checkException(final Class<E> exception, final Runnable runnable, final String operationDescription) {
    final String details = operationDescription != null ? " while " + operationDescription : "";
    try {
      runnable.run();
    } catch (Throwable e) {
      if (exception.isAssignableFrom(e.getClass())) {
        return (E)e;
      }
      final StringBuilder exceptionDetails = new StringBuilder();
      ExceptionUtil.dumpStacktrace(exceptionDetails, e);
      fail("Wrong exception type is thrown" + details + ".\n" +
           "Expected: " + exception.getName() + "\n" +
           "Actual  : " + exceptionDetails.toString());
    }
    fail("No exception is thrown" + details +
         ". Expected: " + exception.getName());
    return null; //this is never reached
  }

  public static <S> String getDescription(final List<S> result) {
    return getDescription(result, BaseFinderTest::getDescription);
  }

  public static <S> String getDescription(final List<S> result, @NotNull DescriptionProvider <S> logger) {
    if (result == null) {
      return LogUtil.describe((Object)null);
    }

    final StringBuilder result1 = new StringBuilder();
    final Iterator<S> it = result.iterator();
    while(it.hasNext()) {
      S item = it.next();
      if (item != null) {
        result1.append(logger.describe(item));
        if (it.hasNext()) {
          result1.append("\n");
        }
      }
    }
    return result1.toString();
  }

  public interface CollectionsMatchStrategy<S, R> {
    void matchCollection(@NotNull final S[] items, @NotNull final List<R> result);

    void matchSingle(final S[] items, final Supplier<R> singleResultSupplier);
  }

  public static class OrderedMatcherStrategy<S, R> implements CollectionsMatchStrategy<S, R> {
    @NotNull private final Matcher<S, R> myMatcher;
    @NotNull private final DescriptionProvider<Pair<List<R>, Integer>> myCollectionMatchDescriptionProvider;
    @NotNull private final DescriptionProvider<Pair<S, R>> mySingleMatchDescriptionProvider;

    public OrderedMatcherStrategy(@NotNull final Matcher<S, R> matcher,
                                  @NotNull final DescriptionProvider<Pair<List<R>, Integer>> collectionMatchDescriptionProvider,
                                  @NotNull final DescriptionProvider<Pair<S, R>> singleMatchDescriptionProvider) {
      myMatcher = matcher;
      myCollectionMatchDescriptionProvider = collectionMatchDescriptionProvider;
      mySingleMatchDescriptionProvider = singleMatchDescriptionProvider;
    }

    @Override
    public void matchCollection(@NotNull final S[] items, @NotNull final List<R> result) {
      for (int i = 0; i < items.length; i++) {
        if (!myMatcher.matches(items[i], result.get(i))) {
          fail(myCollectionMatchDescriptionProvider.describe(Pair.create(result, i)));
        }
      }
    }

    @Override
    public void matchSingle(final S[] items, final Supplier<R> singleResultSupplier) {
      if (items.length == 0) {
        try {
          R r = singleResultSupplier.get(); // should fail with NotFoundException
          fail(mySingleMatchDescriptionProvider.describe(Pair.create(null, r)));
        } catch (NotFoundException e) {
          //exception is expected
        }
      } else {
        R r = singleResultSupplier.get();
        final S item = items[0];
        if (!myMatcher.matches(item, r)) {
          fail(mySingleMatchDescriptionProvider.describe(Pair.create(item, r)));
        }
      }
    }
  }
}
