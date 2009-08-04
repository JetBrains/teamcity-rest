/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.build;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.*;
import jetbrains.buildServer.server.rest.DataProvider;
import jetbrains.buildServer.server.rest.data.Comment;
import jetbrains.buildServer.server.rest.data.Properties;
import jetbrains.buildServer.server.rest.data.agent.AgentRef;
import jetbrains.buildServer.server.rest.data.buildType.BuildTypeRef;
import jetbrains.buildServer.server.rest.data.change.Changes;
import jetbrains.buildServer.server.rest.data.change.Revisions;
import jetbrains.buildServer.server.rest.data.issue.IssueUsages;
import jetbrains.buildServer.server.rest.request.BuildRequest;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.dependency.BuildDependency;
import org.jetbrains.annotations.NotNull;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
//todo: add changes
//todo: reuse fields code from DataProvider
@XmlRootElement(name = "build")
@XmlType(propOrder = {"pinned", "personal", "webUrl", "href", "status", "number", "id",
  "statusText", "buildType", "startDate", "finishDate", "agent", "comment", "tags", "properties",
  "buildDependencies", "revisions", "changes", "issues"})
public class Build {
  @NotNull
  protected SBuild myBuild;
  @NotNull
  private DataProvider myDataProvider;

  public Build() {
  }

  public Build(@NotNull final SBuild build, @NotNull final DataProvider dataProvider) {
    myBuild = build;
    myDataProvider = dataProvider;
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
    return BuildRequest.getBuildHref(myBuild);
  }

  @XmlAttribute
  public String getStatus() {
    return myBuild.getStatusDescriptor().getStatus().getText();
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
    return new AgentRef(agent);
  }

  @XmlElement
  public BuildTypeRef getBuildType() {
    return new BuildTypeRef(myBuild.getBuildType());
  }

  //todo: investigate common date formats approach
  @XmlElement
  public String getStartDate() {
    return (new SimpleDateFormat("yyyyMMdd'T'HHmmssZ")).format(myBuild.getStartDate());
  }

  @XmlElement
  public String getFinishDate() {
    return (new SimpleDateFormat("yyyyMMdd'T'HHmmssZ")).format(myBuild.getFinishDate());
  }

  @XmlElement(defaultValue = "")
  public Comment getComment() {
    final jetbrains.buildServer.serverSide.comments.Comment comment = myBuild.getBuildComment();
    if (comment != null) {
      return new Comment(comment);
    }
    return null;
  }

  @XmlElementWrapper(name = "tags")
  @XmlElement(name = "tag")
  public List<String> getTags() {
    return myBuild.getTags();
  }

  @XmlElement
  public Properties getProperties() {
    return new Properties(myBuild.getBuildPromotion().getBuildParameters());
  }

  @XmlElement(name = "dependency-build")
  public List<BuildRef> getBuildDependencies() {
    return getBuildRefs(myBuild.getBuildPromotion().getDependencies(), myDataProvider);
  }

  @XmlElement(name = "revisions")
  public Revisions getRevisions() {
    return new Revisions(myBuild.getRevisions());
  }

  @XmlElement(name = "changes")
  public Changes getChanges() {
    return new Changes(myBuild.getContainingChanges());
  }

  @XmlElement(name = "relatedIssues")
  public IssueUsages getIssues() {
    return new IssueUsages(myBuild.getRelatedIssues(), myBuild);
  }

  private List<BuildRef> getBuildRefs(@NotNull Collection<? extends BuildDependency> dependencies,
                                      @NotNull final DataProvider dataProvider) {
    List<BuildRef> result = new ArrayList<BuildRef>(dependencies.size());
    for (BuildDependency dependency : dependencies) {
      final SBuild dependOnBuild = dependency.getDependOn().getAssociatedBuild();
      if (dependOnBuild != null) {
        result.add(new BuildRef(dependOnBuild, dataProvider));
      }
    }
    return result;
  }

}