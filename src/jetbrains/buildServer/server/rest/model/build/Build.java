/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.AgentRestrictor;
import jetbrains.buildServer.AgentRestrictorType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.artifacts.RevisionRule;
import jetbrains.buildServer.artifacts.RevisionRules;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.build.TagFinder;
import jetbrains.buildServer.server.rest.data.problem.ProblemOccurrenceFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.*;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.agent.Agent;
import jetbrains.buildServer.server.rest.model.agent.Agents;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.model.buildType.PropEntitiesArtifactDep;
import jetbrains.buildServer.server.rest.model.change.Changes;
import jetbrains.buildServer.server.rest.model.change.Revision;
import jetbrains.buildServer.server.rest.model.change.Revisions;
import jetbrains.buildServer.server.rest.model.files.FileApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.files.Files;
import jetbrains.buildServer.server.rest.model.issue.IssueUsages;
import jetbrains.buildServer.server.rest.model.problem.ProblemOccurrences;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrences;
import jetbrains.buildServer.server.rest.model.user.User;
import jetbrains.buildServer.server.rest.request.*;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.Branch;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeFinder;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.buildDistribution.WaitReason;
import jetbrains.buildServer.serverSide.dependency.BuildDependency;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.impl.problems.BuildProblemImpl;
import jetbrains.buildServer.serverSide.metadata.BuildMetadataEntry;
import jetbrains.buildServer.serverSide.metadata.impl.MetadataStorageEx;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.serverSide.userChanges.CanceledInfo;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.VcsModificationHistory;
import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "build")
/*Comments inside propOrder: q = queued, r = running, f = finished*/
@XmlType(name = "build",
         propOrder = {"id"/*rf*/, "promotionId"/*q*/, "buildTypeId", "buildTypeInternalId", "number"/*rf*/, "status"/*rf*/, "state", "running"/*r*/, "composite"/*qrf*/,
           "failedToStart"/*f*/,
           "personal", "percentageComplete"/*r*/, "branchName", "defaultBranch", "unspecifiedBranch", "history", "pinned"/*rf*/, "href", "webUrl",
           "statusText"/*rf*/,
           "buildType", "comment", "tags", "pinInfo"/*f*/, "personalBuildUser",
           "startEstimate"/*q*/, "waitReason"/*q*/,
           "runningBuildInfo"/*r*/, "canceledInfo"/*rf*/,
           "queuedDate", "startDate"/*rf*/, "finishDate"/*f*/,
           "triggered", "lastChanges", "changes", "revisions", "versionedSettingsRevision",
           "agent", "compatibleAgents"/*q*/,
           "testOccurrences"/*rf*/, "problemOccurrences"/*rf*/,
           "artifacts"/*rf*/, "issues"/*rf*/,
           "properties", "resultingProperties", "attributes", "statistics", "metadata"/*rf*/,
           "buildDependencies", "buildArtifactDependencies", "customBuildArtifactDependencies"/*q*/,
           "settingsHash", "currentSettingsHash", "modificationId", "chainModificationId", "replacementIds",
           "triggeringOptions"/*only when triggering*/})
public class Build {
  private static final Logger LOG = Logger.getInstance(Build.class.getName());

  public static final String CANCELED_INFO = "canceledInfo";
  public static final String PROMOTION_ID = "taskId";
  public static final String REST_BEANS_BUILD_INCLUDE_ALL_ATTRIBUTES = "rest.beans.build.includeAllAttributes";

  @NotNull final protected BuildPromotion myBuildPromotion;
  @Nullable final protected SBuild myBuild;
  @Nullable final private SQueuedBuild myQueuedBuild;

  @NotNull final protected Fields myFields;
  @NotNull final private BeanContext myBeanContext;
  @NotNull private ServiceLocator myServiceLocator;

  @SuppressWarnings("ConstantConditions")
  public Build() {
    myBuildPromotion = null;
    myBuild = null;
    myQueuedBuild = null;

    myFields = null;
    myBeanContext = null;
    myServiceLocator = null;
  }

  public Build(@NotNull final SBuild build, @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    myBuild = build;
    myBuildPromotion = myBuild.getBuildPromotion();
    myQueuedBuild = null;

    myBeanContext = beanContext;
    myServiceLocator = beanContext.getServiceLocator();
    myFields = fields;
  }

  public Build(@NotNull final BuildPromotion buildPromotion, @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    myBuildPromotion = buildPromotion;
    myQueuedBuild = myBuildPromotion.getQueuedBuild();
    myBuild = myQueuedBuild != null ? null : myBuildPromotion.getAssociatedBuild();

    if (myQueuedBuild == null && myBuild == null) { //diagnostics for TW-41263
      final long currentId = myBuildPromotion.getId();
      final BuildPromotion replacement = beanContext.getSingletonService(BuildPromotionManager.class).findPromotionOrReplacement(currentId);
      if (replacement == null) {
        LOG.info("Promotion with id " + currentId + " was removed during request processing");
      } else if (replacement.getId() != currentId) {
        LOG.info("Promotion with id " + currentId + " was replaced by promotion with id " + replacement.getId() + " during request processing");
      }
    }

    myBeanContext = beanContext;
    myServiceLocator = beanContext.getServiceLocator();
    myFields = fields;
  }

  @XmlAttribute
  public Long getId() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("id", true), new ValueWithDefault.Value<Long>() {
      @Nullable
      public Long get() {
        // since 9.0 promotionId == buildId (apart from https://youtrack.jetbrains.com/issue/TW-38777), so assume so for queued builds
        return myBuild != null ? myBuild.getBuildId() : myBuildPromotion.getId();
      }
    });
  }

  @XmlAttribute (name = PROMOTION_ID)
  public Long getPromotionId() {
    return ValueWithDefault.decideDefault(myFields.isIncluded(PROMOTION_ID, false, false), myBuildPromotion.getId());
  }

  @XmlAttribute
  public String getState() {
    if (!myFields.isIncluded("state", true, true)){
      return null;
    }
    if (myQueuedBuild != null) return "queued";
    if (myBuild != null && !myBuild.isFinished()) return "running";
    //noinspection ConstantConditions
    if (myBuild != null && myBuild.isFinished()) return "finished";
    if (((BuildPromotionEx)myBuildPromotion).isDeleted()) return "deleted";
    return "unknown";
  }

  /**
   * @deprecated use "state" instead
   * @return
   */
  @XmlAttribute
  public Boolean isRunning() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("running"), () -> myBuild != null && !myBuild.isFinished());
  }

  /**
   * Experimental
   * "composite" build state (since TeamCty 2017.2)
   * TODO: to review before the release. May be this should turn into a node with more details like completion %
   */
  @XmlAttribute
  public Boolean isComposite() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("composite"), () -> ((BuildPromotionEx)myBuildPromotion).isCompositeBuild());
  }

  @XmlAttribute
  public Boolean isFailedToStart() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("failedToStart"), () -> myBuild != null && myBuild.isInternalError());
  }

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
    //todo: consider getting details from full statistics is that is required for the node as otherwise the text and test counts will be not in sync
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
    return ValueWithDefault.decideDefault(myFields.isIncluded("branchName"), () -> {
      Branch branch = myBuildPromotion.getBranch();
      return branch == null ? null : branch.getDisplayName();
    });
  }

  @XmlAttribute
  public Boolean getDefaultBranch() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("defaultBranch"), () -> {
      Branch branch = myBuildPromotion.getBranch();
      return branch == null ? null : branch.isDefaultBranch();
    });
  }

  @XmlAttribute
  public Boolean getUnspecifiedBranch() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("unspecifiedBranch"), () -> {
      Branch branch = myBuildPromotion.getBranch();
      if (branch == null) return null;
      return Branch.UNSPECIFIED_BRANCH_NAME.equals(branch.getName());
    });
  }

  @XmlAttribute
  public Boolean isPersonal() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("personal"), myBuildPromotion.isPersonal());
  }

  @XmlAttribute
  public String getWebUrl() {
    String result = null;
    if (myBuild != null) {
      result = myServiceLocator.getSingletonService(WebLinks.class).getViewResultsUrl(myBuild);
    } else if (myQueuedBuild != null) {
      result = myServiceLocator.getSingletonService(WebLinks.class).getQueuedBuildUrl(myQueuedBuild);
    }
    return ValueWithDefault.decideDefault(myFields.isIncluded("webUrl", true), result);
  }

  @XmlAttribute
  public String getBuildTypeId() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("buildTypeId", true), new ValueWithDefault.Value<String>() {
      @Nullable
      public String get() {
        final String buildTypeExternalId = myBuildPromotion.getBuildTypeExternalId();
        if (!BuildPromotion.NOT_EXISTING_BUILD_TYPE_ID.equals(buildTypeExternalId)) {
          return buildTypeExternalId;
        } else {
          return null;
        }
      }
    });
  }

  @XmlAttribute
  public String getBuildTypeInternalId() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("buildTypeInternalId", false, false), myBuildPromotion.getBuildTypeId());
  }

  @XmlElement
  public String getStatusText() {
    return myBuild == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("statusText", false, true), new ValueWithDefault.Value<String>() {
      public String get() {
        return myBuild.getStatusDescriptor().getText();
      }
    });
  }

  @XmlElement(name = "agent")
  public Agent getAgent() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("agent", false), new ValueWithDefault.Value<Agent>() {
      public Agent get() {
        if (myBuild != null) {
          SBuildAgent agent = myBuild.getAgent();
          return new Agent(agent, myBeanContext.getSingletonService(AgentPoolFinder.class), myFields.getNestedField("agent"), myBeanContext);
        }
        if (myQueuedBuild != null) {
          final AgentRestrictor agentRestrictor = myQueuedBuild.getAgentRestrictor();
          if (agentRestrictor != null) {
            if (agentRestrictor.getType() == AgentRestrictorType.SINGLE_AGENT) {
              SBuildAgent agent = myQueuedBuild.getBuildAgent();
              if (agent != null) {
                return new Agent(agent, myBeanContext.getSingletonService(AgentPoolFinder.class), myFields.getNestedField("agent"), myBeanContext);
              }
            }
            if (agentRestrictor.getType() == AgentRestrictorType.AGENT_POOL) {
              final int agentPoolId = agentRestrictor.getId();
              final AgentPool agentPool = myBeanContext.getSingletonService(AgentPoolFinder.class).getAgentPoolById(agentPoolId);
              return new Agent(agentPool, myFields.getNestedField("agent"), myBeanContext);
            }
            if (agentRestrictor.getType() == AgentRestrictorType.CLOUD_IMAGE) {
              final int agentTypeId = agentRestrictor.getId();
              final SAgentType agentType = AgentFinder.getAgentType(String.valueOf(agentTypeId), myBeanContext.getSingletonService(AgentTypeFinder.class));
              return new Agent(agentType, myFields.getNestedField("agent"), myBeanContext);
            }
          }
        }
        return null;
      }
    });
  }

  @XmlElement(name = "buildType")
  public BuildType getBuildType() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("buildType", false), new ValueWithDefault.Value<BuildType>() {
      public BuildType get() {
        final SBuildType buildType = myBuildPromotion.getParentBuildType();
        return buildType == null ? null : new BuildType(new BuildTypeOrTemplate(buildType), myFields.getNestedField("buildType"), myBeanContext);
      }
    });
  }

  @XmlElement
  public String getStartDate() { // consider adding myBuild.getServerStartDate()
    return myBuild == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("startDate", false), Util.formatTime(myBuild.getStartDate()));
  }

  @XmlElement
  public String getFinishDate() {
    return myBuild == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("finishDate", false), Util.formatTime(myBuild.getFinishDate()));
  }

  @XmlElement(defaultValue = "") //todo: remove comment
  public Comment getComment() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("comment", false), () -> {
      jetbrains.buildServer.serverSide.comments.Comment comment = myBuildPromotion.getBuildComment();
      return comment == null ? null : new Comment(comment, myFields.getNestedField("comment", Fields.NONE, Fields.LONG), myBeanContext);
    });
  }

  @XmlElement
  public Tags getTags() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("tags", false), new ValueWithDefault.Value<Tags>() {
      public Tags get() {
        final Fields fields = myFields.getNestedField("tags", Fields.NONE, Fields.LONG);
        final TagFinder tagFinder = new TagFinder(myBeanContext.getSingletonService(UserFinder.class), myBuildPromotion);
        return new Tags(tagFinder.getItems(fields.getLocator(), TagFinder.getDefaultLocator()).myEntries, fields, myBeanContext);
      }
    });
  }

  @XmlElement(name = "pinInfo")
  public Comment getPinInfo() {
    if (myBuild == null || !myBuild.isPinned() || !(myBuild instanceof SFinishedBuild)) {
      return null;
    }
    return ValueWithDefault.decideDefault(myFields.isIncluded("pinInfo", false), new ValueWithDefault.Value<Comment>() {
      public Comment get() {
        SFinishedBuild finishedBuild = (SFinishedBuild)myBuild;  //TeamCity API: getPinComment() is only available for finished build, while isPinned is available for running
        final jetbrains.buildServer.serverSide.comments.Comment pinComment = finishedBuild.getPinComment();
        if (pinComment == null) {
          return null;
        }
        return new Comment(pinComment, myFields.getNestedField("pinInfo", Fields.NONE, Fields.LONG), myBeanContext);
      }
    });
  }

  @XmlElement
  public Properties getProperties() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("properties", false), new ValueWithDefault.Value<Properties>() {
      public Properties get() {
        return new Properties(Properties.createEntity(myBuildPromotion.getParameters(), myBuildPromotion.getCustomParameters()), null,
                              null, myFields.getNestedField("properties", Fields.NONE, Fields.LONG), myServiceLocator);
      }
    });
  }

  /**
   * Experimental
   */
  @XmlElement
  public Properties getResultingProperties() {
    if (myBuild == null) {
      return null;
    }
    return ValueWithDefault.decideDefault(myFields.isIncluded("resultingProperties", false, false), new ValueWithDefault.Value<Properties>() {
      public Properties get() {
        try {
          //noinspection ConstantConditions
          myBeanContext.getServiceLocator().findSingletonService(PermissionChecker.class).checkPermission(Permission.VIEW_BUILD_RUNTIME_DATA, myBuildPromotion);
        } catch (Exception e) {
          return null;
        }
        return new Properties(myBuild.getParametersProvider().getAll(), null, myFields.getNestedField("resultingProperties", Fields.NONE, Fields.LONG), myServiceLocator);
      }
    });
  }

  @XmlElement
  public Entries getAttributes() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("attributes", false), new ValueWithDefault.Value<Entries>() {
      public Entries get() {
        final LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
        Fields nestedFields = myFields.getNestedField("attributes", Fields.LONG, Fields.LONG);
        String locator = ParameterCondition.getNameAndNotEmptyValueLocator(BuildAttributes.CLEAN_SOURCES);
        boolean supportCustomLocator = TeamCityProperties.getPropertyOrNull(REST_BEANS_BUILD_INCLUDE_ALL_ATTRIBUTES) == null;
        if (supportCustomLocator) {
          String locatorFromFields = nestedFields.getLocator();
          if (locatorFromFields != null) locator = locatorFromFields;
        } else if (TeamCityProperties.getBoolean(REST_BEANS_BUILD_INCLUDE_ALL_ATTRIBUTES)) {
          locator = null; //include all
        }
        final ParameterCondition parameterCondition = ParameterCondition.create(locator);
        final Map<String, Object> buildAttributes = ((BuildPromotionEx)myBuildPromotion).getAttributes();
        for (Map.Entry<String, Object> attribute : buildAttributes.entrySet()) {
          if (parameterCondition == null || parameterCondition.parameterMatches(new SimpleParameter(attribute.getKey(), attribute.getValue().toString()), null)) {
            result.put(attribute.getKey(), attribute.getValue().toString());
          }
        }
        return new Entries(result, nestedFields);
      }
    });
  }

  @XmlElement
  public Properties getStatistics() {
    if (myBuild == null) {
      return null;
    } else {
      final String statisticsHref = myBeanContext.getApiUrlBuilder().getHref(myBuild) + BuildRequest.STATISTICS;
        return ValueWithDefault.decideDefault(myFields.isIncluded("statistics", false), new ValueWithDefault.Value<Properties>() {
          public Properties get() {
            final Fields nestedField = myFields.getNestedField("statistics");
            return new Properties(nestedField.isMoreThenShort() ? getBuildStatisticsValues(myBuild) : null, //for performance reasons
                                  statisticsHref, nestedField, myServiceLocator);
          }
        });
    }
  }

  @XmlElement
  public NamedDatas getMetadata() {
    if (myBuild == null) {
      return null;
    } else {
      return ValueWithDefault.decideDefault(myFields.isIncluded("metadata", false, false), new ValueWithDefault.Value<NamedDatas>() {
        public NamedDatas get() {
          HashMap<String, Map<String, String>> result = new HashMap<>();
          MetadataStorageEx metadataStorage = myServiceLocator.getSingletonService(MetadataStorageEx.class);
          for (String providerId : metadataStorage.getProviderIds()) {
            Iterator<BuildMetadataEntry> metadataEntryIterator = metadataStorage.getBuildEntry(myBuild.getBuildId(), providerId);
            while (metadataEntryIterator.hasNext()) {
              BuildMetadataEntry metadataEntry = metadataEntryIterator.next();
              HashMap<String, String> properties = new HashMap<>(metadataEntry.getMetadata());
              if (properties.get(".providerId") == null) {
                properties.put(".providerId", providerId);
              } else {
                properties.put(".teamcity.rest.providerId", providerId); // assume clash here does not happen
              }
              if (properties.get(".key") == null) {
                properties.put(".key", metadataEntry.getKey());
              } else {
                properties.put(".teamcity.rest.key", metadataEntry.getKey());  // assume clash here does not happen
              }
              result.put(providerId + "_" + metadataEntry.getKey(), properties);
            }
          }
          return new NamedDatas(result, myFields.getNestedField("metadata"));
        }
      });
    }
  }

  @NotNull
  public static Map<String, String> getBuildStatisticsValues(@NotNull final SBuild build) {
    final Map<String, BigDecimal> values = build.getStatisticValues();

    final Map<String, String> result = new HashMap<String, String>(values.size());
    for (Map.Entry<String, BigDecimal> entry : values.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      result.put(entry.getKey(), entry.getValue().stripTrailingZeros().toPlainString());
    }

    return result;
  }

  @NotNull
  public static String getBuildStatisticValue(@NotNull final SBuild build, @NotNull final String statisticValueName) {
    Map<String, String> stats = getBuildStatisticsValues(build);
    String val = stats.get(statisticValueName);
    if (val != null) {
      return val;
    }
    BigDecimal directValue = build.getStatisticValue(statisticValueName); //TeamCity API issue: this can actually provide a value which is not returned in the list
    if (directValue != null) {
      return directValue.stripTrailingZeros().toPlainString();
    }
    throw new NotFoundException("No statistics data for key: " + statisticValueName + "' in build " + LogUtil.describe(build));
  }

  /**
   * @deprecated See @getRunningBuildInfo
   */
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
    SRunningBuild runningBuild = getRunningBuild(myBuildPromotion, myServiceLocator);
    if (runningBuild == null) return null;
    return ValueWithDefault.decideDefault(myFields.isIncluded("running-info", false), new RunningBuildInfo(runningBuild, myFields.getNestedField("running-info")));
  }

  @Nullable
  public static SRunningBuild getRunningBuild(@NotNull final BuildPromotion buildPromotion, final ServiceLocator serviceLocator) {
    final SBuild build = buildPromotion.getAssociatedBuild();
    if (build == null || build.isFinished()) {
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
                                          Builds.createFromBuildPromotions(getBuildPromotions(myBuildPromotion.getDependencies()),
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
    return ValueWithDefault.decideDefault(myFields.isIncluded("artifact-dependencies", false), new ValueWithDefault.Value<Builds>() {
      public Builds get() {
        final Map<jetbrains.buildServer.Build, List<ArtifactInfo>> artifacts = myBuild.getDownloadedArtifacts().getArtifacts();
        final List<BuildPromotion> builds = new ArrayList<BuildPromotion>(artifacts.size());
        for (jetbrains.buildServer.Build sourceBuild : artifacts.keySet()) {
          //TeamCity API: cast to SBuild?
          builds.add(((SBuild)sourceBuild).getBuildPromotion());
        }
        Collections.sort(builds, new BuildPromotionDependenciesComparator());
        return Builds.createFromBuildPromotions(builds, null,
                          myFields.getNestedField("artifact-dependencies", Fields.NONE, Fields.LONG),
                          myBeanContext);
      }
    });
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
    return ValueWithDefault.decideDefault(myFields.isIncluded("custom-artifact-dependencies", false), new ValueWithDefault.Value<PropEntitiesArtifactDep>() {
      public PropEntitiesArtifactDep get() {
        final List<SArtifactDependency> artifactDependencies = ((BuildPromotionEx)myBuildPromotion).getCustomArtifactDependencies(); //TeamCity API: cast
        return new PropEntitiesArtifactDep(artifactDependencies, null, myFields.getNestedField("custom-artifact-dependencies", Fields.NONE, Fields.LONG), myBeanContext);
      }
    });
  }

  @XmlElement(name = "revisions")
  public Revisions getRevisions() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("revisions", false), new ValueWithDefault.Value<Revisions>() {
      public Revisions get() {
        return new Revisions(myBuildPromotion.getRevisions(), myFields.getNestedField("revisions", Fields.NONE, Fields.LONG), myBeanContext);
      }
    });
  }

  @XmlElement(name = "versionedSettingsRevision")
  public Revision getVersionedSettingsRevision() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("versionedSettingsRevision", false), new ValueWithDefault.Value<Revision>() {
      public Revision get() {
        List<BuildRevision> revisions =
          CollectionsUtil.filterAndConvertCollection(((BuildPromotionEx)myBuildPromotion).getAllRevisionsMap().values(), source -> source, data -> data.isSettingsRevision());
        if (revisions.isEmpty()) {
          return null;
        }
        if (revisions.size() > 1) {
          LOG.warn("Found more then one versioned settings revision for " + LogUtil.describe(myBuildPromotion));
        }
        return new Revision(revisions.get(0), myFields.getNestedField("versionedSettingsRevision", Fields.NONE, Fields.LONG), myBeanContext);
      }
    });
  }

  /**
   * Lists last change(s) included into the build so that this can be used in the build start request. The changes correspond to the revisions used by the build.
   * The set of the changes included can vary in the future TeamCity versions. In TeamCity 8.1 this is the last usual change and also a personal change (for personal build only)
   * @return
   */
  @XmlElement(name = "lastChanges")
  public Changes getLastChanges() {
    if (!myFields.isIncluded("lastChanges", false, true)) {
      return null;
    }
    return ValueWithDefault.decideDefault(myFields.isIncluded("lastChanges", false), new ValueWithDefault.Value<Changes>() {
      public Changes get() {
        final Changes result = new Changes(null, myFields.getNestedField("lastChanges", Fields.NONE, Fields.LONG), myBeanContext, new CachingValue<List<SVcsModification>>() {
          @NotNull
          @Override
          protected List<SVcsModification> doGet() {
            final List<SVcsModification> result = new ArrayList<SVcsModification>();
            final Long lastModificationId = myBuildPromotion.getLastModificationId();
            if (lastModificationId != null && lastModificationId != -1) {
              try {
                SVcsModification modification = myBeanContext.getSingletonService(VcsModificationHistory.class).findChangeById(lastModificationId);
                if (modification != null && modification.getRelatedConfigurations().contains(myBuildPromotion.getParentBuildType())) {
                  result.add(modification);
                }
              } catch (AccessDeniedException e) {
                //ignore: the associated modification id probably does not belong to the build configuration (related to TW-35390)
              }
            }
            result.addAll(myBuildPromotion.getPersonalChanges());
            return result;
          }
        });
        return result.isDefault() ? null : result;
      }
    });
  }

  @XmlElement(name = "changes")
  public Changes getChanges() {
    if (!myFields.isIncluded("changes", false, true)) {
      return null;
    }
    return ValueWithDefault.decide(myFields.isIncluded("changes", false), new ValueWithDefault.Value<Changes>() {
      public Changes get() {
        final String href = ChangeRequest.getChangesHref(myBuildPromotion);
        final Fields changesFields = myFields.getNestedField("changes");
        return new Changes(new PagerData(href), changesFields, myBeanContext, new CachingValue<List<SVcsModification>>() {
          @NotNull
          @Override
          protected List<SVcsModification> doGet() {
            return ChangeFinder.getBuildChanges(myBuildPromotion);
          }
        });
      }
    }, null, true);
  }

  @XmlElement(name = "triggered")
  public TriggeredBy getTriggered() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("triggered", false), new ValueWithDefault.Value<TriggeredBy>() {
      public TriggeredBy get() {
        jetbrains.buildServer.serverSide.TriggeredBy triggeredBy = null;
        if (myBuild != null) {
          triggeredBy = myBuild.getTriggeredBy();
        } else if (myQueuedBuild != null) {
          triggeredBy = myQueuedBuild.getTriggeredBy();
        }
        return triggeredBy == null ? null : new TriggeredBy(triggeredBy, myFields.getNestedField("triggered", Fields.NONE, Fields.LONG),myBeanContext);
      }
    });
  }

  @XmlElement(name = "relatedIssues")
  public IssueUsages getIssues() {
    return myBuild == null
           ? null
           : ValueWithDefault.decideDefault(myFields.isIncluded("relatedIssues", false), new ValueWithDefault.Value<IssueUsages>() {
             public IssueUsages get() {
               final boolean includeAllInline = TeamCityProperties.getBoolean("rest.beans.build.inlineRelatedIssues");
               return new IssueUsages(myBuild, myFields.getNestedField("relatedIssues", Fields.NONE, includeAllInline ? Fields.LONG : Fields.SHORT), myBeanContext);
             }
           });
  }

  @XmlElement(name = "user")
  public jetbrains.buildServer.server.rest.model.user.User getPersonalBuildUser() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("user", false), new ValueWithDefault.Value<jetbrains.buildServer.server.rest.model.user.User>() {
      public jetbrains.buildServer.server.rest.model.user.User get() {
        final SUser owner = myBuildPromotion.getOwner();
        return owner == null ? null : new User(owner, myFields.getNestedField("user"), myBeanContext);
      }
    });
  }

  @XmlElement(name = "artifacts")
  public Files getArtifacts() {
    return ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("artifacts", false), new ValueWithDefault.Value<Files>() {
      @Nullable
      public Files get() {
        final Fields nestedFields = myFields.getNestedField("artifacts");
        final FileApiUrlBuilder builder = FilesSubResource.fileApiUrlBuilder(nestedFields.getLocator(), BuildRequest.getArtifactsUrlPrefix(myBuildPromotion, myBeanContext));
        return new Files(builder.getChildrenHref(null), new ValueWithDefault.Value<List<? extends Element>>() {
          @Nullable
          public List<? extends Element> get() {
            return BuildArtifactsFinder.getItems(BuildArtifactsFinder.getArtifactElement(myBuildPromotion, "", myServiceLocator), nestedFields.getLocator(), builder, myServiceLocator);
          }
        }, builder, nestedFields, myBeanContext);
      }
    });
  }

  @XmlElement(name = "testOccurrences")
  public TestOccurrences getTestOccurrences() {
    if (myBuild == null) return null;
    return ValueWithDefault.decideDefault(myFields.isIncluded("testOccurrences", false),
                                          new ValueWithDefault.Value<TestOccurrences>() {
                                            @Nullable
                                            public TestOccurrences get() {
                                              final Fields testOccurrencesFields = myFields.getNestedField("testOccurrences");
                                              final Boolean testDetailsIncluded = TestOccurrences.isTestOccurrenceIncluded(testOccurrencesFields);
                                              final BuildStatistics fullStatistics = (testDetailsIncluded == null || testDetailsIncluded) ?
                                                                                     myBuild.getFullStatistics() : null;
                                              final ShortStatistics statistics = fullStatistics != null ? fullStatistics : myBuild.getShortStatistics();
                                              if (statistics.getAllTestCount() == 0) {
                                                return null;
                                              }

                                              final int mutedTestsCount =
                                                statistics.getFailedTestsIncludingMuted().size() - statistics.getFailedTestCount(); //TeamCity API: not effective
                                              if (myBuild.getBuildType() == null){
                                                //workaround for http://youtrack.jetbrains.com/issue/TW-34734
                                                return null;
                                              }
                                              final List<STestRun> tests = ValueWithDefault.decideDefault(
                                                testDetailsIncluded, new ValueWithDefault.Value<List<STestRun>>() {
                                                  public List<STestRun> get() {
                                                    return (fullStatistics != null ? fullStatistics : myBuild.getFullStatistics()).getAllTests();
                                                  }
                                                }
                                              );
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
    return ValueWithDefault.decideDefault(myFields.isIncluded("problemOccurrences", false),
                                          new ValueWithDefault.Value<ProblemOccurrences>() {
                                            @Nullable
                                            public ProblemOccurrences get() {
                                              final List<BuildProblem> problemOccurrences = ProblemOccurrenceFinder.getProblemOccurrences(myBuildPromotion);
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
                                                  myBuildPromotion));
                                              return new ProblemOccurrences(problems,
                                                                            problemOccurrences.size(),
                                                                            null,
                                                                            null,
                                                                            newProblemsCount,
                                                                            null,
                                                                            mutedProblemsCount,
                                                                            ProblemOccurrenceRequest.getHref(myBuildPromotion),
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

  public static class BuildPromotionDependenciesComparator implements Comparator<BuildPromotion> {
    public int compare(final BuildPromotion o1, final BuildPromotion o2) {
      final int buildTypesCompare = o1.getBuildTypeId().compareTo(o2.getBuildTypeId());
      return buildTypesCompare != 0 ? buildTypesCompare : (int)(o1.getId() - o2.getId());
    }
  }

  @XmlElement(name = CANCELED_INFO)
  public Comment getCanceledInfo() {  //TeamCity API: is only available for running or finished build, while isCanceled is available for queued
    return myBuild == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded(CANCELED_INFO, false), new ValueWithDefault.Value<Comment>() {
      public Comment get() {
        return getCanceledComment(myBuild, myFields.getNestedField(CANCELED_INFO, Fields.NONE, Fields.LONG), myBeanContext);
      }
    });
  }


  @XmlElement
  public String getQueuedDate() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("queuedDate", false), () -> {
      Date result = null;
      if (myBuild != null) {
        result = myBuild.getQueuedDate();
      } else if (myQueuedBuild != null) {
        result = myQueuedBuild.getWhenQueued();
      }
      return result == null ? null : Util.formatTime(result);
    });
  }

  @XmlElement(name = "compatibleAgents")
  public Agents getCompatibleAgents() {
    return myQueuedBuild == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("compatibleAgents", false, true), new ValueWithDefault.Value<Agents>() {
      public Agents get() {
        final Fields nestedFields = myFields.getNestedField("compatibleAgents");
        String  actualLocatorText = Locator.merge(nestedFields.getLocator(), AgentFinder.getCompatibleAgentsLocator(myBuildPromotion));
        return new Agents(actualLocatorText, new PagerData(AgentRequest.getItemsHref(actualLocatorText)), nestedFields, myBeanContext);
      }
    });
  }

  @XmlElement
  public String getStartEstimate() {
    final Boolean include = myFields.isIncluded("startEstimate", false);
    if (myQueuedBuild == null || (include != null && !include)) return null;
    return ValueWithDefault.decideDefault(include, new ValueWithDefault.Value<String>() {
      @Nullable
      public String get() {
        final BuildEstimates buildEstimates = myQueuedBuild.getBuildEstimates();
        if (buildEstimates == null) return null;

        final TimeInterval timeInterval = buildEstimates.getTimeInterval();
        if (timeInterval == null) return null;

        if (TeamCityProperties.getBoolean("rest.beans.build.startEstimate.legacyBehavior")) {
          //logic before https://youtrack.jetbrains.com/issue/TW-50824 fix as the fix goes to the bugfix update
          //this property support can be dropped in TeamCity 2017.2
          final TimePoint endPoint = timeInterval.getEndPoint();
          if (endPoint == null) return null;
          return Util.formatTime(endPoint.getAbsoluteTime());
        }
        TimePoint result = timeInterval.getStartPoint();
        if (result == TimePoint.NEVER) return null;
        return Util.formatTime(result.getAbsoluteTime());
      }
    });
  }

  @XmlElement
  public String getWaitReason() {
    final Boolean include = myFields.isIncluded("waitReason", false);
    if (myQueuedBuild == null || (include != null && !include)) return null;

    return ValueWithDefault.decideDefault(include, new ValueWithDefault.Value<String>() {
      @Nullable
      public String get() {
        final BuildEstimates buildEstimates = myQueuedBuild.getBuildEstimates();
        if (buildEstimates == null) return null;

        final WaitReason waitReason = buildEstimates.getWaitReason();
        if (waitReason == null) return null;
        return waitReason.getDescription();
      }
    });
  }

  /**
   * Experimental
   */
  @XmlElement
  public String getSettingsHash() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("settingsHash", false, false), new ValueWithDefault.Value<String>() {
      @Nullable
      public String get() {
        return new String(Hex.encodeHex(((BuildPromotionEx)myBuildPromotion).getSettingsDigest(false)));
      }
    });
  }

  /**
   * Experimental
   */
  @XmlElement
  public String getCurrentSettingsHash() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("currentSettingsHash", false, false), new ValueWithDefault.Value<String>() {
      @Nullable
      public String get() {
        return new String(Hex.encodeHex(((BuildPromotionEx)myBuildPromotion).getBuildSettings().getDigest()));
      }
    });
  }

  /**
   * Experimental
   */
  @XmlElement
  public String getModificationId() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("modificationId", false, false), new ValueWithDefault.Value<String>() {
      @Nullable
      public String get() {
        return String.valueOf(myBuildPromotion.getLastModificationId());
      }
    });
  }

  /**
   * Experimental
   */
  @XmlElement
  public String getChainModificationId() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("chainModificationId", false, false), new ValueWithDefault.Value<String>() {
      @Nullable
      public String get() {
        return String.valueOf(((BuildPromotionEx)myBuildPromotion).getChainModificationId());
      }
    });
  }

  /**
   * Experimental
   */
  @XmlElement
  public Items getReplacementIds() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("replacementIds", false, false), new ValueWithDefault.Value<Items>() {
      @Nullable
      public Items get() {
        final Collection<Long> replacementIds = myServiceLocator.getSingletonService(BuildPromotionReplacement.class).getOriginalPromotionIds(myBuildPromotion.getId());
        ArrayList<Long> sortedReplacemetIds = new ArrayList<Long>(replacementIds);
        Collections.sort(sortedReplacemetIds, Collections.reverseOrder());

        return new Items(CollectionsUtil.convertCollection(sortedReplacemetIds, new Converter<String, Long>() {
          @Override
          public String createFrom(@NotNull final Long source) {
            return String.valueOf(source);
          }
        }));
      }
    });
  }

  public static Comment getCanceledComment(@NotNull final SBuild build, @NotNull final Fields fields, @NotNull final BeanContext context) {
    final CanceledInfo canceledInfo = build.getCanceledInfo();
    if (canceledInfo == null) return null;

    jetbrains.buildServer.users.User user = null;
    if (canceledInfo.isCanceledByUser()) {
      final Long userId = canceledInfo.getUserId();
      assert userId != null;
      user = context.getSingletonService(UserModel.class).findUserById(userId);
    }
    return new Comment(user, new Date(canceledInfo.getCreatedAt()), canceledInfo.getComment(), fields, context);  //todo: returns wrong date after server restart!
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

  @Nullable
  public Long getPromotionIdOfSubmittedBuild() {
    return submittedPromotionId != null ? submittedPromotionId : submittedId;
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
  public BuildPromotion getFromPosted(@NotNull final BuildPromotionFinder buildFinder,
                                      @NotNull final Map<Long, Long> buildPromotionIdQueuedBuildsReplacements) {
    return buildFinder.getItem(getLocatorFromPosted(buildPromotionIdQueuedBuildsReplacements));
  }

  @NotNull
  public String getLocatorFromPosted(final @NotNull Map<Long, Long> buildPromotionIdQueuedBuildsReplacements) {
    String locatorText;
    if (submittedLocator != null) {
      if (submittedPromotionId != null) {
        throw new BadRequestException("Both 'locator' and '" + BuildPromotionFinder.PROMOTION_ID + "' attributes are specified. Only one should be present.");
      }
      if (submittedId != null) {
        throw new BadRequestException("Both 'locator' and '" + BuildPromotionFinder.DIMENSION_ID + "' attributes are specified. Only one should be present.");
      }
      locatorText = submittedLocator;
    } else {
      final Locator locator = Locator.createEmptyLocator();
      if (submittedPromotionId != null) {
        final Long replacementPromotionId = buildPromotionIdQueuedBuildsReplacements.get(submittedPromotionId);
        if (replacementPromotionId != null){
          locator.setDimension(BuildPromotionFinder.PROMOTION_ID, String.valueOf(replacementPromotionId));
        } else{
          locator.setDimension(BuildPromotionFinder.PROMOTION_ID, String.valueOf(submittedPromotionId));
        }
      }
      if (submittedId != null) {
        //assuming https://youtrack.jetbrains.com/issue/TW-38777 never takes place
        final Long replacementPromotionId = buildPromotionIdQueuedBuildsReplacements.get(submittedId);
        if (replacementPromotionId != null){
          locator.setDimension(BuildPromotionFinder.PROMOTION_ID, String.valueOf(replacementPromotionId));
        } else{
          locator.setDimension(BuildPromotionFinder.DIMENSION_ID, String.valueOf(submittedId));
        }
      }
      if (locator.isEmpty()) {
        throw new BadRequestException("No build specified. Either '" + BuildPromotionFinder.DIMENSION_ID + "' or 'locator' attributes should be present.");
      }

      locatorText = locator.getStringRepresentation();
    }
    return locatorText;
  }

  private BuildTriggeringOptions submittedTriggeringOptions;
  private String submittedBuildTypeId;
  private BuildType submittedBuildType;
  private Comment submittedComment;
  private Properties submittedProperties;
  private String submittedBranchName;
  private Boolean submittedPersonal;
  private Changes submittedLastChanges;
  private Builds submittedBuildDependencies;
  private Agent submittedAgent;
  //todo: add support for snapshot dependency options, probably with: private PropEntitiesSnapshotDep submittedCustomBuildSnapshotDependencies;
  private PropEntitiesArtifactDep submittedCustomBuildArtifactDependencies;
  private Builds submittedBuildArtifactDependencies;
  private Tags submittedTags;
  private Entries submittedAttributes;

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

  public void setBuildType(final BuildType submittedBuildType) {
    this.submittedBuildType = submittedBuildType;
  }

  public void setComment(final Comment submittedComment) {
    this.submittedComment = submittedComment;
  }

  public void setProperties(final Properties submittedProperties) {
    this.submittedProperties = submittedProperties;
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

  public void setAgent(final Agent submittedAgent) {
    this.submittedAgent = submittedAgent;
  }

  public void setCustomBuildArtifactDependencies(final PropEntitiesArtifactDep submittedCustomBuildArtifactDependencies) {
    this.submittedCustomBuildArtifactDependencies = submittedCustomBuildArtifactDependencies;
  }

  public void setBuildArtifactDependencies(final Builds submittedBuildArtifactDependencies) {
    this.submittedBuildArtifactDependencies = submittedBuildArtifactDependencies;
  }

  public void setAttributes(final Entries submittedAttributes) {
    this.submittedAttributes = submittedAttributes;
  }
  public void setTags(final Tags tags) {
    this.submittedTags = tags;
  }

  private BuildPromotion getBuildToTrigger(@Nullable final SUser user, @NotNull final ServiceLocator serviceLocator, @NotNull final Map<Long, Long> buildPromotionIdReplacements) {
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

    final SBuildType submittedBuildType = getSubmittedBuildType(serviceLocator, personalChangeToUse, user);
    final BuildCustomizer customizer = serviceLocator.getSingletonService(BuildCustomizerFactory.class).createBuildCustomizer(submittedBuildType, user);
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
    if (submittedProperties != null){
      customizer.setParameters(submittedProperties.getMap());
    }

    if (submittedBranchName != null) customizer.setDesiredBranchName(submittedBranchName);
    if (submittedPersonal != null) customizer.setPersonal(submittedPersonal);

    if (submittedBuildDependencies != null) {
      try {
        customizer.setSnapshotDependencyNodes(submittedBuildDependencies.getFromPosted(serviceLocator, buildPromotionIdReplacements));
      } catch (IllegalArgumentException e) {
        throw new BadRequestException("Error trying to use specified snapshot dependencies" + getRelatedBuildDescription() + ":" + e.getMessage());
      } catch (NotFoundException e) {
        throw new BadRequestException("Error searching for snapshot dependency" + getRelatedBuildDescription() + ": " + e.getMessage(), e);
      }
    }

    if (submittedTriggeringOptions != null) {
      if (submittedTriggeringOptions.cleanSources != null) {
        customizer.setCleanSources(submittedTriggeringOptions.cleanSources);
      }
      if (submittedTriggeringOptions.cleanSourcesInAllDependencies != null) {
        ((BuildCustomizerEx)customizer).setApplyCleanSourcesToDependencies(submittedTriggeringOptions.cleanSourcesInAllDependencies);
      }
      if (submittedTriggeringOptions.freezeSettings != null) {
        ((BuildCustomizerEx)customizer).setFreezeSettings(submittedTriggeringOptions.freezeSettings);
      }
      if (submittedTriggeringOptions.tagDependencies != null) {
        ((BuildCustomizerEx)customizer).setApplyTagsToDependencies(submittedTriggeringOptions.tagDependencies);
      }
      if (submittedTriggeringOptions.rebuildAllDependencies != null) {
        customizer.setRebuildDependencies(submittedTriggeringOptions.rebuildAllDependencies);
      }
      if (submittedTriggeringOptions.rebuildDependencies != null) {
        customizer.setRebuildDependencies(CollectionsUtil.convertCollection(
          submittedTriggeringOptions.rebuildDependencies.getFromPosted(serviceLocator.getSingletonService(BuildTypeFinder.class)), new Converter<String, BuildTypeOrTemplate>() {
          public String createFrom(@NotNull final BuildTypeOrTemplate source) {
            if (source.getBuildType() == null) {
              //noinspection ConstantConditions
              throw new BadRequestException("Template is specified instead of a build type. Template id: '" + source.getTemplate().getExternalId() + "'");
            }
            return source.getBuildType().getInternalId();
          }
        }));
      }
    }

    List<BuildPromotion> artifactDepsBuildsPosted = null;
    try {
      artifactDepsBuildsPosted = submittedBuildArtifactDependencies == null ? null : submittedBuildArtifactDependencies.getFromPosted(serviceLocator, buildPromotionIdReplacements);
    } catch (NotFoundException e) {
      throw new BadRequestException("Error searching for artifact dependency" + getRelatedBuildDescription() + ": " + e.getMessage(), e);
    }
    if (submittedCustomBuildArtifactDependencies != null) {
      //todo: investigate if OK: here new dependencies are created and set into the build. Custom run build dialog onthe contrary, sets artifact deps with the same IDs into the build
      final List<SArtifactDependency> customDeps = submittedCustomBuildArtifactDependencies.getFromPosted(submittedBuildType.getArtifactDependencies(), serviceLocator);
      if (artifactDepsBuildsPosted == null) {
        setDepsWithNullCheck(customizer, customDeps);
      } else {
        //patch with "artifact-dependencies"
        setDepsWithNullCheck(customizer, getBuildPatchedDeps(customDeps, true, serviceLocator, artifactDepsBuildsPosted));
      }
    } else {
      if (artifactDepsBuildsPosted != null) {
        //use "artifact-dependencies" as the only dependencies as this allows to repeat a build
        setDepsWithNullCheck(customizer, getBuildPatchedDeps(submittedBuildType.getArtifactDependencies(), false, serviceLocator, artifactDepsBuildsPosted));
      } else {
        //no artifact dependencies customizations necessary
      }
    }

    if (submittedTags != null){
        customizer.setTagDatas(new HashSet<TagData>(submittedTags.getFromPosted(serviceLocator.getSingletonService(UserFinder.class))));
    }
    if (submittedAttributes != null){
        customizer.setAttributes(submittedAttributes.getMap());
    }
    final BuildPromotion result;
    try {
      result = customizer.createPromotion();
    } catch (IllegalStateException e) {
      //IllegalStateException is thrown e.g. when we try to create a personal build in a build type which does not allow this
      throw new BadRequestException("Cannot trigger build: " + e.getMessage());
    }
    BuildTypeEx modifiedBuildType = getCustomizedSubmittedBuildType(serviceLocator);
    if (modifiedBuildType!= null) {
      //it's core's responsibility to check permissions here
      try {
        ((BuildPromotionEx)result).freezeSettings(modifiedBuildType, "rest");
      } catch (IOException e) {
        throw new OperationException("Error while freezing promotion settings", e); //include nested erorr or it can expose too much data?
      }
    }
    return result;
  }

  @NotNull
  private String getRelatedBuildDescription() {
    Long promotionId = getPromotionIdOfSubmittedBuild();
    return promotionId == null ? "" : " in the submitted build with id '" + promotionId + "'";
  }

  private void setDepsWithNullCheck(@NotNull final BuildCustomizer customizer, @Nullable final List<SArtifactDependency> newDeps) {
    if (newDeps == null || newDeps.isEmpty()) {
      throw new BadRequestException("Attempt to set empty artifact dependencies collection which is not supported by TeamCity API"); //TeamCity API
    }
    customizer.setArtifactDependencies(newDeps);
  }

  @NotNull
  private List<SArtifactDependency> getBuildPatchedDeps(@NotNull final List<SArtifactDependency> originalDeps,
                                                        final boolean useAllOriginalDeps,
                                                        final @NotNull ServiceLocator serviceLocator, @NotNull final List<BuildPromotion> artifactDepsBuilds) {
    List<SArtifactDependency> originalDepsCopy = useAllOriginalDeps ? new ArrayList<>(originalDeps) : null;
    Map<String, Integer> processedBuildsByBuildTypeExternalId = new HashMap<>();
    final ArtifactDependencyFactory artifactDependencyFactory = serviceLocator.getSingletonService(ArtifactDependencyFactory.class);
    List<SArtifactDependency> result = new ArrayList<>(artifactDepsBuilds.size());
    for (BuildPromotion source : artifactDepsBuilds) {
      Integer buildTypeProcessedBuilds = processedBuildsByBuildTypeExternalId.get(source.getBuildTypeExternalId());
      if (buildTypeProcessedBuilds == null) {
        buildTypeProcessedBuilds = 0;
      }
      final SArtifactDependency originalDep = getArtifactDependency(originalDeps, source.getBuildType(), buildTypeProcessedBuilds);
      processedBuildsByBuildTypeExternalId.put(source.getBuildTypeExternalId(), buildTypeProcessedBuilds + 1);
      final SBuild associatedBuild = source.getAssociatedBuild();
      final RevisionRule revisionRule = RevisionRules.newBuildIdRule(source.getId(), associatedBuild != null ? associatedBuild.getBuildNumber() : null);
      final SArtifactDependency dep = artifactDependencyFactory.createArtifactDependency(source.getBuildTypeExternalId(), originalDep.getSourcePaths(), revisionRule);
      dep.setCleanDestinationFolder(originalDep.isCleanDestinationFolder());
      result.add(dep);
      if (useAllOriginalDeps) {
        originalDepsCopy.remove(originalDep);
      }
    }
    if (useAllOriginalDeps && !originalDepsCopy.isEmpty()) {
      result.addAll(originalDepsCopy);
    }
    return result;
  }

  @NotNull
  private SArtifactDependency getArtifactDependency(@NotNull final List<SArtifactDependency> originalDeps, @NotNull final SBuildType sourceBuildType, final int index) {
    int processedBuildsOfSourceBuildType = 0;
    for (SArtifactDependency dependency : originalDeps) {
      String dependencyBuildTypeExternalId = dependency.getSourceExternalId();
      if (sourceBuildType.getExternalId().equals(dependencyBuildTypeExternalId)) {
        if (index == processedBuildsOfSourceBuildType) {
          return dependency;
        }
        processedBuildsOfSourceBuildType++;
      }
    }
    if (processedBuildsOfSourceBuildType == 0) {
      throw new BadRequestException("No artifact dependency on build type with id " + sourceBuildType.getExternalId() + " is found" + getRelatedBuildDescription() +
                                    ". Make sure it is present in the build type settings or submit it in 'custom-artifact-dependencies' node.");
    }
    throw new BadRequestException("Only " + processedBuildsOfSourceBuildType + " artifact dependencies on build type with id " + sourceBuildType.getExternalId() +
                                  " are found" + getRelatedBuildDescription() + ", but at least " + index + " builds of the build type are passed as artifact dependencies.");
  }

  /**
   *
   * @return null if the submitted build type does not contain any custom settings
   */
  @Nullable
  private BuildTypeEx getCustomizedSubmittedBuildType(@NotNull ServiceLocator serviceLocator) {
    if (submittedBuildType == null) {
      return null;
    }

    final BuildTypeOrTemplate customizedBuildTypeFromPosted = submittedBuildType.getCustomizedBuildTypeFromPosted(serviceLocator.findSingletonService(BuildTypeFinder.class), serviceLocator);
    if (customizedBuildTypeFromPosted == null) {
      return null;
    }

    return (BuildTypeEx)customizedBuildTypeFromPosted.getBuildType();
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
    final SBuildType regularBuildType = buildTypeFromPosted.getBuildType();
    if (regularBuildType == null) {
      throw new BadRequestException("Found template instead on build type. Only build types can run builds.");
    }

    if (personalChange == null) {
      return regularBuildType;
    }
    if (currentUser == null) {
      throw new BadRequestException("Cannot trigger a personal build while no current user is present. Please specify credentials of a valid and non-special user.");
    }
    return ((BuildTypeEx)regularBuildType).createPersonalBuildType(currentUser, personalChange.getId());
  }

  @NotNull
  public SQueuedBuild triggerBuild(@Nullable final SUser user, @NotNull final ServiceLocator serviceLocator, @NotNull final Map<Long, Long> buildPromotionIdReplacements) {
    BuildPromotion buildToTrigger = getBuildToTrigger(user, serviceLocator, buildPromotionIdReplacements);
    SQueuedBuild queuedBuild = triggerBuild((BuildPromotionEx)buildToTrigger, user,
                                            submittedAgent == null ? null : submittedAgent.getAgentRestrictor(serviceLocator)); //TeamCity API issue: cast
    if (queuedBuild == null) {
      throw new InvalidStateException("Failed to add build for build type with id '" + buildToTrigger.getBuildTypeExternalId() + "' into the queue for unknown reason.");
    }
    if (submittedTriggeringOptions != null && submittedTriggeringOptions.queueAtTop != null && submittedTriggeringOptions.queueAtTop) {
      serviceLocator.getSingletonService(BuildQueue.class).moveTop(queuedBuild.getItemId());
    }
    return queuedBuild;
  }

  @Nullable
  private SQueuedBuild triggerBuild(@NotNull final BuildPromotionEx buildToTrigger, @Nullable final SUser user, @Nullable final SAgentRestrictor agentRestrictor) {
    final BuildTypeEx bt = buildToTrigger.getBuildType();
    if (bt == null) return null;
    final String triggeredBy = getTriggeredBy(user);

    if (agentRestrictor != null) {
      return bt.addToQueue(agentRestrictor, buildToTrigger, triggeredBy);
    }

    return bt.addToQueue(buildToTrigger, triggeredBy);
  }

  private String getTriggeredBy(final @Nullable SUser user) {
    TriggeredByBuilder result;
    if (user != null) {
      result = new TriggeredByBuilder(user);
      result.addParameter(TriggeredByBuilder.TYPE_PARAM_NAME, "user");
    } else {
      result = new TriggeredByBuilder();
      result.addParameter(TriggeredByBuilder.TYPE_PARAM_NAME, "request");
    }
    result.addParameter("origin", "rest");
    return result.toString();
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
    } else if ("failedToStart".equals(field)) {
      return String.valueOf(build.isFailedToStart());
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
    } else if (PROMOTION_ID.equals(field) || "promotionId".equals(field)) { //Experimental support only
      return (String.valueOf(build.getPromotionId()));
    } else if ("modificationId".equals(field)) { //Experimental support only
      return String.valueOf(buildPromotion.getLastModificationId());
    } else if ("chainModificationId".equals(field)) { //Experimental support only
      return String.valueOf(((BuildPromotionEx)buildPromotion).getChainModificationId());
    } else if ("commentText".equals(field)) { //Experimental support only
      final Comment comment = build.getComment();
      return comment == null ? null : comment.text;
    } else if ("collectChangesError".equals(field)) { //Experimental support only
      return ((BuildPromotionEx)buildPromotion).getCollectChangesError();
    } else if ("changesCollectingInProgress".equals(field)) { //Experimental support only
      return String.valueOf(((BuildPromotionEx)buildPromotion).isChangesCollectingInProgress());
    } else if ("changeCollectingNeeded".equals(field)) { //Experimental support only
      return String.valueOf(((BuildPromotionEx)buildPromotion).isChangeCollectingNeeded());
    } else if ("settingsHash".equals(field)) { //Experimental support only to get settings digest
      return new String(Hex.encodeHex(((BuildPromotionEx)buildPromotion).getSettingsDigest(false)));
    } else if ("currentSettingsHash".equals(field)) { //Experimental support only to get settings digest
      return new String(Hex.encodeHex(((BuildPromotionEx)buildPromotion).getBuildSettings().getDigest()));
    }

    final SBuild associatedBuild = buildPromotion.getAssociatedBuild();
    final SQueuedBuild queuedBuild = buildPromotion.getQueuedBuild();

    if ("triggeredBy.username".equals(field)) { //Experimental support only
      if (associatedBuild != null) {
        final SUser user = associatedBuild.getTriggeredBy().getUser();
        return user == null ? null : user.getUsername();
      }
      if (queuedBuild != null) {
        final SUser user = queuedBuild.getTriggeredBy().getUser();
        return user == null ? null : user.getUsername();
      }
      return null;
    } else if ("triggeredBy.raw".equals(field)) { //Experimental support only
      if (associatedBuild != null) {
        return associatedBuild.getTriggeredBy().getRawTriggeredBy();
      }
      if (queuedBuild != null) {
        return queuedBuild.getTriggeredBy().getRawTriggeredBy();
      }
      return null;
    }

    throw new NotFoundException("Field '" + field + "' is not supported. Supported are: number, status, id, startDate, finishDate, buildTypeId, branchName.");
  }
}
