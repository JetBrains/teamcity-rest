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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Comment;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.agent.AgentRef;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeRef;
import jetbrains.buildServer.server.rest.model.buildType.PropEntitiesArtifactDep;
import jetbrains.buildServer.server.rest.model.change.ChangesRef;
import jetbrains.buildServer.server.rest.model.change.Revisions;
import jetbrains.buildServer.server.rest.model.user.UserRef;
import jetbrains.buildServer.server.rest.request.BuildQueueRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.Branch;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.buildDistribution.WaitReason;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * User: Yegor Yarko
 */
@XmlRootElement(name = "queuedBuild")
@XmlType(name = "queuedBuild", propOrder = {"id", "href", "webUrl", "branchName", "defaultBranch", "unspecifiedBranch", "personal", "history", "buildType",
  "queuedDate", "agent", "compatibleAgents", "comment", "personalBuildUser", "properties", "buildDependencies", "buildArtifactDependencies", "changes", "startEstimate",
  "waitReason",
  "revisions", "triggered"})
public class QueuedBuild {
  @NotNull
  protected SQueuedBuild myBuild;
  @NotNull
  private DataProvider myDataProvider;
  private ApiUrlBuilder myApiUrlBuilder;
  @Autowired private BeanFactory myFactory;

  private ServiceLocator myServiceLocator;

  public QueuedBuild() {
  }

  public QueuedBuild(@NotNull final SQueuedBuild build,
                     @NotNull final DataProvider dataProvider,
                     final ApiUrlBuilder apiUrlBuilder,
                     @NotNull final ServiceLocator serviceLocator, final BeanFactory factory) {
    myBuild = build;
    myDataProvider = dataProvider;
    myApiUrlBuilder = apiUrlBuilder;
    myServiceLocator = serviceLocator;
    factory.autowire(this);
  }

  @XmlAttribute
  public long getId() {
    return myBuild.getBuildPromotion().getId();
  }

  @XmlAttribute
  public String getHref() {
    return myApiUrlBuilder.getHref(myBuild);
  }

  @XmlAttribute
  public boolean isHistory() {
    return myBuild.getBuildPromotion().isOutOfChangesSequence();
  }

  @XmlAttribute
  public String getBranchName() {
    Branch branch = myBuild.getBuildPromotion().getBranch();
    if (branch == null) {
      return null;
    }
    return branch.getDisplayName();
  }

  @XmlAttribute
  public Boolean getDefaultBranch() {
    Branch branch = myBuild.getBuildPromotion().getBranch();
    if (branch == null) {
      return null;
    }
    return branch.isDefaultBranch() ? Boolean.TRUE : null;
  }

  @XmlAttribute
  public Boolean getUnspecifiedBranch() {
    Branch branch = myBuild.getBuildPromotion().getBranch();
    if (branch == null) {
      return null;
    }
    return Branch.UNSPECIFIED_BRANCH_NAME.equals(branch.getName()) ? Boolean.TRUE : null;
  }

  @XmlAttribute
  public boolean isPersonal() {
    return myBuild.getBuildPromotion().isPersonal();
  }

  @XmlAttribute
  public String getWebUrl() {
    return myServiceLocator.getSingletonService(WebLinks.class).getQueuedBuildUrl(myBuild);
  }

  //todo: support assignment to agent pools
  @XmlElement(name = "agent")
  @Nullable
  public AgentRef getAgent() {
    final SBuildAgent buildAgent = myBuild.getBuildAgent();
    return buildAgent == null ? null : new AgentRef(buildAgent, myApiUrlBuilder);
  }

  @XmlElement(name = "compatibleAgents")
  public Href getCompatibleAgents() {
    return new Href(BuildQueueRequest.getCompatibleAgentsHref(myBuild), myApiUrlBuilder);
  }

  @XmlElement(name = "buildType")
  public BuildTypeRef getBuildType() {
    return new BuildTypeRef(myBuild.getBuildType(), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  @XmlElement
  public String getQueuedDate() {
    return Util.formatTime(myBuild.getWhenQueued());
  }

  @XmlElement
  public String getStartEstimate() {
    final BuildEstimates buildEstimates = myBuild.getBuildEstimates();
    if (buildEstimates == null) return null;
    final TimeInterval timeInterval = buildEstimates.getTimeInterval();
    if (timeInterval == null) return null;
    final TimePoint endPoint = timeInterval.getEndPoint();
    if (endPoint == null) return null;
    return Util.formatTime(endPoint.getAbsoluteTime());
  }

  @XmlElement
  public String getWaitReason() {
    final BuildEstimates buildEstimates = myBuild.getBuildEstimates();
    if (buildEstimates == null) return null;
    final WaitReason waitReason = buildEstimates.getWaitReason();
    if (waitReason == null) return null;
    return waitReason.getDescription();
  }

  @XmlElement(defaultValue = "")
  public Comment getComment() {
    final jetbrains.buildServer.serverSide.comments.Comment comment = myBuild.getBuildPromotion().getBuildComment();
    if (comment != null) {
      return new Comment(comment, myApiUrlBuilder);
    }
    return null;
  }

  @XmlElement
  public Properties getProperties() {
    return new Properties(myBuild.getBuildPromotion().getCustomParameters());
  }

  @XmlElement(name = "snapshot-dependencies")
  public Builds getBuildDependencies() {
    //todo: this returns only finished builds, need to also return queued builds
    final List<SBuild> builds = Build.getBuilds(myBuild.getBuildPromotion().getDependencies());
    return builds.size() > 0 ? new Builds(builds, myServiceLocator, null, myApiUrlBuilder) : null;
  }

  @XmlElement(name = "artifact-dependencies")
  public PropEntitiesArtifactDep getBuildArtifactDependencies() {
    final List<SArtifactDependency> artifactDependencies = myBuild.getBuildPromotion().getArtifactDependencies();
    return artifactDependencies.size() > 0 ? new PropEntitiesArtifactDep(artifactDependencies, new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder)) : null;
  }

  @XmlElement(name = "revisions")
  public Revisions getRevisions() {
    return new Revisions(myBuild.getBuildPromotion().getRevisions(), myApiUrlBuilder);
  }

  @XmlElement(name = "changes")
  public ChangesRef getChanges() {
    return new ChangesRef(myBuild.getBuildPromotion(), myApiUrlBuilder);
  }

  @XmlElement(name = "triggered")
  public TriggeredBy getTriggered() {
    final jetbrains.buildServer.serverSide.TriggeredBy triggeredBy = myBuild.getTriggeredBy();
    return triggeredBy != null ? new TriggeredBy(triggeredBy, myDataProvider, myApiUrlBuilder) : null;
  }

  @XmlElement(name = "user")
  public UserRef getPersonalBuildUser() {
    final SUser owner = myBuild.getBuildPromotion().getOwner();
    return owner == null ? null : new UserRef(owner, myApiUrlBuilder);
  }

  @Nullable
  public static String getFieldValue(@NotNull final SQueuedBuild build, @Nullable final String field) {
    if ("id".equals(field)) {
      return (new Long(build.getItemId())).toString();
    } else if ("queuedDate".equals(field)) {
      return Util.formatTime(build.getWhenQueued());
    } else if ("startEstimateDate".equals(field)) {
      final BuildEstimates buildEstimates = build.getBuildEstimates();
      if (buildEstimates == null) {
        return null;
      } else {
        final TimeInterval timeInterval = buildEstimates.getTimeInterval();
        if (timeInterval == null) {
          return null;
        }
        final TimePoint endPoint = timeInterval.getEndPoint();
        if (endPoint == null) {
          return null;
        }
        return Util.formatTime(endPoint.getAbsoluteTime());
      }
    } else if ("buildTypeId".equals(field)) {
      return (build.getBuildType().getExternalId());
    } else if ("buildTypeInternalId".equals(field)) {
      return (build.getBuildTypeId());
    } else if ("branchName".equals(field)) {
      Branch branch = build.getBuildPromotion().getBranch();
      return branch == null ? "" : branch.getDisplayName();
    } else if ("branch".equals(field)) {
      Branch branch = build.getBuildPromotion().getBranch();
      return branch == null ? "" : branch.getName();
    } else if ("defaultBranch".equals(field)) {
      Branch branch = build.getBuildPromotion().getBranch();
      return branch == null ? "" : String.valueOf(branch.isDefaultBranch());
    } else if ("unspecifiedBranch".equals(field)) {
      Branch branch = build.getBuildPromotion().getBranch();
      return branch == null ? "" : String.valueOf(Branch.UNSPECIFIED_BRANCH_NAME.equals(branch.getName()));
    } else if ("promotionId".equals(field)) { //Experimental support only, this is not exposed in any other way
      return (String.valueOf(build.getBuildPromotion().getId()));
    } else if ("modificationId".equals(field)) { //Experimental support only, this is not exposed in any other way
      return (String.valueOf(build.getBuildPromotion().getLastModificationId()));
    } else if ("commentText".equals(field)) { //Experimental support only
      final jetbrains.buildServer.serverSide.comments.Comment buildComment = build.getBuildPromotion().getBuildComment();
      return buildComment != null ? buildComment.getComment() : null;
    }
    throw new NotFoundException("Field '" + field + "' is not supported. Supported are: id, queuedDate, startEstimateDate, buildTypeId, branchName.");
  }
}