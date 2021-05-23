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

import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorDimensionDataType;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.mute.CurrentMuteInfo;
import jetbrains.buildServer.serverSide.mute.ProblemMutingService;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.serverSide.problems.BuildProblemManager;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
@LocatorResource(value = LocatorName.PROBLEM,
    extraDimensions = {AbstractFinder.DIMENSION_ID, AbstractFinder.DIMENSION_LOOKUP_LIMIT, PagerData.START, PagerData.COUNT, AbstractFinder.DIMENSION_ITEM},
    baseEntity = "Problem",
    examples = {
        "`currentlyInvestigated:true` — find last 100 build problems which are being currently investigated.",
        "`build:<buildLocator>` — find build problems under build found by `buildLocator`."
    }
)
public class ProblemFinder extends AbstractFinder<ProblemWrapper> {
  @LocatorDimension(value = "currentlyFailing", dataType = LocatorDimensionDataType.BOOLEAN, notes = "Is currently failing.")
  private static final String CURRENT = "currentlyFailing";
  @LocatorDimension("identity") public static final String IDENTITY = "identity";
  @LocatorDimension("type") public static final String TYPE = "type";
  @LocatorDimension(value = "affectedProject", format = LocatorName.PROJECT, notes = "Project (direct or indirect parent) locator.")
  public static final String AFFECTED_PROJECT = "affectedProject";
  @LocatorDimension(value = "currentlyInvestigated", dataType = LocatorDimensionDataType.BOOLEAN, notes = "Is currently investigated.")
  public static final String CURRENTLY_INVESTIGATED = "currentlyInvestigated";
  @LocatorDimension(value = "currentlyMuted", dataType = LocatorDimensionDataType.BOOLEAN, notes = "Is currently muted.")
  public static final String CURRENTLY_MUTED = "currentlyMuted";
  @LocatorDimension(value = "build", format = LocatorName.BUILD, notes = "Build locator.")
  public static final String BUILD = "build";

  @NotNull private final ProjectFinder myProjectFinder;

  @NotNull private final BuildPromotionFinder myBuildPromotionFinder;
  @NotNull private final BuildProblemManager myBuildProblemManager;
  @NotNull private final ProjectManager myProjectManager;
  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final ProblemMutingService myProblemMutingService;

  public ProblemFinder(final @NotNull ProjectFinder projectFinder,
                       final @NotNull BuildPromotionFinder buildPromotionFinder,
                       final @NotNull BuildProblemManager buildProblemManager,
                       final @NotNull ProjectManager projectManager,
                       final @NotNull ServiceLocator serviceLocator,
                       final @NotNull ProblemMutingService problemMutingService) {
    super(DIMENSION_ID, IDENTITY, TYPE, AFFECTED_PROJECT, CURRENT, CURRENTLY_INVESTIGATED, CURRENTLY_MUTED,
          Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    setHiddenDimensions(BUILD); //ineffective perfomance-wise
    myProjectFinder = projectFinder;
    myBuildPromotionFinder = buildPromotionFinder;
    myBuildProblemManager = buildProblemManager;
    myProjectManager = projectManager;
    myServiceLocator = serviceLocator;
    myProblemMutingService = problemMutingService;
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final ProblemWrapper problemWrapper) {
    return ProblemFinder.getLocator(problemWrapper);
  }

  public static String getLocator(final ProblemWrapper problem) {
    return getLocator(problem.getId().intValue());
  }

  public static String getLocator(final int problemId) {
    return Locator.createEmptyLocator().setDimension(DIMENSION_ID, String.valueOf(problemId)).getStringRepresentation();
  }

  @Override
  @Nullable
  public ProblemWrapper findSingleItem(@NotNull final Locator locator) {
    Long id = getProblemIdByLocator(locator);
    if (id != null) {
      return new ProblemWrapper(id.intValue(), myServiceLocator);
    }

    return null;
  }

  @Nullable
  public static Long getProblemIdByLocator(@NotNull final Locator locator){
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's id
      final Long parsedId = locator.getSingleValueAsLong();
      if (parsedId == null) {
        throw new BadRequestException("Expecting id, found empty value.");
      }
      return parsedId;
    }

    // dimension-specific item search
    Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      return id;
    }
    return null;
  }

  @NotNull
  @Override
  public ItemHolder<ProblemWrapper> getPrefilteredItems(@NotNull final Locator locator) {
    String buildLocator = locator.getSingleDimensionValue(BUILD);
    if (buildLocator != null) {
      return getItemHolder(getProblemsByBuilds(buildLocator));
    }

    final SProject affectedProject;
    String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null) {
      affectedProject = myProjectFinder.getItem(affectedProjectDimension);
    }else{
      affectedProject = myProjectFinder.getRootProject();
    }

    Boolean currentDimension = locator.lookupSingleDimensionValueAsBoolean(CURRENT);
    if (currentDimension != null && currentDimension) {
      locator.markUsed(Collections.singleton(CURRENT));
      return getItemHolder(getCurrentProblemsList(affectedProject));
    }

    Boolean currentlyMutedDimension = locator.lookupSingleDimensionValueAsBoolean(CURRENTLY_MUTED);
    if (currentlyMutedDimension != null && currentlyMutedDimension) {
      locator.markUsed(Collections.singleton(CURRENTLY_MUTED));
      return getItemHolder(getCurrentlyMutedProblems(affectedProject));
    }

    //todo: TeamCity API: find a way to do this
    ArrayList<String> exampleLocators = new ArrayList<String>();
    exampleLocators.add(Locator.getStringLocator(DIMENSION_ID, "XXX"));
    exampleLocators.add(Locator.getStringLocator(CURRENT, "true", AFFECTED_PROJECT, "XXX"));
    exampleLocators.add(Locator.getStringLocator(CURRENTLY_MUTED, "true", AFFECTED_PROJECT, "XXX"));
    throw new BadRequestException("Unsupported problem locator '" + locator.getStringRepresentation() + "'. Try one of locator dimensions: " + DataProvider.dumpQuoted(exampleLocators));
  }

  @NotNull
  @Override
  public ItemFilter<ProblemWrapper> getFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<ProblemWrapper> result = new MultiCheckerFilter<ProblemWrapper>();

    final String identityDimension = locator.getSingleDimensionValue(IDENTITY);
    if (identityDimension != null) {
      result.add(new FilterConditionChecker<ProblemWrapper>() {
        public boolean isIncluded(@NotNull final ProblemWrapper item) {
          return identityDimension.equals(item.getIdentity());
        }
      });
    }

    final String typeDimension = locator.getSingleDimensionValue(TYPE);
    if (typeDimension != null) {
      result.add(new FilterConditionChecker<ProblemWrapper>() {
        public boolean isIncluded(@NotNull final ProblemWrapper item) {
          return typeDimension.equals(item.getType());
        }
      });
    }

    final String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null) {
      @NotNull final SProject project = myProjectFinder.getItem(affectedProjectDimension);
      final Set<ProblemWrapper> currentProjectProblems = getCurrentProblemsList(project);
      //todo: bug: searches only inside current problems: non-current problems are not returned
      result.add(new FilterConditionChecker<ProblemWrapper>() {
        public boolean isIncluded(@NotNull final ProblemWrapper item) {
          return currentProjectProblems.contains(item);  //todo: TeamCity API (VB): is there a dedicated API call for this?  -- consider doing this via ProblemOccurrences
        }
      });
    }

    final Boolean currentlyInvestigatedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_INVESTIGATED);
    if (currentlyInvestigatedDimension != null) {
      result.add(new FilterConditionChecker<ProblemWrapper>() {
        public boolean isIncluded(@NotNull final ProblemWrapper item) {
          //todo: check investigation in affected Project/buildType only, if set
          return FilterUtil.isIncludedByBooleanFilter(currentlyInvestigatedDimension, !item.getInvestigations().isEmpty());
        }
      });
    }

    final Boolean currentlyMutedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_MUTED);
    if (currentlyMutedDimension != null) {
      result.add(new FilterConditionChecker<ProblemWrapper>() {
        public boolean isIncluded(@NotNull final ProblemWrapper item) {
          //todo: check in affected Project/buildType only, if set
          return FilterUtil.isIncludedByBooleanFilter(currentlyMutedDimension, !item.getMutes().isEmpty());
        }
      });
    }

    if (locator.isUnused(CURRENT)) {
      final Boolean currentDimension = locator.getSingleDimensionValueAsBoolean(CURRENT);
      if (currentDimension != null) {
        final Set<ProblemWrapper> currentProblems = getCurrentProblemsList(null);
        result.add(item -> FilterUtil.isIncludedByBooleanFilter(currentDimension, currentProblems.contains(item)));
      }
    }

    if (locator.isUnused(BUILD)) {
      String buildLocator = locator.getSingleDimensionValue(BUILD);
      if (buildLocator != null) {
        final Set<ProblemWrapper> problems = getProblemsByBuilds(buildLocator);
        result.add(new FilterConditionChecker<ProblemWrapper>() {
          @Override
          public boolean isIncluded(@NotNull final ProblemWrapper item) {
            return problems.contains(item);
          }
        });
      }
    }

    return result;
  }

  @NotNull
  private Set<ProblemWrapper> getCurrentProblemsList(@Nullable SProject project) {
    if (project == null){
      project = myProjectManager.getRootProject();
    }
    final List<BuildProblem> currentBuildProblemsList = myBuildProblemManager.getCurrentBuildProblemsList(project);

    @NotNull final Set<ProblemWrapper> resultSet = new TreeSet<ProblemWrapper>();
    for (BuildProblem buildProblem : currentBuildProblemsList) {
      resultSet.add(new ProblemWrapper(buildProblem.getId(), buildProblem.getBuildProblemData(), myServiceLocator));
    }

    return resultSet;
  }

  @NotNull
  private Set<ProblemWrapper> getProblemsByBuilds(@NotNull final String buildLocator) {
    LinkedHashSet<ProblemWrapper> result = new LinkedHashSet<>();
    List<BuildPromotion> builds = myBuildPromotionFinder.getItems(buildLocator).myEntries;
    for (BuildPromotion build : builds) {
      result.addAll(CollectionsUtil.convertCollection(ProblemOccurrenceFinder.getProblemOccurrences(build), new Converter<ProblemWrapper, BuildProblem>() {
        @Override
        public ProblemWrapper createFrom(@NotNull final BuildProblem buildProblem) {
          return new ProblemWrapper(buildProblem.getId(), buildProblem.getBuildProblemData(), myServiceLocator);
        }
      }));
    }
    return result;
  }

  public Set<ProblemWrapper> getCurrentlyMutedProblems(final SProject affectedProject) {
    final Map<Integer,CurrentMuteInfo> currentMutes = myProblemMutingService.getBuildProblemsCurrentMuteInfo(affectedProject);
    final TreeSet<ProblemWrapper> result = new TreeSet<ProblemWrapper>();
    for (Map.Entry<Integer, CurrentMuteInfo> mutedData : currentMutes.entrySet()) {
      result.add(new ProblemWrapper(mutedData.getKey(), myServiceLocator));
    }
    return result;
  }

  public static List<ProblemWrapper> getProblemWrappers(@NotNull Collection<Integer> problemIds, @NotNull final ServiceLocator serviceLocator) {
    final TreeSet<ProblemWrapper> result = new TreeSet<ProblemWrapper>();
    for (Integer problemId : problemIds) {
      result.add(new ProblemWrapper(problemId, serviceLocator));
    }
    return new ArrayList<ProblemWrapper>(result);
  }
}
