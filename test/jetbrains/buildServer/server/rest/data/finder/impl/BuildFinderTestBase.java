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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.buildTriggers.vcs.ModificationDataBuilder;
import jetbrains.buildServer.log.Loggable;
import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.db.DBActionNoResults;
import jetbrains.buildServer.serverSide.db.DBException;
import jetbrains.buildServer.serverSide.db.DBFunctions;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2015
 */
public class BuildFinderTestBase extends BaseFinderTest<SBuild> {
  private static Logger LOG = Logger.getInstance(BuildFinderTestBase.class.getName());

  public void checkBuilds(@Nullable final String locator, SBuild... builds) {
    checkMultipleBuilds(locator, builds);

    //check single build retrieve
    if (locator != null) {
      if (builds.length == 0) {
        checkNoBuildFound(locator);
      } else {
        checkBuild(locator, builds[0]);
      }
    }
  }

  public void checkMultipleBuilds(final @Nullable String locator, final SBuild... builds) {
    final List<BuildPromotion> result = myBuildFinder.getBuilds(null, locator).myEntries;
    final String expected = getPromotionsDescription(getPromotions(builds));
    final String actual = getPromotionsDescription(result);
    assertEquals("For builds locator \"" + locator + "\"\n" +
                 "Expected:\n" + expected + "\n\n" +
                 "Actual:\n" + actual, builds.length, result.size());

    for (int i = 0; i < builds.length; i++) {
      if (!builds[i].getBuildPromotion().equals(result.get(i))) {
        fail("Wrong build found for locator \"" + locator + "\" at position " + (i + 1) + "/" + builds.length + "\n" +
             "Expected:\n" + expected + "\n" +
             "\nActual:\n" + actual);
      }
    }
  }

  protected void checkBuild(final String locator, @NotNull SBuild build) {
    checkBuild(null, locator, build.getBuildPromotion());
  }

  protected void checkBuild(final String locator, @NotNull BuildPromotion buildPromotion) {
    checkBuild(null, locator, buildPromotion);
  }

  protected void checkBuild(final SBuildType buildType, final String locator, @NotNull BuildPromotion buildPromotion) {
    SBuild build = buildPromotion.getAssociatedBuild();
    if (build != null) {
      //checking for build
      SBuild result = myBuildFinder.getBuild(buildType, locator);

      if (!build.equals(result)) {
        fail("While searching for single build with locator \"" + locator + "\"\n" +
             "Expected: " + LogUtil.describeInDetail(build) + "\n" +
             "Actual: " + LogUtil.describeInDetail(result));
      }
    }

    //checking for build promotion
    BuildPromotion result1 = myBuildFinder.getBuildPromotion(buildType, locator);

    if (!buildPromotion.equals(result1)) {
      fail("While searching for single build promotion with locator \"" + locator + "\"\n" +
           "Expected: " + LogUtil.describeInDetail(buildPromotion) + "\n" +
           "Actual: " + LogUtil.describeInDetail(result1));
    }
  }

  private List<BuildPromotion> getPromotions(final SBuild[] builds) {
    return CollectionsUtil.convertCollection(Arrays.asList(builds), new Converter<BuildPromotion, SBuild>() {
      public BuildPromotion createFrom(@NotNull final SBuild source) {
        return source.getBuildPromotion();
      }
    });
  }

  private List<BuildPromotion> getPromotions(final Iterable<SBuild> builds) {
    return CollectionsUtil.convertCollection(builds, new Converter<BuildPromotion, SBuild>() {
      public BuildPromotion createFrom(@NotNull final SBuild source) {
        return source.getBuildPromotion();
      }
    });
  }

  protected void checkNoBuildsFound(@Nullable final String locator) {
    final List<BuildPromotion> result = myBuildFinder.getBuilds(null, locator).myEntries;
    if (!result.isEmpty()) {
      fail("For builds locator \"" + locator + "\" expected NotFoundException but found " + LogUtil.describe(result) + "");
    }
  }

  protected void checkNoBuildFound(final String singleBuildLocator) {
    checkExceptionOnBuildSearch(NotFoundException.class, singleBuildLocator);
  }

  public static String getPromotionsDescription(final List<BuildPromotion> result) {
    return LogUtil.describe(CollectionsUtil.convertCollection(result, new Converter<Loggable, BuildPromotion>() {
      public Loggable createFrom(@NotNull final BuildPromotion source) {
        return new Loggable() {
          @NotNull
          public String describe(final boolean verbose) {
            return LogUtil.appendDescription(LogUtil.describeInDetail(source), "startTime: " + LogUtil.describe(source.getServerStartDate()));
          }
        };
      }
    }), "\n", "", "");
  }

  public <E extends Throwable> void checkExceptionOnBuildSearch(final Class<E> exception, final String singleBuildLocator) {
    checkExceptionOnBuildSearch(exception, null, singleBuildLocator);
  }

  public <E extends Throwable> void checkExceptionOnBuildSearch(final Class<E> exception, final SBuildType buildType, final String singleBuildLocator) {
    checkException(exception, new Runnable() {
      public void run() {
        myBuildFinder.getBuild(buildType, singleBuildLocator);
      }
    }, "searching single build with locator \"" + singleBuildLocator + "\"");

    checkException(exception, new Runnable() {
      public void run() {
        myBuildFinder.getBuildPromotion(buildType, singleBuildLocator);
      }
    }, "searching single build promotion with locator \"" + singleBuildLocator + "\"");
  }

  public <E extends Throwable> void checkExceptionOnBuildsSearch(final Class<E> exception, @Nullable final String multipleBuildsLocator) {
    checkException(exception, new Runnable() {
      public void run() {
        myBuildFinder.getBuilds(null, multipleBuildsLocator);
      }
    }, "searching builds with locator \"" + multipleBuildsLocator + "\"");
  }

  @NotNull
  public static String fDate(final Date date) {
    return new SimpleDateFormat("yyyyMMdd'T'HHmmssZ", Locale.ENGLISH).format(date);
  }

  /**
   * requires calling recreateBuildServer() and re-initializing all the beans
   */
  protected void prepareFinishedBuildIdChange(final long oldId, final long newId) {
    withDBF(new DBActionNoResults() {
      public void run(final DBFunctions dbf) throws DBException {
        assertEquals(1, dbf.executeDml("update history set build_id = ? where build_id = ?", newId, oldId));
        assertEquals(1, dbf.executeDml("update build_state set build_id = ? where build_id = ?", newId, oldId));
      }
    }, true);
  }

  public static class MockCollectRepositoryChangesPolicy implements CollectChangesBetweenRepositories {

    private final ConcurrentMap<Long, RepositoryStateData> myCurrentStates = new ConcurrentHashMap<Long, RepositoryStateData>();
    private final ConcurrentMap<Long, RepositoryStateData> myLastToStates = new ConcurrentHashMap<Long, RepositoryStateData>();
    private final ConcurrentMap<Long, RepositoryStateData> myLastFromStates = new ConcurrentHashMap<Long, RepositoryStateData>();
    private final ConcurrentMap<Long, List<ModificationData>> myChangesPerRoot = new ConcurrentHashMap<Long, List<ModificationData>>();
    private final ConcurrentMap<Pair<Long, Long>, List<ModificationData>> myChangesPerRootInterval = new ConcurrentHashMap<Pair<Long, Long>, List<ModificationData>>();
    private final AtomicReference<RepositoryStateData> myLastToState = new AtomicReference<RepositoryStateData>();

    @NotNull
    public List<ModificationData> collectChanges(@NotNull final VcsRoot repository,
                                                 @NotNull final RepositoryStateData fromState,
                                                 @NotNull final RepositoryStateData toState,
                                                 @NotNull final CheckoutRules checkoutRules) throws VcsException {
      myLastToState.set(toState);
      myLastToStates.put(repository.getId(), toState);
      myLastFromStates.put(repository.getId(), fromState);
      List<ModificationData> changes = myChangesPerRoot.put(repository.getId(), Collections.<ModificationData>emptyList());
      if (changes != null)
        return changes;
      return Collections.emptyList();
    }

    @NotNull
    public List<ModificationData> collectChanges(@NotNull final VcsRoot fromRepository,
                                                 @NotNull final RepositoryStateData fromState,
                                                 @NotNull final VcsRoot toRepository,
                                                 @NotNull final RepositoryStateData toState,
                                                 @NotNull final CheckoutRules checkoutRules) throws VcsException {
      myLastToState.set(toState);
      myLastToStates.put(toRepository.getId(), toState);
      myLastFromStates.put(toRepository.getId(), fromState);
      List<ModificationData> changes = myChangesPerRootInterval.put(Pair.create(fromRepository.getId(), toRepository.getId()), Collections.<ModificationData>emptyList());
      if (changes != null)
        return changes;
      changes = myChangesPerRoot.put(toRepository.getId(), Collections.<ModificationData>emptyList());
      if (changes != null)
        return changes;
      return Collections.emptyList();
    }

    @NotNull
    public RepositoryStateData getCurrentState(@NotNull final VcsRoot repository) throws VcsException {
      return myCurrentStates.get(repository.getId());
    }

    public void setCurrentState(@NotNull VcsRoot repository, @NotNull RepositoryStateData state) {
      myCurrentStates.put(repository.getId(), state);
    }

    public RepositoryStateData getLastToState(@NotNull VcsRoot repository) {
      return myLastToStates.get(repository.getId());
    }

    public RepositoryStateData getLastToState() {
      return myLastToState.get();
    }

    public RepositoryStateData getLastFromState(@NotNull VcsRoot repository) {
      return myLastFromStates.get(repository.getId());
    }

    public void setChanges(@NotNull VcsRoot repository, @NotNull ModificationDataBuilder... changes) {
      List<ModificationData> newChanges = new ArrayList<ModificationData>();
      for (ModificationDataBuilder change : changes) {
        newChanges.add(change.build());
      }
      myChangesPerRoot.put(repository.getId(), newChanges);
    }

    public void setChanges(@NotNull VcsRoot fromRepository, @NotNull VcsRoot toRepository, @NotNull ModificationDataBuilder... changes) {
      List<ModificationData> newChanges = new ArrayList<ModificationData>();
      for (ModificationDataBuilder change : changes) {
        newChanges.add(change.build());
      }
      myChangesPerRootInterval.put(Pair.create(fromRepository.getId(), toRepository.getId()), newChanges);
    }

    public void setChanges(@NotNull VcsRoot repository, @NotNull List<ModificationData> changes) {
      List<ModificationData> newChanges = new ArrayList<ModificationData>();
      for (ModificationData change : changes) {
        newChanges.add(change);
      }
      myChangesPerRoot.put(repository.getId(), newChanges);
    }
  }
}
