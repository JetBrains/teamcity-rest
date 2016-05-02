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

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.request.BuildRequest;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.db.DBActionNoResults;
import jetbrains.buildServer.serverSide.db.DBException;
import jetbrains.buildServer.serverSide.db.DBFunctions;
import jetbrains.buildServer.serverSide.db.SQLRunnerEx;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.serverSide.problems.BuildProblemManager;
import jetbrains.buildServer.util.ItemProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 18.11.13
 */
public class ProblemOccurrenceFinder extends AbstractFinder<BuildProblem> {
  private static Logger LOG = Logger.getInstance(ProblemOccurrenceFinder.class.getName());

  private static final String BUILD = "build";
  private static final String IDENTITY = "identity";
  private static final String CURRENT = "currentlyFailing";
  private static final String PROBLEM = "problem";
  public static final String CURRENTLY_INVESTIGATED = "currentlyInvestigated";
  public static final String MUTED = "muted";
  public static final String CURRENTLY_MUTED = "currentlyMuted";
  public static final String AFFECTED_PROJECT = "affectedProject";

  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final BuildFinder myBuildFinder;
  @NotNull private final ProblemFinder myProblemFinder;

  @NotNull private final BuildProblemManager myBuildProblemManager;
  @NotNull private final ProjectManager myProjectManager;
  @NotNull private final jetbrains.buildServer.ServiceLocator myServiceLocator;

  public ProblemOccurrenceFinder(final @NotNull ProjectFinder projectFinder,
                                 final @NotNull BuildFinder buildFinder,
                                 final @NotNull ProblemFinder problemFinder,
                                 final @NotNull BuildProblemManager buildProblemManager,
                                 final @NotNull ProjectManager projectManager,
                                 final @NotNull ServiceLocator serviceLocator) {
    super(new String[]{PROBLEM, IDENTITY, "type", "build", AFFECTED_PROJECT, CURRENT, MUTED, CURRENTLY_MUTED, CURRENTLY_INVESTIGATED});
    myProjectFinder = projectFinder;
    myBuildFinder = buildFinder;
    myProblemFinder = problemFinder;
    myBuildProblemManager = buildProblemManager;
    myProjectManager = projectManager;
    myServiceLocator = serviceLocator;
  }

  @Override
  public Long getDefaultPageItemsCount() {
    return (long)Constants.getDefaultPageItemsCount();
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final BuildProblem buildProblem) {
    return ProblemOccurrenceFinder.getProblemOccurrenceLocator(buildProblem);
  }

  public static String getProblemOccurrenceLocator(final @NotNull BuildProblem problem) {
    final SBuild build = problem.getBuildPromotion().getAssociatedBuild();
    if (build == null) {
      throw new InvalidStateException("Build problem with id '" + problem.getId() + "' does not have an associated build.");
    }
    return Locator.createEmptyLocator().setDimension(PROBLEM, ProblemFinder.getLocator(problem.getId())).setDimension(BUILD, BuildRequest
      .getBuildLocator(build)).getStringRepresentation();
  }

  public static String getProblemOccurrenceLocator(final @NotNull SBuild build) {
    return Locator.createEmptyLocator().setDimension(BUILD, BuildRequest.getBuildLocator(build)).getStringRepresentation();
  }

  public static String getProblemOccurrenceLocator(final @NotNull BuildPromotion buildPromotion) {
    return Locator.createEmptyLocator().setDimension(BUILD, BuildRequest.getBuildLocator(buildPromotion)).getStringRepresentation();
  }

  public static String getProblemOccurrenceLocator(final @NotNull ProblemWrapper problem) {
    return Locator.createEmptyLocator().setDimension(PROBLEM, ProblemFinder.getLocator(problem)).getStringRepresentation();
  }

  @Override
  protected BuildProblem findSingleItem(@NotNull final Locator locator) {
    //todo: searching occurrence by id does not work: review!!!

    // dimension-specific item search

    String buildDimension = locator.getSingleDimensionValue(BUILD);
    if (buildDimension != null) {
      List<BuildPromotion> builds = myBuildFinder.getBuilds(null, buildDimension).myEntries;
      if (builds.size() != 1) {
        return null;
      }
      final BuildPromotion build = builds.get(0);
      String problemDimension = locator.getSingleDimensionValue(PROBLEM);
      if (problemDimension != null) {
        Long problemId = ProblemFinder.getProblemIdByLocator(new Locator(problemDimension));
        if(problemId == null){
          problemId = myProblemFinder.getItem(problemDimension).getId();
        }
        final BuildProblem item = findProblem(build, problemId);
        if (item != null) {
          return item;
        }
        throw new NotFoundException("No problem with id '" + problemId + "' found in build with id " + build.getId());  //message might be incorrect: uses promotion id
      } else{
        locator.markUnused(BUILD, PROBLEM);
      }
    }
    return null;
  }

  @NotNull
  @Override
  protected ItemHolder<BuildProblem> getPrefilteredItems(@NotNull final Locator locator) {
    String buildDimension = locator.getSingleDimensionValue(BUILD);
    if (buildDimension != null) {
      List<BuildPromotion> builds = myBuildFinder.getBuilds(null, buildDimension).myEntries;
      AggregatingItemHolder<BuildProblem> result = new AggregatingItemHolder<>();
      for (BuildPromotion build : builds) {
        List<BuildProblem> buildProblemOccurrences = getProblemOccurrences(build);
        if (!buildProblemOccurrences.isEmpty()) result.add(getItemHolder(buildProblemOccurrences));
      }
      return result;
    }

    Boolean currentDimension = locator.getSingleDimensionValueAsBoolean(CURRENT);
    if (currentDimension != null && currentDimension) {
      return getItemHolder(getCurrentProblemOccurences(getAffectedProject(locator)));
    }

    String problemDimension = locator.getSingleDimensionValue(PROBLEM);
    if (problemDimension != null) {
      return getProblemOccurrences(myProblemFinder.getItems(problemDimension).myEntries);
    }

    Boolean currentlyMutedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_MUTED);
    if (currentlyMutedDimension != null && currentlyMutedDimension) {
      final SProject affectedProject = getAffectedProject(locator);
      return getProblemOccurrences(myProblemFinder.getCurrentlyMutedProblems(affectedProject));
    }

    ArrayList<String> exampleLocators = new ArrayList<String>();
//    exampleLocators.add(Locator.getStringLocator(DIMENSION_ID, "XXX"));
    exampleLocators.add(Locator.getStringLocator(BUILD, "XXX"));
    exampleLocators.add(Locator.getStringLocator(PROBLEM, "XXX"));
    exampleLocators.add(Locator.getStringLocator(CURRENT, "true", AFFECTED_PROJECT, "XXX"));
    exampleLocators.add(Locator.getStringLocator(CURRENTLY_MUTED, "true", AFFECTED_PROJECT, "XXX"));
    throw new BadRequestException("Unsupported problem occurrence locator '" + locator.getStringRepresentation() + "'. Try one of locator dimensions: " + DataProvider.dumpQuoted(exampleLocators));
  }

  @NotNull
  private ItemHolder<BuildProblem> getProblemOccurrences(@NotNull final Iterable<ProblemWrapper> problems) {
    final AggregatingItemHolder<BuildProblem> result = new AggregatingItemHolder<BuildProblem>();
    for (ProblemWrapper problem : problems) {
      result.add(getProblemOccurrences(problem.getId(), myServiceLocator, myBuildFinder));
    }
    return result;
  }

  @NotNull
  @Override
  protected ItemFilter<BuildProblem> getFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<BuildProblem> result = new MultiCheckerFilter<BuildProblem>();

    if (locator.isUnused(PROBLEM)) {
      String problemDimension = locator.getSingleDimensionValue(PROBLEM);
      if (problemDimension != null) {
        final PagedSearchResult<ProblemWrapper> problems = myProblemFinder.getItems(problemDimension);
        final HashSet<Integer> problemIds = new HashSet<Integer>();
        for (ProblemWrapper problem : problems.myEntries) {
          problemIds.add(problem.getId().intValue());
        }
        result.add(new FilterConditionChecker<BuildProblem>() {
          public boolean isIncluded(@NotNull final BuildProblem item) {
            return problemIds.contains(item.getId());
          }
        });
      }
    }

    final String identityDimension = locator.getSingleDimensionValue(IDENTITY);
    if (identityDimension != null) {
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          return identityDimension.equals(item.getBuildProblemData().getIdentity());
        }
      });
    }

    final String typeDimension = locator.getSingleDimensionValue("type");
    if (typeDimension != null) {
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          return typeDimension.equals(item.getBuildProblemData().getType());
        }
      });
    }

    if (locator.isUnused(BUILD)) {
      String buildDimension = locator.getSingleDimensionValue(BUILD);
      if (buildDimension != null) {
        List<BuildPromotion> builds = myBuildFinder.getBuilds(null, buildDimension).myEntries;
        result.add(new FilterConditionChecker<BuildProblem>() {
          public boolean isIncluded(@NotNull final BuildProblem item) {
            return builds.contains(item.getBuildPromotion());
          }
        });
      }
    }

    final String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null) {
      @NotNull final SProject project = myProjectFinder.getItem(affectedProjectDimension);
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          return ProjectFinder.isSameOrParent(project, myProjectFinder.getItem(item.getProjectId()));
        }
      });
    }

    final Boolean currentlyInvestigatedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_INVESTIGATED);
    if (currentlyInvestigatedDimension != null) {
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          //todo: check investigation in affected Project/buildType only, if set
          return FilterUtil.isIncludedByBooleanFilter(currentlyInvestigatedDimension,
                                                      !item.getAllResponsibilities().isEmpty());  //todo: TeamCity API (VM): what is the difference with   getResponsibility() ???
        }
      });
    }

    final Boolean currentlyMutedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_MUTED);
    if (currentlyMutedDimension != null) {
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          //todo: check in affected Project/buildType only, if set
          return FilterUtil.isIncludedByBooleanFilter(currentlyMutedDimension, item.getCurrentMuteInfo() != null);
        }
      });
    }

    final Boolean muteDimension = locator.getSingleDimensionValueAsBoolean(MUTED);
    if (muteDimension != null) {
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          return FilterUtil.isIncludedByBooleanFilter(muteDimension, item.getMuteInBuildInfo() != null);
        }
      });
    }


    final String currentDimension = locator.getSingleDimensionValue(CURRENT);
    if (currentDimension != null) {
      @NotNull final Set<Integer> currentBuildProblemsList = new TreeSet<Integer>();
      for (BuildProblem buildProblem : getCurrentProblemOccurences(null)) {
        currentBuildProblemsList.add(buildProblem.getId());
      }
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          return currentBuildProblemsList.contains(item.getId());
        }
      });
    }

    return result;
  }

  @NotNull
  private List<BuildProblem> getCurrentProblemOccurences(@Nullable SProject project) {
    if (project == null) {
      project = myProjectManager.getRootProject();
    }
    return myBuildProblemManager.getCurrentBuildProblemsList(project);
    /*
    final List<BuildProblem> currentBuildProblemsList = myBuildProblemManager.getCurrentBuildProblemsList(project);

    @NotNull final Set<BuildProblem> resultSet = new TreeSet<BuildProblem>();
    for (BuildProblem buildProblem : currentBuildProblemsList) {
      resultSet.add(buildProblem);
    }

    return new ArrayList<BuildProblem>(resultSet);
    */
  }

  @Nullable
  private static BuildProblem findProblem(@NotNull final BuildPromotion build, @NotNull final Long problemId) {
    final List<BuildProblem> buildProblems = getProblemOccurrences(build);
    for (BuildProblem buildProblem : buildProblems) {
      if (buildProblem.getId() == problemId.intValue()) {
        //todo: TeamCity API, JavaDoc (VB): add into the JavaDoc that problem with a given id can only occur once in a build
        return buildProblem;
      }
    }
    return null;
  }

  @NotNull
  private static ItemHolder<BuildProblem> getProblemOccurrences(@NotNull final Long problemId, @NotNull final ServiceLocator serviceLocator, @NotNull final BuildFinder buildFinder) {
    //todo: TeamCity API (VB): how to do this?
    final ArrayList<Long> buildIds = new ArrayList<Long>();
    try {
      //final SQLRunner sqlRunner = myServiceLocator.getSingletonService(SQLRunner.class);
      //workaround for http://youtrack.jetbrains.com/issue/TW-25260
      final SQLRunnerEx sqlRunner = serviceLocator.getSingletonService(BuildServerEx.class).getSQLRunner();
      sqlRunner.withDB(new DBActionNoResults() {
        public void run(final DBFunctions dbf) throws DBException {
          dbf.queryForTuples(new Object() {
            public void getBuildProblem(String build_state_id) throws IOException {
              try {
                //do nothing within database connection
                buildIds.add(Long.valueOf(build_state_id));
              } catch (NumberFormatException e) {
                LOG.infoAndDebugDetails("Non-number build promotion id " + build_state_id + " retrieved from the database for problemId: " + problemId + ", ignoring.", e);
              }
            }
          }, "getBuildProblem", "select build_state_id from build_problem where problem_id = " + problemId);
        }
      });

    } catch (Exception e) {
      throw new OperationException("Error performing database query: " + e.toString(), e);
    }

    return new ItemHolder<BuildProblem>() {
      @Override
      public boolean process(@NotNull final ItemProcessor<BuildProblem> processor) {
        for (Long buildId : buildIds) {
          try {
            final BuildPromotion buildByPromotionId = buildFinder.getBuildByPromotionId(Long.valueOf(buildId));
            if (buildByPromotionId.getBuildType() == null) {
              //missing build type, skip. Workaround for http://youtrack.jetbrains.com/issue/TW-34733
            } else {
              final BuildProblem problem = findProblem(buildByPromotionId, problemId);
              if (problem != null) {
                if (!processor.processItem(problem)) return false;
              }
            }
          } catch (RuntimeException e) {
            //addressing TW-41636
            LOG.infoAndDebugDetails("Error getting problems for build promotion with id " + buildId + ", problemId: " + problemId + ", ignoring. Cause", e);
          }
          }
        return true;
        }
    };
  }

  @NotNull
  public static List<BuildProblem> getProblemOccurrences(@NotNull final BuildPromotion buildPromotion) {
    return ((BuildPromotionEx)buildPromotion).getBuildProblems();
  }

  @NotNull
  private SProject getAffectedProject(@NotNull final Locator locator) {
    String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null) {
      return myProjectFinder.getItem(affectedProjectDimension);
    }else{
      return myProjectFinder.getRootProject();
    }
  }
}
