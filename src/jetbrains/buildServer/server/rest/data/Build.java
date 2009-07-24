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

package jetbrains.buildServer.server.rest.data;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.dependency.BuildDependency;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
//todo: add changes
//todo: reuse fields code from DataProvider
@XmlRootElement(name = "build")
public class Build extends BuildRef {

  public Build() {
  }

  public Build(SBuild build) {
    super(build);
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

  //todo: investigate empty comment case
  @XmlElement
  public List<Comment> getComment() {
    ArrayList<Comment> result = new ArrayList<Comment>();
    final jetbrains.buildServer.serverSide.comments.Comment comment = myBuild.getBuildComment();
    if (comment != null) {
      result.add(new Comment(comment));
    }
    return result;
  }

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
    return getBuildRefs(myBuild.getBuildPromotion().getDependencies());
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
  public Issues getIssues() {
    return new Issues(myBuild.getRelatedIssues());
  }

  private List<BuildRef> getBuildRefs(Collection<? extends BuildDependency> dependencies) {
    List<BuildRef> result = new ArrayList<BuildRef>(dependencies.size());
    for (BuildDependency dependency : dependencies) {
      result.add(new BuildRef(dependency.getDependOn().getAssociatedBuild()));
    }
    return result;
  }

}