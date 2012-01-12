/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
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
@XmlType(propOrder = {"running", "pinned", "history", "personal", "webUrl", "href", "status", "number", "id",
  "runningBuildInfo", "statusText", "buildType", "startDate", "finishDate", "agent", "comment", "tags", "pinInfo", "personalBuildUser", "properties",
  "buildDependencies", "revisions", "changes", "issues"})
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

  @XmlElement
  public AgentRef getAgent() {
    final SBuildAgent agent = myDataProvider.findAgentByName(myBuild.getAgentName());
    if (agent == null) {
      return new AgentRef(myBuild.getAgentName());
    }
    return new AgentRef(agent, myApiUrlBuilder);
  }

  @XmlElement
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

  @XmlElement(name = "dependency-build")
  public List<BuildRef> getBuildDependencies() {
    return getBuildRefs(myBuild.getBuildPromotion().getDependencies(), myDataProvider);
  }

  @XmlElement(name = "revisions")
  public Revisions getRevisions() {
    return new Revisions(myBuild.getRevisions(), myApiUrlBuilder);
  }

  @XmlElement(name = "changes")
  public ChangesRef getChanges() {
    return new ChangesRef(myBuild, myApiUrlBuilder);
  }

  @XmlElement(name = "relatedIssues")
  public IssueUsages getIssues() {
    return new IssueUsages(myBuild.getRelatedIssues(), myBuild, myApiUrlBuilder, myFactory);
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
    return result;
  }

}