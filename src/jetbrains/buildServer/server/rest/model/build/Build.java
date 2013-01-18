/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import jetbrains.buildServer.issueTracker.Issue;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.model.Comment;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.agent.AgentRef;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeRef;
import jetbrains.buildServer.server.rest.model.change.ChangesRef;
import jetbrains.buildServer.server.rest.model.change.Revisions;
import jetbrains.buildServer.server.rest.model.issue.IssueUsages;
import jetbrains.buildServer.server.rest.model.user.UserRef;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.Branch;
import jetbrains.buildServer.serverSide.dependency.BuildDependency;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
//todo: add changes
//todo: reuse fields code from DataProvider
@XmlRootElement(name = "build")
@XmlType(name = "build", propOrder = {"id", "number", "status", "href", "webUrl", "branchName", "defaultBranch", "unspecifiedBranch", "personal", "history", "pinned", "running",
  "runningBuildInfo", "statusText", "buildType", "startDate", "finishDate", "agent", "comment", "tags", "pinInfo", "personalBuildUser", "properties",
  "buildDependencies", "buildArtifactDependencies", "revisions", "triggered", "changes", "issues"})
public class Build {
  @NotNull
  protected SBuild myBuild;
  @NotNull
  private DataProvider myDataProvider;
  private ApiUrlBuilder myApiUrlBuilder;
  @Autowired BeanFactory myFactory;

  private ServiceLocator myServiceLocator;

  public Build() {
  }

  public Build(@NotNull final SBuild build,
               @NotNull final DataProvider dataProvider,
               final ApiUrlBuilder apiUrlBuilder,
               @NotNull final ServiceLocator serviceLocator, final BeanFactory myFactory) {
    myBuild = build;
    myDataProvider = dataProvider;
    myApiUrlBuilder = apiUrlBuilder;
    myServiceLocator = serviceLocator;
    myFactory.autowire(this);
  }

  @XmlAttribute
  public long getId() {
    return myBuild.getBuildId();
  }

  @XmlAttribute
  public String getNumber() {
    return myBuild.getBuildNumber();
  }

  @XmlAttribute
  public String getHref() {
    return myApiUrlBuilder.getHref(myBuild);
  }

  @XmlAttribute
  public String getStatus() {
    return myBuild.getStatusDescriptor().getStatus().getText();
  }

  @XmlAttribute
  public boolean isHistory() {
    return myBuild.isOutOfChangesSequence();
  }

  @XmlAttribute
  public boolean isPinned() {
    return myBuild.isPinned();
  }

  @XmlAttribute
  public String getBranchName() {
    Branch branch = myBuild.getBranch();
    if (branch == null){
      return null;
    }
    return branch.getDisplayName();
  }

  @XmlAttribute
  public Boolean getDefaultBranch() {
    Branch branch = myBuild.getBranch();
    if (branch == null){
      return null;
    }
    return branch.isDefaultBranch() ? Boolean.TRUE : null;
  }

  @XmlAttribute
  public Boolean getUnspecifiedBranch() {
    Branch branch = myBuild.getBranch();
    if (branch == null){
      return null;
    }
    return Branch.UNSPECIFIED_BRANCH_NAME.equals(branch.getName()) ? Boolean.TRUE : null;
  }

  @XmlAttribute
  public boolean isPersonal() {
    return myBuild.isPersonal();
  }

  @XmlAttribute
  public String getWebUrl() {
    return myDataProvider.getBuildUrl(myBuild);
  }

  @XmlElement
  public String getStatusText() {
    return myBuild.getStatusDescriptor().getText();
  }

  @XmlElement(name = "agent")
  public AgentRef getAgent() {
    final SBuildAgent agent = myDataProvider.findAgentByName(myBuild.getAgentName());
    if (agent == null) {
      return new AgentRef(myBuild.getAgentName());
    }
    return new AgentRef(agent, myApiUrlBuilder);
  }

  @XmlElement(name = "buildType")
  public BuildTypeRef getBuildType() {
    return new BuildTypeRef(myBuild.getBuildType(), myDataProvider, myApiUrlBuilder);
  }

  //todo: investigate common date formats approach
  @XmlElement
  public String getStartDate() {
    return Util.formatTime(myBuild.getStartDate());
  }

  @XmlElement
  public String getFinishDate() {
    return Util.formatTime(myBuild.getFinishDate());
  }

  @XmlElement(defaultValue = "")
  public Comment getComment() {
    final jetbrains.buildServer.serverSide.comments.Comment comment = myBuild.getBuildComment();
    if (comment != null) {
      return new Comment(comment, myApiUrlBuilder);
    }
    return null;
  }

  @XmlElement
  public Tags getTags() {
    return new Tags(myBuild.getTags());
  }

  @XmlElement(name = "pinInfo")
  public Comment getPinInfo(){
    if (!myBuild.isFinished()) {
      return null;
    }
    SFinishedBuild finishedBuild = (SFinishedBuild)myBuild; //todo: is this OK?
    final jetbrains.buildServer.serverSide.comments.Comment pinComment = finishedBuild.getPinComment();
    if (pinComment == null){
      return null;
    }
    return new Comment(pinComment, myApiUrlBuilder);
  }

  @XmlElement
  public Properties getProperties() {
    return new Properties(myBuild.getBuildPromotion().getParameters());
  }

  @XmlAttribute(name = "running")
  public Boolean getRunning() {
    if (myBuild.isFinished()) {
      return null;
    } else {
      return true;
    }
  }

  @XmlElement(name = "running-info")
  public RunningBuildInfo getRunningBuildInfo() {
    if (myBuild.isFinished()) {
      return null;
    }
    SRunningBuild runningBuild = myServiceLocator.getSingletonService(RunningBuildsManager.class).findRunningBuildById(myBuild.getBuildId());
    if (runningBuild == null){
      return null;
    }
    return new RunningBuildInfo(runningBuild);
  }

  @XmlElement(name = "snapshot-dependencies")
  public BuildsList getBuildDependencies() {
    return new BuildsList(getBuildRefs(myBuild.getBuildPromotion().getDependencies(), myDataProvider));
  }

  @XmlElement(name = "artifact-dependencies")
  public BuildsList getBuildArtifactDependencies() {
    final Map<jetbrains.buildServer.Build,List<ArtifactInfo>> artifacts = myBuild.getDownloadedArtifacts().getArtifacts();
    List<BuildRef> builds = new ArrayList<BuildRef>(artifacts.size());
    for (Map.Entry<jetbrains.buildServer.Build, List<ArtifactInfo>> entry : artifacts.entrySet()) {
      //todo: cast to SBuild?
      builds.add(new BuildRef((SBuild)entry.getKey(), myDataProvider, myApiUrlBuilder));
    }
    Collections.sort(builds, new BuildDependenciesComparator());
    return new BuildsList(builds);
  }

  @XmlElement(name = "revisions")
  public Revisions getRevisions() {
    return new Revisions(myBuild.getRevisions(), myApiUrlBuilder);
  }

  @XmlElement(name = "changes")
  public ChangesRef getChanges() {
    return new ChangesRef(myBuild, myApiUrlBuilder);
  }

  @XmlElement(name = "triggered")
  public TriggeredBy getTriggered(){
    final jetbrains.buildServer.serverSide.TriggeredBy triggeredBy = myBuild.getTriggeredBy();
    return triggeredBy != null ? new TriggeredBy(triggeredBy, myDataProvider, myApiUrlBuilder) : null;
  }
  
  @XmlElement(name = "relatedIssues")
  public IssueUsages getIssues() {
    if (TeamCityProperties.getBoolean("rest.disableBuildRelatedIssues")){
      return null;
    }
    final Collection<Issue> relatedIssues = myBuild.getRelatedIssues();
    return relatedIssues.size() == 0 ? null : new IssueUsages(relatedIssues, myBuild, myApiUrlBuilder, myFactory);
  }

  @XmlElement(name = "user")
  public UserRef getPersonalBuildUser() {
    final SUser owner = myBuild.getOwner();
    return owner == null ? null : new UserRef(owner, myApiUrlBuilder);
  }

  private List<BuildRef> getBuildRefs(@NotNull Collection<? extends BuildDependency> dependencies,
                                      @NotNull final DataProvider dataProvider) {
    List<BuildRef> result = new ArrayList<BuildRef>(dependencies.size());
    for (BuildDependency dependency : dependencies) {
      final SBuild dependOnBuild = dependency.getDependOn().getAssociatedBuild();
      if (dependOnBuild != null) {
        result.add(new BuildRef(dependOnBuild, dataProvider, myApiUrlBuilder));
      }
    }
    Collections.sort(result, new BuildDependenciesComparator());
    return result;
  }

  private class BuildDependenciesComparator implements Comparator<BuildRef> {
    public int compare(final BuildRef o1, final BuildRef o2) {
      final int buildTypesCompare = o1.getBuildTypeId().compareTo(o2.getBuildTypeId());
      return buildTypesCompare != 0 ? buildTypesCompare : (int)(o1.getId() - o2.getId());
    }
  }
}