/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.mute.CurrentMuteInfo;
import jetbrains.buildServer.serverSide.mute.ProblemMutingService;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.serverSide.problems.BuildProblemManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public class ProblemFinder extends AbstractFinder<ProblemWrapper> {
  private static final String CURRENT = "currentlyFailing";
  public static final String IDENTITY = "identity";
  public static final String TYPE = "type";
  public static final String AFFECTED_PROJECT = "affectedProject";
  public static final String CURRENTLY_INVESTIGATED = "currentlyInvestigated";
  public static final String CURRENTLY_MUTED = "currentlyMuted";

  @NotNull private final ProjectFinder myProjectFinder;

  @NotNull private final BuildProblemManager myBuildProblemManager;
  @NotNull private final ProjectManager myProjectManager;
  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final ProblemMutingService myProblemMutingService;

  public ProblemFinder(final @NotNull ProjectFinder projectFinder,
                       final @NotNull BuildProblemManager buildProblemManager,
                       final @NotNull ProjectManager projectManager,
                       final @NotNull ServiceLocator serviceLocator,
                       final @NotNull ProblemMutingService problemMutingService) {
    super(new String[]{DIMENSION_ID, IDENTITY, TYPE, AFFECTED_PROJECT, CURRENT, CURRENTLY_INVESTIGATED, CURRENTLY_MUTED, PagerData.START, PagerData.COUNT,
      Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME, DIMENSION_LOOKUP_LIMIT});
    myProjectFinder = projectFinder;
    myBuildProblemManager = buildProblemManager;
    myProjectManager = projectManager;
    myServiceLocator = serviceLocator;
    myProblemMutingService = problemMutingService;
  }

  public static String getLocator(final ProblemWrapper problem) {
    return Locator.createEmptyLocator().setDimension(DIMENSION_ID, String.valueOf(problem.getId())).getStringRepresentation();
  }

  public static String getLocator(final int problemId) {
    return Locator.createEmptyLocator().setDimension(DIMENSION_ID, String.valueOf(problemId)).getStringRepresentation();
  }

  @Override
  @Nullable
  protected ProblemWrapper findSingleItem(@NotNull final Locator locator) {
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

  @Override
  @NotNull
  public ItemHolder<ProblemWrapper> getAllItems() {
    //todo: TeamCity API: find a way to do this
    ArrayList<String> exampleLocators = new ArrayList<String>();
    exampleLocators.add(Locator.getStringLocator(DIMENSION_ID, "XXX"));
    exampleLocators.add(Locator.getStringLocator(CURRENT, "true", AFFECTED_PROJECT, "XXX"));
    exampleLocators.add(Locator.getStringLocator(CURRENTLY_MUTED, "true", AFFECTED_PROJECT, "XXX"));
    throw new BadRequestException("Listing all problems is not supported. Try one of locator dimensions: " + DataProvider.dumpQuoted(exampleLocators));
  }

  @Override
  protected ItemHolder<ProblemWrapper> getPrefilteredItems(@NotNull final Locator locator) {
    final SProject affectedProject;
    String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null) {
      affectedProject = myProjectFinder.getProject(affectedProjectDimension);
    }else{
      affectedProject = myProjectFinder.getRootProject();
    }

    Boolean currentDimension = locator.getSingleDimensionValueAsBoolean(CURRENT);
    if (currentDimension!= null && currentDimension) {
        return getItemHolder(getCurrentProblemsList(affectedProject));
    }

    Boolean currentlyMutedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_MUTED);
    if (currentlyMutedDimension != null && currentlyMutedDimension) {
      return getItemHolder(getCurrentlyMutedProblems(affectedProject));
    }

    return super.getPrefilteredItems(locator);
  }

  @NotNull
  @Override
  protected AbstractFilter<ProblemWrapper> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<ProblemWrapper> result = new MultiCheckerFilter<ProblemWrapper>(locator.getSingleDimensionValueAsLong(PagerData.START),
                                                                                             countFromFilter != null ? countFromFilter.intValue() : null,
                                                                                             locator.getSingleDimensionValueAsLong(DIMENSION_LOOKUP_LIMIT));

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
      @NotNull final SProject project = myProjectFinder.getProject(affectedProjectDimension);
      final Set<ProblemWrapper> currentProjectProblems = new TreeSet<ProblemWrapper>(getCurrentProblemsList(project));
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

    if (locator.getUnusedDimensions().contains(CURRENT)) {
      final String currentDimension = locator.getSingleDimensionValue(CURRENT);
      if (currentDimension != null) {
        final Set<ProblemWrapper> currentProblems = new TreeSet<ProblemWrapper>(getCurrentProblemsList(null));
        result.add(new FilterConditionChecker<ProblemWrapper>() {
          public boolean isIncluded(@NotNull final ProblemWrapper item) {
            return currentProblems.contains(item);
          }
        });
      }
    }

    return result;
  }

  @NotNull
  private List<ProblemWrapper> getCurrentProblemsList(@Nullable SProject project) {
    if (project == null){
      project = myProjectManager.getRootProject();
    }
    final List<BuildProblem> currentBuildProblemsList = myBuildProblemManager.getCurrentBuildProblemsList(project);

    @NotNull final Set<ProblemWrapper> resultSet = new TreeSet<ProblemWrapper>();
    for (BuildProblem buildProblem : currentBuildProblemsList) {
      resultSet.add(new ProblemWrapper(buildProblem.getId(), buildProblem.getBuildProblemData(), myServiceLocator));
    }

    return new ArrayList<ProblemWrapper>(resultSet);
  }

  public List<ProblemWrapper> getCurrentlyMutedProblems(final SProject affectedProject) {
    final Map<Integer,CurrentMuteInfo> currentMutes = myProblemMutingService.getBuildProblemCurrentMuteInfos(affectedProject);
    final HashSet<ProblemWrapper> result = new HashSet<ProblemWrapper>(currentMutes.size());
    for (Map.Entry<Integer, CurrentMuteInfo> mutedData : currentMutes.entrySet()) {
      result.add(new ProblemWrapper(mutedData.getKey(), myServiceLocator));
    }
    return new ArrayList<ProblemWrapper>(result);
  }

  public static List<ProblemWrapper> getProblemWrappers(@NotNull Collection<Integer> problemIds, @NotNull final ServiceLocator serviceLocator) {
    final HashSet<ProblemWrapper> result = new HashSet<ProblemWrapper>(problemIds.size());
    for (Integer problemId : problemIds) {
      result.add(new ProblemWrapper(problemId, serviceLocator));
    }
    return new ArrayList<ProblemWrapper>(result);
  }
}
