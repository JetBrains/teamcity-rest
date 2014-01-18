/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.build;

import java.util.*;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.problem.ProblemOccurrenceFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.*;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.agent.AgentRef;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeRef;
import jetbrains.buildServer.server.rest.model.buildType.PropEntitiesArtifactDep;
import jetbrains.buildServer.server.rest.model.change.Changes;
import jetbrains.buildServer.server.rest.model.change.Revisions;
import jetbrains.buildServer.server.rest.model.issue.IssueUsages;
import jetbrains.buildServer.server.rest.model.problem.ProblemOccurrences;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrences;
import jetbrains.buildServer.server.rest.model.user.UserRef;
import jetbrains.buildServer.server.rest.request.BuildQueueRequest;
import jetbrains.buildServer.server.rest.request.ChangeRequest;
import jetbrains.buildServer.server.rest.request.ProblemOccurrenceRequest;
import jetbrains.buildServer.server.rest.request.TestOccurrenceRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.Branch;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.buildDistribution.WaitReason;
import jetbrains.buildServer.serverSide.dependency.BuildDependency;
import jetbrains.buildServer.serverSide.impl.problems.BuildProblemImpl;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.serverSide.userChanges.CanceledInfo;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.VcsModificationHistory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "build")
/*Commens inside propOrder: q = queued, r = running, f = finished*/
@XmlType(name = "build",
         propOrder = {"id"/*rf*/, "promotionId"/*q*/, "buildTypeId", "number"/*rf*/, "status"/*rf*/, "state", "running"/*r*/, //"queued"/*q*/, "finished"/*f*/,
           "personal", "percentageComplete"/*r*/, "branchName", "defaultBranch", "unspecifiedBranch", "history", "pinned"/*rf*/, "href", "webUrl",
           "statusText"/*rf*/,
           "buildType", "comment", "tags"/*rf*/, "pinInfo"/*f*/, "personalBuildUser",
           "startEstimate"/*q*/, "waitReason"/*q*/,
           "runningBuildInfo"/*r*/, "canceledInfo"/*rf*/,
           "queuedDate", "startDate"/*rf*/, "finishDate"/*f*/,
           "triggered", "lastChanges", "changes", "revisions",
           "agent", "compatibleAgents"/*q*/,
           "testOccurrences"/*rf*/, "problemOccurrences"/*rf*/,
           "artifacts"/*rf*/, "issues"/*rf*/,
           "properties", "customProperties", "attributes",
           "buildDependencies", "buildArtifactDependencies", "customBuildArtifactDependencies"/*q*/,
           "triggeringOptions"/*only when triggering*/})
public class Build {
  public static final String CANCELED_INFO = "canceledInfo";
  public static final String PROMOTION_ID = "taskId";  //todo: rename this ???
  public static final String REST_BEANS_BUILD_INCLUDE_ALL_ATTRIBUTES = "rest.beans.build.includeAllAttributes";

  @NotNull final protected BuildPromotion myBuildPromotion;
  @Nullable final protected SBuild myBuild;
  @Nullable final private SQueuedBuild myQueuedBuild;

  @NotNull final protected Fields myFields;
  @NotNull final private BeanContext myBeanContext;
  @NotNull final private ApiUrlBuilder myApiUrlBuilder;
  @Autowired @NotNull private DataProvider myDataProvider;
  @Autowired @NotNull private ServiceLocator myServiceLocator;
  @Autowired @NotNull private BeanFactory myFactory;

  @SuppressWarnings("ConstantConditions")
  public Build() {
    myBuildPromotion = null;
    myBuild = null;
    myQueuedBuild = null;

    myFields = null;
    myBeanContext = null;
    myApiUrlBuilder = null;
    myDataProvider = null;
    myServiceLocator = null;
    myFactory = null;
  }

  public Build(@NotNull final SBuild build, @NotNull final BeanContext beanContext) {
    this(build, Fields.LONG, beanContext);
  }

  public Build(@NotNull final SBuild build, @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    myBuild = build;
    myBuildPromotion = myBuild.getBuildPromotion();
    myQueuedBuild = myBuildPromotion.getQueuedBuild();

    myBeanContext = beanContext;
    myApiUrlBuilder = beanContext.getApiUrlBuilder();
    beanContext.autowire(this);
    myFields = fields;
  }

  public Build(@NotNull final BuildPromotion buildPromotion, @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    myBuildPromotion = buildPromotion;
    myBuild = myBuildPromotion.getAssociatedBuild();
    myQueuedBuild = myBuildPromotion.getQueuedBuild();

    myBeanContext = beanContext;
    myApiUrlBuilder = beanContext.getApiUrlBuilder();
    beanContext.autowire(this);
    myFields = fields;
  }

  @XmlAttribute
  public Long getId() {
    return myBuild == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("id", true), myBuild.getBuildId());
  }

  @XmlAttribute (name = PROMOTION_ID)
  public Long getPromotionId() {
    return myBuild != null ? null : ValueWithDefault.decideDefault(myFields.isIncluded(PROMOTION_ID, true), myBuildPromotion.getId());
  }

  @XmlAttribute
  public String getState() {
    if (!myFields.isIncluded("state", true, true)){
      return null;
    }
    if (myQueuedBuild != null) return "queued";
    if (myBuild != null && !myBuild.isFinished()) return "running";
    if (myBuild != null && myBuild.isFinished()) return "finished";
    return "unknown";
  }

  /*
  @XmlAttribute
  public Boolean isQueued() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("queued"), myQueuedBuild != null);
  }
  */

  @XmlAttribute
  public Boolean isRunning() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("running"), myBuild != null && !myBuild.isFinished());
  }

  /*
  @XmlAttribute
  public Boolean isFinished() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("finished"), myBuild != null && myBuild.isFinished());
  }
  */

  @XmlAttribute
  public String getNumber() {
    return myBuild == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("number", true), myBuild.getBuildNumber());
  }

  @XmlAttribute
  public String getHref() {
    String result = null;
    if (myBuild != null) {
      result = myBeanContext.getApiUrlBuilder().getHref(myBuild);
    } else if (myQueuedBuild != null) {
      result = myBeanContext.getApiUrlBuilder().getHref(myQueuedBuild);
    }
    return ValueWithDefault.decideDefault(myFields.isIncluded("href", true), result);
  }

  @XmlAttribute
  public String getStatus() {
    return myBuild == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("status", true), myBuild.getStatusDescriptor().getStatus().getText());
  }

  @XmlAttribute
  public Boolean isHistory() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("history"), myBuildPromotion.isOutOfChangesSequence());
  }

  @XmlAttribute
  public Boolean isPinned() {
    return myBuild == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("pinned"), myBuild.isPinned());
  }

  @XmlAttribute
  public String getBranchName() {
    Branch branch = myBuildPromotion.getBranch();
    return branch == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("branchName"), branch.getDisplayName());
  }

  @XmlAttribute
  public Boolean getDefaultBranch() {
    Branch branch = myBuildPromotion.getBranch();
    return branch == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("defaultBranch"), branch.isDefaultBranch());
  }

  @XmlAttribute
  public Boolean getUnspecifiedBranch() {
    Branch branch = myBuildPromotion.getBranch();
    if (branch == null) return null;
    final boolean result = Branch.UNSPECIFIED_BRANCH_NAME.equals(branch.getName());
    return ValueWithDefault.decideDefault(myFields.isIncluded("unspecifiedBranch"), result);
  }

  @XmlAttribute
  public Boolean isPersonal() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("personal"), myBuildPromotion.isPersonal());
  }

  @XmlAttribute
  public String getWebUrl() {
    String result = null;
    if (myBuild != null) {
      result = myDataProvider.getBuildUrl(myBuild);
    } else if (myQueuedBuild != null) {
      result = myServiceLocator.getSingletonService(WebLinks.class).getQueuedBuildUrl(myQueuedBuild);
    }
    return ValueWithDefault.decideDefault(myFields.isIncluded("webUrl", true), result);
  }

  @XmlAttribute
  public String getBuildTypeId() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("buildTypeId", true), myBuildPromotion.getBuildTypeExternalId());
  }

  @XmlElement
  public String getStatusText() {
    return myBuild == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("statusText", false, true), myBuild.getStatusDescriptor().getText());
  }

  @XmlElement(name = "agent")
  public AgentRef getAgent() {
    SBuildAgent agent = null;
    if (myBuild != null) {
      agent = myBuild.getAgent();
    } else if (myQueuedBuild != null) {
      agent = myQueuedBuild.getBuildAgent();
    }
    return agent == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("agent", false), new AgentRef(agent, myApiUrlBuilder));
  }

  @XmlElement(name = "buildType")
  public BuildTypeRef getBuildType() {
    final SBuildType buildType = myBuildPromotion.getBuildType();
    return buildType == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("buildType", false), new BuildTypeRef(buildType, myBeanContext));
  }

  @XmlElement
  public String getStartDate() {
    return myBuild == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("startDate", false), Util.formatTime(myBuild.getStartDate()));
  }

  @XmlElement
  public String getFinishDate() {
    return myBuild == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("finishDate", false), Util.formatTime(myBuild.getFinishDate()));
  }

  @XmlElement(defaultValue = "") //todo: remove comment
  public Comment getComment() {
    final jetbrains.buildServer.serverSide.comments.Comment comment = myBuildPromotion.getBuildComment();
    return comment == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("comment", false), new Comment(comment, myApiUrlBuilder));
  }

  @XmlElement
  public Tags getTags() {
    return myBuild == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("tags", false), new Tags(myBuild.getTags()));
  }

  @XmlElement(name = "pinInfo")
  public Comment getPinInfo() {  //TeamCity API: is only available for finished build, while isPinned is available for running
    if (myBuild == null || !myBuild.isFinished()) {
      return null;
    }
    SFinishedBuild finishedBuild = (SFinishedBuild)myBuild; //TeamCity API issue
    final jetbrains.buildServer.serverSide.comments.Comment pinComment = finishedBuild.getPinComment();
    if (pinComment == null) {
      return null;
    }
    return ValueWithDefault.decideDefault(myFields.isIncluded("pinInfo", false), new Comment(pinComment, myApiUrlBuilder));
  }

  @XmlElement
  public Properties getProperties() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("properties", false), new Properties(myBuildPromotion.getParameters()));
  }

  @XmlElement
  public Properties getCustomProperties() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("customProperties", false), new Properties(myBuildPromotion.getCustomParameters()));
  }

  @XmlElement
  public Properties getAttributes() {
    final Map<String, Object> buildAttributes = ((BuildPromotionEx)myBuildPromotion).getAttributes();
    final LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
    if (TeamCityProperties.getBoolean(REST_BEANS_BUILD_INCLUDE_ALL_ATTRIBUTES)) {
      for (Map.Entry<String, Object> attribute : buildAttributes.entrySet()) {
        result.put(attribute.getKey(), attribute.getValue().toString());
      }
    } else {
      final Object value = buildAttributes.get(BuildAttributes.CLEAN_SOURCES);
      if (value != null) {
        result.put(BuildAttributes.CLEAN_SOURCES, value.toString());
      }
    }
    return ValueWithDefault.decideDefault(myFields.isIncluded("attributes", false), new Properties(result));
  }

  @XmlAttribute
  public Integer getPercentageComplete() {
    if (myBuild == null || myBuild.isFinished()) {
      return null;
    }
    SRunningBuild runningBuild = (SRunningBuild)myBuild;
    return ValueWithDefault.decideDefault(myFields.isIncluded("percentageComplete"), runningBuild.getCompletedPercent());
  }

  @XmlElement(name = "running-info")
  public RunningBuildInfo getRunningBuildInfo() {
    if (myBuild == null) return null;
    SRunningBuild runningBuild = getRunningBuild(myBuild, myServiceLocator);
    if (runningBuild == null) return null;
    return ValueWithDefault.decideDefault(myFields.isIncluded("running-info", false), new RunningBuildInfo(runningBuild));
  }

  @Nullable
  public static SRunningBuild getRunningBuild(@NotNull final SBuild build, final ServiceLocator serviceLocator) {
    if (build.isFinished()) {
      return null;
    }
    return serviceLocator.getSingletonService(RunningBuildsManager.class).findRunningBuildById(build.getBuildId());
  }

  @XmlElement(name = "snapshot-dependencies")
  public Builds getBuildDependencies() {
    if (!myFields.isIncluded("snapshot-dependencies", false, true)) {
      return null;
    }
    return ValueWithDefault.decideDefault(myFields.isIncluded("snapshot-dependencies", false),
                                          new Builds(getBuildPromotions(myBuildPromotion.getDependencies()),
                                                     null,
                                                     myFields.getNestedField("snapshot-dependencies", Fields.NONE, Fields.LONG),
                                                     myBeanContext));
  }

  @XmlElement(name = "artifact-dependencies")
  public Builds getBuildArtifactDependencies() {
    if (myBuild == null) {
      //todo: support serving artifact dependencies for queued build, may be rename the node
      return null;
    }
    final Map<jetbrains.buildServer.Build, List<ArtifactInfo>> artifacts = myBuild.getDownloadedArtifacts().getArtifacts();
    List<BuildPromotion> builds = new ArrayList<BuildPromotion>(artifacts.size());
    for (jetbrains.buildServer.Build sourceBuild : artifacts.keySet()) {
      //TeamCity API: cast to SBuild?
      builds.add(((SBuild)sourceBuild).getBuildPromotion());
    }
    Collections.sort(builds, new BuildPromotionDependenciesComparator());
    return ValueWithDefault.decideDefault(myFields.isIncluded("artifact-dependencies", false),
                                          new Builds(builds, null,
                                                     myFields.getNestedField("artifact-dependencies", Fields.NONE, Fields.LONG),
                                                     myBeanContext));
  }

  /**
   * Specifies artifact dependencies to be used in the build _instead_ of those specified in the build type.
   */
  @XmlElement(name = "custom-artifact-dependencies")
  public PropEntitiesArtifactDep getCustomBuildArtifactDependencies() {
    if (myBuild != null) {
      //todo: support serving for the running/finished builds, via a link
      return null;
    }
    final List<SArtifactDependency> artifactDependencies = ((BuildPromotionEx)myBuildPromotion).getCustomArtifactDependencies(); //TeamCity API: cast
    return ValueWithDefault.decideDefault(myFields.isIncluded("custom-artifact-dependencies", false),
                                          new PropEntitiesArtifactDep(artifactDependencies, new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder)));
  }

  @XmlElement(name = "revisions")
  public Revisions getRevisions() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("revisions", false), new Revisions(myBuildPromotion.getRevisions(), myApiUrlBuilder));
  }

  @XmlElement(name = "lastChanges")
  public Changes getLastChanges() {
    if (!myFields.isIncluded("lastChanges", false, true)) {
      return null;
    }
    final List<SVcsModification> result = new ArrayList<SVcsModification>();
    final Long lastModificationId = myBuildPromotion.getLastModificationId();
    if (lastModificationId != null && lastModificationId != -1) {
      SVcsModification modification = myBeanContext.getSingletonService(VcsModificationHistory.class).findChangeById(lastModificationId);
      if (modification != null) {
        result.add(modification);
      }
    }
    result.addAll(myBuildPromotion.getPersonalChanges());
    return ValueWithDefault
      .decideDefault(myFields.isIncluded("lastChanges", false), new Changes(result, null, myFields.getNestedField("lastChanges", Fields.NONE, Fields.LONG), myBeanContext));
  }

  @XmlElement(name = "changes")
  public Changes getChanges() {
    if (!myFields.isIncluded("changes", false, true)) {
      return null;
    }
    final List<SVcsModification> changesInternal = ChangeFinder.getBuildChanges(myBuildPromotion);
    final String href;
    if (myBuild != null) {
      href = ChangeRequest.getBuildChangesHref(myBuild);
    } else {
      href = ChangeRequest.getChangesHref(myBuildPromotion);
    }
    return ValueWithDefault.decideDefault(myFields.isIncluded("changes", false),
                                          new Changes(changesInternal, new PagerData(href), myFields.getNestedField("changes"), myBeanContext));
  }

  @XmlElement(name = "triggered")
  public TriggeredBy getTriggered() {
    jetbrains.buildServer.serverSide.TriggeredBy triggeredBy = null;
    if (myBuild != null) {
      triggeredBy = myBuild.getTriggeredBy();
    } else if (myQueuedBuild != null) {
      triggeredBy = myQueuedBuild.getTriggeredBy();
    }
    return triggeredBy == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("triggered", false), new TriggeredBy(triggeredBy, myDataProvider, myApiUrlBuilder));
  }

  @XmlElement(name = "relatedIssues")
  public IssueUsages getIssues() {
    final boolean includeAllInline = TeamCityProperties.getBoolean("rest.beans.build.inlineRelatedIssues");
    return myBuild == null
           ? null
           : ValueWithDefault.decideDefault(myFields.isIncluded("relatedIssues", false),
                                            new IssueUsages(myBuild, myFields.getNestedField("relatedIssues", Fields.NONE, includeAllInline ? Fields.LONG : Fields.SHORT),
                                                            myBeanContext));
  }

  @XmlElement(name = "user")
  public UserRef getPersonalBuildUser() {
    final SUser owner = myBuildPromotion.getOwner();
    return owner == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("user", false), new UserRef(owner, myApiUrlBuilder));
  }

  @XmlElement(name = "artifacts")
  public Href getArtifacts() {
    if (myBuild == null) return null;
    final Href value = new Href(BuildArtifactsFinder.fileApiUrlBuilderForBuild(myApiUrlBuilder, myBuild, null).getChildrenHref(null));
    return ValueWithDefault.decideDefault(myFields.isIncluded("artifacts", false), value);
  }

  @XmlElement(name = "testOccurrences")
  public TestOccurrences getTestOccurrences() {
    if (myBuild == null) return null;
    return ValueWithDefault.decideDefault(myFields.isIncluded("testOccurrences", false),
                                          new ValueWithDefault.Value<TestOccurrences>() {
                                            @Nullable
                                            public TestOccurrences get() {
                                              final ShortStatistics statistics = myBuild.getShortStatistics();
                                              if (statistics.getAllTestCount() == 0) {
                                                return null;
                                              }

                                              final int mutedTestsCount =
                                                statistics.getFailedTestsIncludingMuted().size() - statistics.getFailedTestCount(); //TeamCity API: not effective
                                              final Fields testOccurrencesFields = myFields.getNestedField("testOccurrences");
                                              final List<STestRun> tests = ValueWithDefault.decideDefault(
                                                testOccurrencesFields.isIncluded("testOccurrence", false), myBuild.getFullStatistics().getAllTests());
                                              return new TestOccurrences(tests,
                                                                         statistics.getAllTestCount(),
                                                                         statistics.getPassedTestCount(),
                                                                         statistics.getFailedTestCount(),
                                                                         statistics.getNewFailedCount(),
                                                                         statistics.getIgnoredTestCount(),
                                                                         mutedTestsCount,
                                                                         TestOccurrenceRequest.getHref(myBuild),
                                                                         null,
                                                                         testOccurrencesFields, myBeanContext
                                              );
                                            }
                                          });
  }

  @XmlElement(name = "problemOccurrences")
  public ProblemOccurrences getProblemOccurrences() {
    if (myBuild == null) return null;
    return ValueWithDefault.decideDefault(myFields.isIncluded("problemOccurrences", false),
                                          new ValueWithDefault.Value<ProblemOccurrences>() {
                                            @Nullable
                                            public ProblemOccurrences get() {
                                              final List<BuildProblem> problemOccurrences = ProblemOccurrenceFinder.getProblemOccurrences(myBuild);
                                              if (problemOccurrences.size() == 0) return null;

                                              int newProblemsCount = 0;
                                              int mutedProblemsCount = 0;
                                              for (BuildProblem problem : problemOccurrences) {
                                                if (problem.isMutedInBuild()) mutedProblemsCount++;
                                                final Boolean isNew = ((BuildProblemImpl)problem).isNew();
                                                if (isNew != null && isNew) newProblemsCount++;
                                              }
                                              final Fields problemOccurrencesFields = myFields.getNestedField("problemOccurrences");
                                              final List<BuildProblem> problems = ValueWithDefault
                                                .decideDefault(problemOccurrencesFields.isIncluded("problemOccurrence", false), ProblemOccurrenceFinder.getProblemOccurrences(
                                                  myBuild));
                                              return new ProblemOccurrences(problems,
                                                                            problemOccurrences.size(),
                                                                            null,
                                                                            null,
                                                                            newProblemsCount,
                                                                            null,
                                                                            mutedProblemsCount,
                                                                            ProblemOccurrenceRequest.getHref(myBuild),
                                                                            null,
                                                                            problemOccurrencesFields, myBeanContext
                                              );
                                            }
                                          });
  }

  static List<SBuild> getBuilds(@NotNull Collection<? extends BuildDependency> dependencies) {
    List<SBuild> result = new ArrayList<SBuild>(dependencies.size());
    for (BuildDependency dependency : dependencies) {
      final SBuild dependOnBuild = dependency.getDependOn().getAssociatedBuild();
      if (dependOnBuild != null) {
        result.add(dependOnBuild);
      }
    }
    Collections.sort(result, new BuildDependenciesComparator());
    return result;
  }

  static List<BuildPromotion> getBuildPromotions(@NotNull Collection<? extends BuildDependency> dependencies) {
    List<BuildPromotion> result = new ArrayList<BuildPromotion>(dependencies.size());
    for (BuildDependency dependency : dependencies) {
      result.add(dependency.getDependOn());
    }
    Collections.sort(result, new BuildPromotionDependenciesComparator());
    return result;
  }

  private static class BuildDependenciesComparator implements Comparator<SBuild> {
    public int compare(final SBuild o1, final SBuild o2) {
      final int buildTypesCompare = o1.getBuildTypeId().compareTo(o2.getBuildTypeId());
      return buildTypesCompare != 0 ? buildTypesCompare : (int)(o1.getBuildId() - o2.getBuildId());
    }
  }

  private static class BuildPromotionDependenciesComparator implements Comparator<BuildPromotion> {
    public int compare(final BuildPromotion o1, final BuildPromotion o2) {
      final int buildTypesCompare = o1.getBuildTypeId().compareTo(o2.getBuildTypeId());
      return buildTypesCompare != 0 ? buildTypesCompare : (int)(o1.getId() - o2.getId());
    }
  }

  @XmlElement(name = CANCELED_INFO)
  public Comment getCanceledInfo() {  //TeamCity API: is only available for running or finished build, while isCanceled is available for queued
    return myBuild == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded(CANCELED_INFO, false), getCanceledComment(myBuild, myApiUrlBuilder, myServiceLocator));
  }


  @XmlElement
  public String getQueuedDate() {
    Date result = null;
    if (myBuild != null) {
      result = myBuild.getQueuedDate();
    } else if (myQueuedBuild != null) {
      result = myQueuedBuild.getWhenQueued();
    }
    return result == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("queuedDate", false), Util.formatTime(result));
  }

  @XmlElement(name = "compatibleAgents")
  public Href getCompatibleAgents() {
    return myQueuedBuild == null
           ? null
           : ValueWithDefault.decideDefault(myFields.isIncluded("compatibleAgents", false), new Href(BuildQueueRequest.getCompatibleAgentsHref(myQueuedBuild), myApiUrlBuilder));
  }

  @XmlElement
  public String getStartEstimate() {
    final Boolean include = myFields.isIncluded("startEstimate", false);
    if (myQueuedBuild == null || (include != null && !include)) return null;

    final BuildEstimates buildEstimates = myQueuedBuild.getBuildEstimates();
    if (buildEstimates == null) return null;

    final TimeInterval timeInterval = buildEstimates.getTimeInterval();
    if (timeInterval == null) return null;

    final TimePoint endPoint = timeInterval.getEndPoint();
    if (endPoint == null) return null;
    return ValueWithDefault.decideDefault(include, Util.formatTime(endPoint.getAbsoluteTime()));
  }

  @XmlElement
  public String getWaitReason() {
    final Boolean include = myFields.isIncluded("waitReason", false);
    if (myQueuedBuild == null || (include != null && !include)) return null;

    final BuildEstimates buildEstimates = myQueuedBuild.getBuildEstimates();
    if (buildEstimates == null) return null;

    final WaitReason waitReason = buildEstimates.getWaitReason();
    if (waitReason == null) return null;
    return ValueWithDefault.decideDefault(include, waitReason.getDescription());
  }

  public static Comment getCanceledComment(@NotNull final SBuild build, @NotNull final ApiUrlBuilder apiUrlBuilder, @NotNull final ServiceLocator serviceLocator) {
    final CanceledInfo canceledInfo = build.getCanceledInfo();
    if (canceledInfo == null) return null;

    User user = null;
    if (canceledInfo.isCanceledByUser()) {
      final Long userId = canceledInfo.getUserId();
      assert userId != null;
      user = serviceLocator.getSingletonService(UserModel.class).findUserById(userId);
    }
    return new Comment(user, new Date(canceledInfo.getCreatedAt()), canceledInfo.getComment(), apiUrlBuilder);
  }

  /**
   * This is used only when posting a link to the build
   */
  private Long submittedId;
  private Long submittedPromotionId;
  private String submittedLocator;

  public void setId(Long id) {
    submittedId = id;
  }

  public void setPromotionId(Long id) {
    submittedPromotionId = id;
  }

  /**
   * This is used only when posting a link to the build
   */
  @XmlAttribute
  public String getLocator() {
    return null;
  }

  public void setLocator(final String locator) {
    submittedLocator = locator;
  }

  @NotNull
  public BuildPromotion getFromPosted(@NotNull final BuildFinder buildFinder, @NotNull final QueuedBuildFinder queuedBuildFinder) {
    String locatorText;
    if (submittedLocator != null) {
      if (submittedPromotionId != null) {
        throw new BadRequestException("Both 'locator' and '" + QueuedBuildFinder.PROMOTION_ID + "' attributes are specified. Only one should be present.");
      }
      if (submittedId != null) {
        throw new BadRequestException("Both 'locator' and '" + BuildFinder.DIMENSION_ID + "' attributes are specified. Only one should be present.");
      }
      locatorText = submittedLocator;
    } else {
      final Locator locator = Locator.createEmptyLocator();
      if (submittedPromotionId != null) {
        locator.setDimension(QueuedBuildFinder.PROMOTION_ID, String.valueOf(submittedPromotionId));
      }
      if (submittedId != null) {
        locator.setDimension(BuildFinder.DIMENSION_ID, String.valueOf(submittedId));
      }
      if (locator.isEmpty()) {
        throw new BadRequestException("No build specified. Either '" + PROMOTION_ID + "', '" + BuildFinder.DIMENSION_ID + "' or 'locator' attributes should be present.");
      }

      locatorText = locator.getStringRepresentation();
    }

    BuildPromotion result;
    try {
      result = queuedBuildFinder.getItem(locatorText).getBuildPromotion();
    } catch (Exception e) {
      result = buildFinder.getBuild(null, locatorText).getBuildPromotion();
    }
    return result;
  }

  private BuildTriggeringOptions submittedTriggeringOptions;
  private String submittedBuildTypeId;
  private BuildTypeRef submittedBuildType;
  private Comment submittedComment;
  private Properties submittedCustomProperties;
  private String submittedBranchName;
  private Boolean submittedPersonal;
  private Changes submittedLastChanges;
  private Builds submittedBuildDependencies;
  private AgentRef submittedAgent;
  private PropEntitiesArtifactDep submittedCustomBuildArtifactDependencies;
  private Properties submittedAttributes;

  /**
   * Used only when posting for triggering a build
   *
   * @return
   */
  @XmlElement
  public BuildTriggeringOptions getTriggeringOptions() {
    return null;
  }

  public void setTriggeringOptions(final BuildTriggeringOptions submittedTriggeringOptions) {
    this.submittedTriggeringOptions = submittedTriggeringOptions;
  }

  public void setBuildTypeId(final String submittedBuildTypeId) {
    this.submittedBuildTypeId = submittedBuildTypeId;
  }

  public void setBuildType(final BuildTypeRef submittedBuildType) {
    this.submittedBuildType = submittedBuildType;
  }

  public void setComment(final Comment submittedComment) {
    this.submittedComment = submittedComment;
  }

  public void setCustomProperties(final Properties submittedCustomProperties) {
    this.submittedCustomProperties = submittedCustomProperties;
  }

  public void setBranchName(final String submittedBranchName) {
    this.submittedBranchName = submittedBranchName;
  }

  public void setPersonal(final Boolean submittedPersonal) {
    this.submittedPersonal = submittedPersonal;
  }

  public void setLastChanges(final Changes submittedLstChanges) {
    this.submittedLastChanges = submittedLstChanges;
  }

  public void setBuildDependencies(final Builds submittedBuildDependencies) {
    this.submittedBuildDependencies = submittedBuildDependencies;
  }

  public void setAgent(final AgentRef submittedAgent) {
    this.submittedAgent = submittedAgent;
  }

  public void setCustomBuildArtifactDependencies(final PropEntitiesArtifactDep submittedCustomBuildArtifactDependencies) {
    this.submittedCustomBuildArtifactDependencies = submittedCustomBuildArtifactDependencies;
  }

  public void setAttributes(final Properties submittedAttributes) {
    this.submittedAttributes = submittedAttributes;
  }

  private BuildPromotion getBuildToTrigger(@Nullable final SUser user, @NotNull final ServiceLocator serviceLocator) {
    SVcsModification changeToUse = null;
    SVcsModification personalChangeToUse = null;
    if (submittedLastChanges != null) {
      List<SVcsModification> lastChanges = submittedLastChanges.getChangesFromPosted(serviceLocator.getSingletonService(ChangeFinder.class));
      if (lastChanges.size() > 0) {
        boolean changeProcessed = false;
        boolean personalChangeProcessed = false;
        for (SVcsModification change : lastChanges) {
          if (!change.isPersonal()) {
            if (!changeProcessed) {
              changeToUse = change;
              changeProcessed = true;
            } else {
              throw new BadRequestException("Several non-personal changes are submitted, only one can be present");
            }
          } else {
            if (!personalChangeProcessed) {
              personalChangeToUse = change;
              personalChangeProcessed = true;
            } else {
              throw new BadRequestException("Several personal changes are submitted, only one can be present");
            }
          }
        }
      }
    }

    BuildCustomizer customizer =
      serviceLocator.getSingletonService(BuildCustomizerFactory.class).createBuildCustomizer(getSubmittedBuildType(serviceLocator, personalChangeToUse, user), user);
    if (changeToUse != null) {
      customizer.setChangesUpTo(changeToUse);
    }
    if (submittedComment != null) {
      if (submittedComment.text != null) {
        customizer.setBuildComment(submittedComment.text);
      } else {
        throw new BadRequestException("Submitted comment does not have 'text' set.");
      }
    }
    if (submittedCustomProperties != null) customizer.setParameters(submittedCustomProperties.getMap());

    if (submittedBranchName != null) customizer.setDesiredBranchName(submittedBranchName);
    if (submittedPersonal != null) customizer.setPersonal(submittedPersonal);
    if (submittedTriggeringOptions != null && submittedTriggeringOptions.cleanSources != null) customizer.setCleanSources(submittedTriggeringOptions.cleanSources);
    if (submittedTriggeringOptions != null && submittedTriggeringOptions.rebuildAllDependencies != null) {
      customizer.setRebuildDependencies(submittedTriggeringOptions.rebuildAllDependencies);
    }
    if (submittedBuildDependencies != null) {
      try {
        customizer.setSnapshotDependencyNodes(submittedBuildDependencies.getFromPosted(serviceLocator));
      } catch (IllegalArgumentException e) {
        throw new BadRequestException("Error trying to use specified snapshot dependencies: " + e.getMessage());
      }
    }
    if (submittedTriggeringOptions != null && submittedTriggeringOptions.rebuildDependencies != null) {
      customizer.setRebuildDependencies(CollectionsUtil.convertCollection(
        submittedTriggeringOptions.rebuildDependencies.getFromPosted(serviceLocator.getSingletonService(BuildTypeFinder.class)), new Converter<String, BuildTypeOrTemplate>() {
        public String createFrom(@NotNull final BuildTypeOrTemplate source) {
          if (!source.isBuildType()) {
            throw new BadRequestException("Template is specified instead of a build type. Template id: '" + source.getTemplate().getExternalId() + "'");
          }
          return source.getBuildType().getInternalId();
        }
      }));
    }
    if (submittedCustomBuildArtifactDependencies != null) {
      final List<SArtifactDependency> artifactDependencies = submittedCustomBuildArtifactDependencies.getFromPosted(serviceLocator);
      if (!artifactDependencies.isEmpty()) { //TeamCity API does not allow to set empty depenedencies list
        customizer.setArtifactDependencies(artifactDependencies); //todo: merge with build type dependencies
      }
    }
    if (submittedAttributes != null){
      if (TeamCityProperties.getBoolean(REST_BEANS_BUILD_INCLUDE_ALL_ATTRIBUTES)) {
        customizer.setAttributes(submittedAttributes.getMap());
      } else {
        final String cleanSources = submittedAttributes.getMap().get(BuildAttributes.CLEAN_SOURCES);
        if (cleanSources != null) {
          customizer.setCleanSources(Boolean.valueOf(cleanSources));
        }
      }
    }
    return customizer.createPromotion();
  }

  private SBuildType getSubmittedBuildType(@NotNull ServiceLocator serviceLocator, @Nullable final SVcsModification personalChange, @Nullable final SUser currentUser) {
    if (submittedBuildType == null) {
      if (submittedBuildTypeId == null) {
        throw new BadRequestException("No 'buildType' element in the posted entry.");
      }
      SBuildType buildType = serviceLocator.getSingletonService(ProjectManager.class).findBuildTypeByExternalId(submittedBuildTypeId);
      if (buildType == null) {
        throw new NotFoundException("No build type found by submitted id '" + submittedBuildTypeId + "'");
      }
      return buildType;
    }

    final BuildTypeOrTemplate buildTypeFromPosted = submittedBuildType.getBuildTypeFromPosted(serviceLocator.findSingletonService(BuildTypeFinder.class));
    if (!buildTypeFromPosted.isBuildType()) {
      throw new BadRequestException("Found template instead on build type. Only build types can run builds.");
    }

    final SBuildType regularBuildType = buildTypeFromPosted.getBuildType();
    if (personalChange == null) {
      return regularBuildType;
    }
    if (currentUser == null) {
      throw new BadRequestException("Cannot trigger a personal build while no current user is present. Please specify credentials of a valid and non-special user.");
    }
    return ((BuildTypeEx)regularBuildType).createPersonalBuildType(currentUser, personalChange.getId());
  }

  @Nullable
  private SBuildAgent getSubmittedAgent(@NotNull final AgentFinder agentFinder) {
    if (submittedAgent == null) {
      return null;
    }
    return submittedAgent.getAgentFromPosted(agentFinder);
  }

  @NotNull
  public SQueuedBuild triggerBuild(@Nullable final SUser user, @NotNull final ServiceLocator serviceLocator) {
    BuildPromotion buildToTrigger = getBuildToTrigger(user, serviceLocator);
    TriggeredByBuilder triggeredByBulder = new TriggeredByBuilder();
    if (user != null) {
      triggeredByBulder = new TriggeredByBuilder(user);
    }
    final SBuildAgent agent = getSubmittedAgent(serviceLocator.getSingletonService(AgentFinder.class));
    SQueuedBuild queuedBuild;
    if (agent != null) {
      queuedBuild = buildToTrigger.addToQueue(agent, triggeredByBulder.toString());
    } else {
      queuedBuild = buildToTrigger.addToQueue(triggeredByBulder.toString());
    }
    if (queuedBuild == null) {
      throw new InvalidStateException("Failed to add build for build type with id '" + buildToTrigger.getBuildTypeExternalId() + "' into the queue for unknown reason.");
    }
    if (submittedTriggeringOptions != null && submittedTriggeringOptions.queueAtTop != null && submittedTriggeringOptions.queueAtTop) {
      serviceLocator.getSingletonService(BuildQueue.class).moveTop(queuedBuild.getItemId());
    }
    return queuedBuild;
  }

  @Nullable
  public static String getFieldValue(@NotNull final BuildPromotion buildPromotion, @Nullable final String field, @NotNull final BeanContext beanContext) {
    final Build build = new Build(buildPromotion, Fields.ALL, beanContext);

    if ("number".equals(field)) {
      return build.getNumber();
    } else if ("status".equals(field)) {
      return build.getStatus();
    } else if ("id".equals(field)) {
      return String.valueOf(build.getId());
    } else if ("state".equals(field)) {
      return build.getState();
    } else if ("startEstimateDate".equals(field)) {
      return build.getStartEstimate();
    } else if ("percentageComplete".equals(field)) {
      return String.valueOf(build.getPercentageComplete());
    } else if ("personal".equals(field)) {
      return String.valueOf(build.isPersonal());
    } else if ("queuedDate".equals(field)) {
      return build.getQueuedDate();
    } else if ("startDate".equals(field)) {
      return build.getStartDate();
    } else if ("finishDate".equals(field)) {
      return build.getFinishDate();
    } else if ("buildTypeId".equals(field)) {
      return build.getBuildTypeId();
    } else if ("buildTypeInternalId".equals(field)) {
      return buildPromotion.getBuildTypeId();
    } else if ("branchName".equals(field)) {
      return build.getBranchName();
    } else if ("branch".equals(field)) {
      Branch branch = buildPromotion.getBranch();
      return branch == null ? "" : branch.getName();
    } else if ("defaultBranch".equals(field)) {
      return String.valueOf(build.getDefaultBranch());
    } else if ("unspecifiedBranch".equals(field)) {
      return String.valueOf(build.getUnspecifiedBranch());
    } else if (PROMOTION_ID.equals(field)) { //Experimental support only, this is not exposed in any other way
      return (String.valueOf(build.getPromotionId()));
    } else if ("modificationId".equals(field)) { //Experimental support only, this is not exposed in any other way
      return String.valueOf(buildPromotion.getLastModificationId());
    } else if ("commentText".equals(field)) { //Experimental support only
      final Comment comment = build.getComment();
      return comment == null ? null : comment.text;
    } else if ("collectChangesError".equals(field)) { //Experimental support only
      return ((BuildPromotionEx)buildPromotion).getCollectChangesError();
    } else if ("changesCollectingInProgress".equals(field)) { //Experimental support only
      return String.valueOf(((BuildPromotionEx)buildPromotion).isChangesCollectingInProgress());
    }
    throw new NotFoundException("Field '" + field + "' is not supported. Supported are: number, status, id, startDate, finishDate, buildTypeId, branchName.");
  }
}