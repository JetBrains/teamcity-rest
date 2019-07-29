/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.parameters.PasswordParametersFilterCore;
import jetbrains.buildServer.parameters.impl.MapParametersProviderImpl;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.BuildArtifactsFinder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Comment;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.agent.AgentRef;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeRef;
import jetbrains.buildServer.server.rest.model.change.ChangesRef;
import jetbrains.buildServer.server.rest.model.change.Revisions;
import jetbrains.buildServer.server.rest.model.issue.IssueUsages;
import jetbrains.buildServer.server.rest.model.user.UserRef;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.Branch;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.dependency.BuildDependency;
import jetbrains.buildServer.serverSide.impl.BaseBuild;
import jetbrains.buildServer.serverSide.parameters.types.PasswordsSearcher;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.PasswordReplacer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.*;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
//todo: add changes
//todo: reuse fields code from DataProvider
@XmlRootElement(name = "build")
@XmlType(name = "build", propOrder = {"id", "number", "status", "href", "webUrl", "branchName", "defaultBranch", "unspecifiedBranch", "personal", "history", "pinned", "running",
  "runningBuildInfo", "statusText", "buildType", "startDate", "finishDate", "agent", "comment", "tags", "pinInfo", "personalBuildUser", "properties",
  "buildDependencies", "buildArtifactDependencies", "revisions", "triggered", "changes", "issues", "artifacts"})
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

  @NotNull
  public static ParametersProvider getBuildResultingParameters(@NotNull BuildPromotion buildPromotion, @NotNull ServiceLocator serviceLocator) {
    SBuild build = buildPromotion.getAssociatedBuild();
    if (build != null && build instanceof BaseBuild) {
      try {
        Map<String, String> parameters = ((BaseBuild)build).getBuildFinishParameters();
        if (parameters == null) {
          parameters = ((BaseBuild)build).getBuildStartParameters();
        }
        if (parameters != null) {
          return new MapParametersProviderImpl(parameters);
        }
      } catch (ClassCastException ignore) {
      }
    }
    //falling back to recalculated parameters
    return calculateAllParameters(buildPromotion, serviceLocator.findSingletonService(PasswordsSearcher.class));
  }

  private static ParametersProvider calculateAllParameters(@NotNull final BuildPromotion buildPromotion, @NotNull PasswordsSearcher searcher) {
    Set<String> passwords = searcher.collectPasswords(buildPromotion);
    final ParametersProvider provider = ((BuildPromotionEx)buildPromotion).getParametersProvider();
    if (passwords.isEmpty()) {
      return provider;
    }
    final PasswordReplacer passwordReplacer = PasswordParametersFilterCore.createPasswordReplacer(passwords);
    return new ParametersProvider() {
      @Nullable public String get(@NotNull final String key) {
        String value = provider.get(key);
        return value == null ? null : passwordReplacer.replacePasswords(value);
      }
      public int size() { return provider.size();}
      public Map<String, String> getAll() {
        Map<String, String> all = provider.getAll();
        Map<String, String> map = new HashMap<String, String>(all.size());
        for (Map.Entry<String, String> e : all.entrySet()) {
          map.put(e.getKey(), passwordReplacer.replacePasswords(e.getValue()));
        }
        return map;
      }
    };
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
  public Builds getBuildDependencies() {
    return new Builds(getBuilds(myBuild.getBuildPromotion().getDependencies()), myDataProvider, null, myApiUrlBuilder);
  }

  @XmlElement(name = "artifact-dependencies")
  public Builds getBuildArtifactDependencies() {
    final Map<jetbrains.buildServer.Build,List<ArtifactInfo>> artifacts = myBuild.getDownloadedArtifacts().getArtifacts();
    List<SBuild> builds = new ArrayList<SBuild>(artifacts.size());
    for (jetbrains.buildServer.Build sourceBuild : artifacts.keySet()) {
      //todo: TeamCity API: cast to SBuild?
      builds.add((SBuild)sourceBuild);
    }
    Collections.sort(builds, new BuildDependenciesComparator());
    return new Builds(builds, myDataProvider, null, myApiUrlBuilder);
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
    return new IssueUsages(myBuild, TeamCityProperties.getBoolean("rest.beans.build.inlineRelatedIssues"), myApiUrlBuilder, myFactory);
  }

  @XmlElement(name = "user")
  public UserRef getPersonalBuildUser() {
    final SUser owner = myBuild.getOwner();
    return owner == null ? null : new UserRef(owner, myApiUrlBuilder);
  }

  @NotNull
  @XmlElement(name = "artifacts")
  public Href getArtifacts() {
    return new Href(BuildArtifactsFinder.fileApiUrlBuilderForBuild(myApiUrlBuilder, myBuild, null).getChildrenHref(null));
  }

  private List<SBuild> getBuilds(@NotNull Collection<? extends BuildDependency> dependencies) {
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

  private class BuildDependenciesComparator implements Comparator<SBuild> {
    public int compare(final SBuild o1, final SBuild o2) {
      final int buildTypesCompare = o1.getBuildTypeId().compareTo(o2.getBuildTypeId());
      return buildTypesCompare != 0 ? buildTypesCompare : (int)(o1.getBuildId() - o2.getBuildId());
    }
  }

  @Nullable
  public static String getFieldValue(@NotNull final SBuild build, @Nullable final String field) {
    if ("number".equals(field)) {
      return build.getBuildNumber();
    } else if ("status".equals(field)) {
      return build.getStatusDescriptor().getStatus().getText();
    } else if ("id".equals(field)) {
      return (new Long(build.getBuildId())).toString();
    } else if ("startDate".equals(field)) {
      return Util.formatTime(build.getStartDate());
    } else if ("finishDate".equals(field)) {
      return Util.formatTime(build.getFinishDate());
    } else if ("buildTypeId".equals(field)) {
      return (build.getBuildTypeExternalId());
    } else if ("buildTypeInternalId".equals(field)) {
      return (build.getBuildTypeId());
    } else if ("branchName".equals(field)) {
      Branch branch = build.getBranch();
      return branch == null ? "" : branch.getDisplayName();
    } else if ("branch".equals(field)) {
      Branch branch = build.getBranch();
      return branch == null ? "" : branch.getName();
    } else if ("defaultBranch".equals(field)) {
      Branch branch = build.getBranch();
      return branch == null ? "" : String.valueOf(branch.isDefaultBranch());
    } else if ("unspecifiedBranch".equals(field)) {
      Branch branch = build.getBranch();
      return branch == null ? "" : String.valueOf(Branch.UNSPECIFIED_BRANCH_NAME.equals(branch.getName()));
    } else if ("promotionId".equals(field)) { //Experimental support only, this is not exposed in any other way
      return (String.valueOf(build.getBuildPromotion().getId()));
    } else if ("modificationId".equals(field)) { //Experimental support only, this is not exposed in any other way
      return (String.valueOf(build.getBuildPromotion().getLastModificationId()));
    } else if ("commentText".equals(field)) { //Experimental support only
      final jetbrains.buildServer.serverSide.comments.Comment buildComment = build.getBuildComment();
      return buildComment != null ? buildComment.getComment() : null;
    }
    throw new NotFoundException("Field '" + field + "' is not supported. Supported are: number, status, id, startDate, finishDate, buildTypeId, branchName.");
  }
}