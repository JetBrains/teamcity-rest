/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import java.util.*;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.mute.CurrentMuteInfo;
import jetbrains.buildServer.serverSide.mute.ProblemMutingService;
import jetbrains.buildServer.tests.TestName;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public class TestFinder extends AbstractFinder<STest> {
  private static final String NAME = "name";
  public static final String AFFECTED_PROJECT = "affectedProject";
  private static final String CURRENT = "currentlyFailing";
  public static final String CURRENTLY_INVESTIGATED = "currentlyInvestigated";
  public static final String CURRENTLY_MUTED = "currentlyMuted";
  public static final String MUTE_AFFECTED = "muteAffected";
  public static final String BUILD = "build";

  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final BuildTypeFinder myBuildTypeFinder;
  @NotNull private final BuildPromotionFinder myBuildPromotionFinder;
  @NotNull private final STestManager myTestManager;
  @NotNull private final TestName2IndexImpl myTestName2Index; //TeamCIty open API issue
  @NotNull private final CurrentProblemsManager myCurrentProblemsManager;
  @NotNull private final ProblemMutingService myProblemMutingService;

  public TestFinder(final @NotNull ProjectFinder projectFinder,
                    final @NotNull BuildTypeFinder buildTypeFinder,
                    final @NotNull BuildPromotionFinder buildPromotionFinder,
                    final @NotNull STestManager testManager,
                    final @NotNull TestName2IndexImpl testName2Index,
                    final @NotNull CurrentProblemsManager currentProblemsManager,
                    final @NotNull ProblemMutingService problemMutingService) {
    super(DIMENSION_ID, NAME, AFFECTED_PROJECT, CURRENT, CURRENTLY_INVESTIGATED, CURRENTLY_MUTED, MUTE_AFFECTED,
      Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    setHiddenDimensions(BUILD); //ineffective performance-wise
    myBuildPromotionFinder = buildPromotionFinder;
    myTestManager = testManager;
    myProjectFinder = projectFinder;
    myBuildTypeFinder = buildTypeFinder;
    myTestName2Index = testName2Index;
    myCurrentProblemsManager = currentProblemsManager;
    myProblemMutingService = problemMutingService;
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final STest sTest) {
    return TestFinder.getTestLocator(sTest);
  }

  public static String getTestLocator(final @NotNull STest test) {
    return getTestLocator(test.getTestNameId());
  }

  public static String getTestLocator(final long testNameId) {
    return Locator.createEmptyLocator().setDimension(DIMENSION_ID, String.valueOf(testNameId)).getStringRepresentation();
  }

  @Override
  @Nullable
  public STest findSingleItem(@NotNull final Locator locator) {
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's id
      final Long parsedId = locator.getSingleValueAsLong();
      if (parsedId == null) {
        throw new BadRequestException("Expecting id, found empty value.");
      }
      STest item = findTest(parsedId);
      if (item == null) {
        throw new NotFoundException("No test can be found by id '" + parsedId + "' on the entire server.");
      }
      return item;
    }

    // dimension-specific item search

    Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      STest item = findTest(id);
      if (item == null) {
        throw new NotFoundException("No test" + " can be found by " + DIMENSION_ID + " '" + id + "' on the entire server.");
      }
      return item;
    }

    String nameDimension = locator.getSingleDimensionValue(NAME);
    if (nameDimension != null) {
      final Long testNameId = myTestName2Index.findTestNameId(new TestName(nameDimension));
      if (testNameId == null) {
        throw new NotFoundException("No test can be found by " + NAME + " '" + nameDimension + "' on the entire server.");
      }
      return findTest(testNameId);
    }

    return null;
  }

  @NotNull
  @Override
  public ItemHolder<STest> getPrefilteredItems(@NotNull final Locator locator) {
    String buildLocator = locator.getSingleDimensionValue(BUILD);
    if (buildLocator != null){
      return getItemHolder(getTestsByBuilds(buildLocator));
    }

    final SProject affectedProject;
    String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null) {
      affectedProject = myProjectFinder.getItem(affectedProjectDimension);
    }else{
      affectedProject = myProjectFinder.getRootProject();
    }

    Boolean currentDimension = locator.getSingleDimensionValueAsBoolean(CURRENT);
    if (currentDimension != null && currentDimension) {
      return getItemHolder(getCurrentlyFailingTests(affectedProject));
    }

    Boolean currentlyMutedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_MUTED);
    if (currentlyMutedDimension != null && currentlyMutedDimension) {
      return getItemHolder(getCurrentlyMutedTests(affectedProject));
    }

    //todo: TeamCity API: find a way to support more cases

    ArrayList<String> exampleLocators = new ArrayList<String>();
    exampleLocators.add(Locator.getStringLocator(DIMENSION_ID, "XXX"));
    exampleLocators.add(Locator.getStringLocator(NAME, "XXX"));
    exampleLocators.add(Locator.getStringLocator(CURRENT, "true", AFFECTED_PROJECT, "XXX"));
    exampleLocators.add(Locator.getStringLocator(CURRENTLY_MUTED, "true", AFFECTED_PROJECT, "XXX"));
    throw new BadRequestException("Unsupported test locator '" + locator.getStringRepresentation() + "'. Try locators: " + DataProvider.dumpQuoted(exampleLocators));
  }

  @NotNull
  private TreeSet<STest> getTestsByBuilds(@NotNull final String buildLocator) {
    TreeSet<STest> result = new TreeSet<>();
    List<BuildPromotion> builds = myBuildPromotionFinder.getItems(buildLocator).myEntries;
    for (BuildPromotion build : builds) {
      SBuild associatedBuild = build.getAssociatedBuild();
      if (associatedBuild != null){
        result.addAll(CollectionsUtil.convertCollection(associatedBuild.getFullStatistics().getAllTests(), new Converter<STest, STestRun>() {
          @Override
          public STest createFrom(@NotNull final STestRun source) {
            return source.getTest();
          }
        }));
      }
    }
    return result;
  }

  Set<STest> getCurrentlyMutedTests(final SProject affectedProject) {
    final Map<Long,CurrentMuteInfo> currentMutes = myProblemMutingService.getTestsCurrentMuteInfo(affectedProject);
    final TreeSet<STest> result = new TreeSet<STest>();
    for (Map.Entry<Long, CurrentMuteInfo> mutedTestData : currentMutes.entrySet()) {
      result.add(findTest(mutedTestData.getKey()));
    }
    return result;
  }

  private Set<STest> getCurrentlyFailingTests(@NotNull final SProject affectedProject) {
    final Set<STestRun> failingTestOccurrences = TestOccurrenceFinder.getCurrentOccurrences(affectedProject, myCurrentProblemsManager);
    final TreeSet<STest> result = new TreeSet<STest>();
    for (STestRun testOccurrence : failingTestOccurrences) {
      result.add(testOccurrence.getTest());
    }
    return result;
  }

  @NotNull
  @Override
  public ItemFilter<STest> getFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<STest> result = new MultiCheckerFilter<STest>();

    final String nameDimension = locator.getSingleDimensionValue(NAME);
    if (nameDimension != null) {
      result.add(new FilterConditionChecker<STest>() {
        public boolean isIncluded(@NotNull final STest item) {
          return nameDimension.equals(item.getName().getAsString());
        }
      });
    }

    final Boolean currentlyInvestigatedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_INVESTIGATED);
    if (currentlyInvestigatedDimension != null) {
      result.add(new FilterConditionChecker<STest>() {
        public boolean isIncluded(@NotNull final STest item) {
          //todo: check investigation in affected Project/buildType only, if set
          return FilterUtil.isIncludedByBooleanFilter(currentlyInvestigatedDimension, !item.getAllResponsibilities().isEmpty());
        }
      });
    }

    final Boolean currentlyMutedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_MUTED);
    if (currentlyMutedDimension != null) {
      result.add(new FilterConditionChecker<STest>() {
        public boolean isIncluded(@NotNull final STest item) {
          //todo: check mute in affected Project/buildType only, if set
          return FilterUtil.isIncludedByBooleanFilter(currentlyMutedDimension, item.getCurrentMuteInfo() != null);
        }
      });
    }

    final String muteAffectedLocatorText = locator.getSingleDimensionValue(MUTE_AFFECTED);
    if (muteAffectedLocatorText != null) {
      Locator muteAffectedLocator = new Locator(muteAffectedLocatorText, "buildType", "project");
      String muteAffectedBuildTypeLocatorText = muteAffectedLocator.getSingleDimensionValue("buildType");
      if (muteAffectedBuildTypeLocatorText != null) {
        final List<SBuildType> buildTypes = myBuildTypeFinder.getBuildTypes(null, muteAffectedBuildTypeLocatorText);
        result.add(new FilterConditionChecker<STest>() {
          public boolean isIncluded(@NotNull final STest item) {
            CurrentMuteInfo muteInfo = item.getCurrentMuteInfo();
            if (muteInfo == null) return false;
            Set<SProject> mutedInProjects = muteInfo.getProjectsMuteInfo().keySet();
            Set<SBuildType> mutedInBuildTypes = muteInfo.getBuildTypeMuteInfo().keySet();
            for (SBuildType buildType : buildTypes) {
              if (buildType == null) continue;
              if (mutedInBuildTypes.contains(buildType)) return true;
              for (SProject mutedInProject : mutedInProjects) {
                if (ProjectFinder.isSameOrParent(mutedInProject, buildType.getProject())) return true;
              }
            }
            return false;
          }
        });
      }
      String muteAffectedProjectLocatorText = muteAffectedLocator.getSingleDimensionValue("project");
      if (muteAffectedProjectLocatorText != null) {
        List<SProject> projects = myProjectFinder.getItems(muteAffectedProjectLocatorText).myEntries;
        result.add(new FilterConditionChecker<STest>() {
          public boolean isIncluded(@NotNull final STest item) {
            CurrentMuteInfo muteInfo = item.getCurrentMuteInfo();
            if (muteInfo == null) return false;
            Set<SProject> mutedInProjects = muteInfo.getProjectsMuteInfo().keySet();
            for (SProject project : projects) {
              for (SProject mutedInProject : mutedInProjects) {
                if (ProjectFinder.isSameOrParent(mutedInProject, project)) return true;
              }
            }
            return false;
          }
        });
      }
      muteAffectedLocator.checkLocatorFullyProcessed();
    }

    if (locator.isUnused(BUILD)) {
      String buildLocator = locator.getSingleDimensionValue(BUILD);
      if (buildLocator != null) {
        final TreeSet<STest> tests = getTestsByBuilds(buildLocator);
        result.add(new FilterConditionChecker<STest>() {
          @Override
          public boolean isIncluded(@NotNull final STest item) {
            return tests.contains(item);
          }
        });
      }
    }

    return result;
  }

  @Nullable
  public STest findTest(final @NotNull Long testNameId) {
    return myTestManager.findTest(testNameId, myProjectFinder.getRootProject().getProjectId()); //STest in root project should have all the data across entire server
  }
}
