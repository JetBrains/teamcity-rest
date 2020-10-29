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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.messages.ErrorData;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.*;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.request.BuildRequest;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorDimensionDataType;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.db.DBActionNoResults;
import jetbrains.buildServer.serverSide.db.DBException;
import jetbrains.buildServer.serverSide.db.DBFunctions;
import jetbrains.buildServer.serverSide.db.SQLRunnerEx;
import jetbrains.buildServer.serverSide.impl.problems.BuildProblemImpl;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.serverSide.problems.BuildProblemManager;
import jetbrains.buildServer.util.ItemProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Yegor.Yarko
 *         Date: 18.11.13
 */
@LocatorResource(value = LocatorName.PROBLEM_OCCURRENCE,
    extraDimensions = {AbstractFinder.DIMENSION_LOOKUP_LIMIT, PagerData.START, PagerData.COUNT, AbstractFinder.DIMENSION_ITEM},
    description = "Represents a locator string for filtering ProblemOccurrence entities." +
        "\nExamples:" +
        "\n* `currentlyInvestigated:true` – find last 100 build problem occurrences which are being currently investigated." +
        "\n* `build:<buildLocator>` – find build problem occurrences under build found by buildLocator.")
public class ProblemOccurrenceFinder extends AbstractFinder<BuildProblem> {
  private static final Logger LOG = Logger.getInstance(ProblemOccurrenceFinder.class.getName());

  @LocatorDimension(value = "build", format = LocatorName.BUILD, notes = "Build locator.")
  private static final String BUILD = "build";
  @LocatorDimension("identity") private static final String IDENTITY = "identity";
  @LocatorDimension("type") public static final String TYPE = "type"; //type of the problem (value conditions are supported). Also experimentally supports "snapshotDependencyProblem:false" value
  @LocatorDimension(value = "currentlyFailing", dataType = LocatorDimensionDataType.BOOLEAN, notes = "Is currently failing.")
  private static final String CURRENT = "currentlyFailing"; //this problem occurrence is in the currently failing or, when "build" is present - latest build have the same problem
  @LocatorDimension("problem") private static final String PROBLEM = "problem";
  @LocatorDimension(value = "currentlyInvestigated", dataType = LocatorDimensionDataType.BOOLEAN, notes = "Is currently investigated.")
  public static final String CURRENTLY_INVESTIGATED = "currentlyInvestigated";
  @LocatorDimension(value = "muted", dataType = LocatorDimensionDataType.BOOLEAN, notes = "Has ever been muted.")
  public static final String MUTED = "muted";
  @LocatorDimension(value = "currentlyMuted", dataType = LocatorDimensionDataType.BOOLEAN, notes = "Is currently muted.")
  public static final String CURRENTLY_MUTED = "currentlyMuted";
  @LocatorDimension(value = "affectedProject", format = LocatorName.PROJECT, notes = "Project (direct or indirect parent) locator.")
  public static final String AFFECTED_PROJECT = "affectedProject";

  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final BuildFinder myBuildFinder;
  @NotNull private final ProblemFinder myProblemFinder;

  @NotNull private final BuildProblemManager myBuildProblemManager;
  @NotNull private final ProjectManager myProjectManager;
  @NotNull private final jetbrains.buildServer.ServiceLocator myServiceLocator;

  public ProblemOccurrenceFinder(@NotNull final ProjectFinder projectFinder,
                                 @NotNull final BuildFinder buildFinder,
                                 @NotNull final ProblemFinder problemFinder,
                                 @NotNull final BuildProblemManager buildProblemManager,
                                 @NotNull final ProjectManager projectManager,
                                 @NotNull final ServiceLocator serviceLocator) {
    super(PROBLEM, IDENTITY, TYPE, BUILD, AFFECTED_PROJECT, CURRENT, MUTED, CURRENTLY_MUTED, CURRENTLY_INVESTIGATED);
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

  public static String getProblemOccurrenceLocator(@NotNull final BuildProblem problem) {
    final SBuild build = problem.getBuildPromotion().getAssociatedBuild();
    if (build == null) {
      throw new InvalidStateException("Build problem with id '" + problem.getId() + "' does not have an associated build.");
    }
    return Locator.createEmptyLocator().setDimension(PROBLEM, ProblemFinder.getLocator(problem.getId())).setDimension(BUILD, BuildRequest
      .getBuildLocator(build)).getStringRepresentation();
  }

  public static String getProblemOccurrenceLocator(@NotNull final SBuild build) {
    return Locator.createEmptyLocator().setDimension(BUILD, BuildRequest.getBuildLocator(build)).getStringRepresentation();
  }

  public static String getProblemOccurrenceLocator(@NotNull final BuildPromotion buildPromotion) {
    return Locator.createEmptyLocator().setDimension(BUILD, BuildRequest.getBuildLocator(buildPromotion)).getStringRepresentation();
  }

  public static String getProblemOccurrenceLocator(@NotNull final ProblemWrapper problem) {
    return Locator.createEmptyLocator().setDimension(PROBLEM, ProblemFinder.getLocator(problem)).getStringRepresentation();
  }

  @Override
  public BuildProblem findSingleItem(@NotNull final Locator locator) {
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
  public ItemHolder<BuildProblem> getPrefilteredItems(@NotNull final Locator locator) {
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

    Boolean currentDimension = locator.lookupSingleDimensionValueAsBoolean(CURRENT);
    if (currentDimension != null && currentDimension) {
      locator.markUsed(Collections.singleton(CURRENT));
      return getItemHolder(getCurrentProblemOccurences(getAffectedProject(locator)));
    }

    String problemDimension = locator.getSingleDimensionValue(PROBLEM);
    if (problemDimension != null) {
      return getProblemOccurrences(myProblemFinder.getItems(problemDimension).myEntries);
    }

    Boolean currentlyMutedDimension = locator.lookupSingleDimensionValueAsBoolean(CURRENTLY_MUTED);
    if (currentlyMutedDimension != null && currentlyMutedDimension) {
      locator.markUsed(Collections.singleton(CURRENTLY_MUTED));
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
  public ItemFilter<BuildProblem> getFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<BuildProblem> result = new MultiCheckerFilter<BuildProblem>();

    if (locator.isUnused(PROBLEM)) {
      String problemDimension = locator.getSingleDimensionValue(PROBLEM);
      if (problemDimension != null) {
        final PagedSearchResult<ProblemWrapper> problems = myProblemFinder.getItems(problemDimension);
        final HashSet<Integer> problemIds = problems.myEntries.stream().map(problem -> problem.getId().intValue()).collect(Collectors.toCollection(HashSet::new));
        result.add(item -> problemIds.contains(item.getId()));
      }
    }

    final String identityDimension = locator.getSingleDimensionValue(IDENTITY);
    if (identityDimension != null) {
      result.add(item -> identityDimension.equals(item.getBuildProblemData().getIdentity()));
    }

    final String typeDimension = locator.getSingleDimensionValue(TYPE);
    if (typeDimension != null) {
      final Boolean snapshotepProblems = getSnapshotDepProblemValue(typeDimension);
      if (snapshotepProblems != null) {
        result.add(item -> FilterUtil.isIncludedByBooleanFilter(snapshotepProblems, ErrorData.isSnapshotDependencyError(item.getBuildProblemData().getType())));
      } else {
        ValueCondition valueCondition = ParameterCondition.createValueCondition(typeDimension);
        result.add(item -> valueCondition.matches(item.getBuildProblemData().getType()));
      }
    }

    if (locator.isUnused(BUILD)) {
      String buildDimension = locator.getSingleDimensionValue(BUILD);
      if (buildDimension != null) {
        List<BuildPromotion> builds = myBuildFinder.getBuilds(null, buildDimension).myEntries;
        result.add(item -> builds.contains(item.getBuildPromotion()));
      }
    }

    final String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null) {
      @NotNull final SProject project = myProjectFinder.getItem(affectedProjectDimension);
      result.add(item -> ProjectFinder.isSameOrParent(project, myProjectFinder.getItem(item.getProjectId())));
    }

    final Boolean currentlyInvestigatedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_INVESTIGATED);
    if (currentlyInvestigatedDimension != null) {
      result.add(item -> {
        //todo: check investigation in affected Project/buildType only, if set
        return FilterUtil.isIncludedByBooleanFilter(currentlyInvestigatedDimension,
                                                    !item.getAllResponsibilities().isEmpty());  //todo: TeamCity API (VM): what is the difference with   getResponsibility() ???
      });
    }

    if (locator.isUnused(CURRENTLY_MUTED)) {
      final Boolean currentlyMutedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_MUTED);
      if (currentlyMutedDimension != null) {
        //todo: check in affected Project/buildType only, if set
        result.add(item -> FilterUtil.isIncludedByBooleanFilter(currentlyMutedDimension, item.getCurrentMuteInfo() != null));
      }
    }

    final Boolean muteDimension = locator.getSingleDimensionValueAsBoolean(MUTED);
    if (muteDimension != null) {
      result.add(item -> FilterUtil.isIncludedByBooleanFilter(muteDimension, item.getMuteInBuildInfo() != null));
    }


    if (locator.isUnused(CURRENT)) {
      final Boolean currentDimension = locator.getSingleDimensionValueAsBoolean(CURRENT);
      if (currentDimension != null) {
        @NotNull final Set<BuildProblemId> currentBuildProblemsList = getCurrentProblemOccurences(null).stream().map(BuildProblemId::create).collect(Collectors.toSet());
        result.add(item -> FilterUtil.isIncludedByBooleanFilter(currentDimension, currentBuildProblemsList.contains(BuildProblemId.create(item))));
      }
    }

    return result;
  }

  @Nullable
  private Boolean getSnapshotDepProblemValue(@NotNull final String text) {
    //experimental support for "snapshotDependencyProblem:false" in type to filter out snapshot-dependency-related problems
    final Boolean snapshotepProblems;
    try {
      Locator snapshotDepProblemLocator = new Locator(text, "snapshotDependencyProblem");
      snapshotepProblems = snapshotDepProblemLocator.getSingleDimensionValueAsStrictBoolean("snapshotDependencyProblem", null);
      snapshotDepProblemLocator.checkLocatorFullyProcessed();
      return snapshotepProblems;
    } catch (LocatorProcessException ignore) {
      // not snapshot dep locator, try regular way
    }
    return null;
  }

  @NotNull
  private List<BuildProblem> getCurrentProblemOccurences(@Nullable SProject project) {
    if (project == null) {
      project = myProjectManager.getRootProject();
    }
    return fillIsNew(myBuildProblemManager.getCurrentBuildProblemsList(project), null);
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
    //todo: TeamCity API, JavaDoc (VB): add into the JavaDoc that problem with a given id can only occur once in a build
    return buildProblems.stream().filter(buildProblem -> buildProblem.getId() == problemId.intValue()).findFirst().orElse(null);
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
          }, "getBuildProblem", "select build_state_id from build_problem where problem_id = ? order by build_state_id desc", problemId);
        }
      });

    } catch (Exception e) {
      throw new OperationException("Error performing database query: " + e.toString(), e);
    }

    return new ItemHolder<BuildProblem>() {
      @Override
      public void process(@NotNull final ItemProcessor<BuildProblem> processor) {
        for (Long buildId : buildIds) {
          try {
            final BuildPromotion buildByPromotionId = buildFinder.getBuildByPromotionId(Long.valueOf(buildId));
            if (buildByPromotionId.getBuildType() == null) {
              //missing build type, skip. Workaround for http://youtrack.jetbrains.com/issue/TW-34733
            } else {
              final BuildProblem problem = findProblem(buildByPromotionId, problemId);
              if (problem != null) {
                processor.processItem(problem);
              }
            }
          } catch (RuntimeException e) {
            //addressing TW-41636
            LOG.infoAndDebugDetails("Error getting problems for build promotion with id " + buildId + ", problemId: " + problemId + ", ignoring. Cause", e);
          }
          }
        }
    };
  }

  @NotNull
  public static List<BuildProblem> getProblemOccurrences(@NotNull final BuildPromotion buildPromotion) {
    return fillIsNew(((BuildPromotionEx)buildPromotion).getBuildProblems(), buildPromotion);
  }

  @NotNull private static List<BuildProblem> fillIsNew(@NotNull List<BuildProblem> problems, @Nullable final BuildPromotion buildPromotion) {
    //partial workaround for https://youtrack.jetbrains.com/issue/TW-63846
    BuildProblemImpl.fillIsNew(buildPromotion, problems);
    return problems;
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

  @NotNull
  public BuildProblem getProblem(@NotNull final SBuild build, @NotNull final BuildProblemData problemData) {
    return getItem(Locator.createEmptyLocator().setDimension(BUILD, BuildRequest.getBuildLocator(build)).setDimension(IDENTITY, problemData.getIdentity()).getStringRepresentation());
  }

  private static class BuildProblemId {
    private int problemId;
    private String buildTypeInternalId;

    static BuildProblemId create(@NotNull BuildProblem bp) {
      BuildProblemId result = new BuildProblemId();
      result.problemId = bp.getId();
      result.buildTypeInternalId = bp.getBuildPromotion().getBuildTypeId();
      return result;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final BuildProblemId that = (BuildProblemId)o;

      if (problemId != that.problemId) return false;
      return buildTypeInternalId != null ? buildTypeInternalId.equals(that.buildTypeInternalId) : that.buildTypeInternalId == null;
    }

    @Override
    public int hashCode() {
      int result = problemId;
      result = 31 * result + (buildTypeInternalId != null ? buildTypeInternalId.hashCode() : 0);
      return result;
    }
  }
}
