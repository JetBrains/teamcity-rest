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
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.dependency.BuildDependency;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
//todo: add changes
//todo: reuse fields code from DataProvider
@XmlRootElement(name = "build")
public class Build {
  @XmlAttribute
  public long id;
  @XmlAttribute
  public String number;
  @XmlAttribute
  public String status;
  @XmlAttribute
  public boolean pinned;

  @XmlElement
  public BuildTypeRef buildType;

  //todo: investigate common date formats approach in REST
  @XmlElement
  public String startDate;
  @XmlElement
  public String finishDate;

  @XmlElement
  public List<String> tags;

  @XmlElement
  public Properties properties;

  @XmlElement(name = "dependency-build")
  public List<BuildRef> buildDependencies;

  @XmlElement(name = "revisions")
  public Revisions revisions;

  @XmlElement(name = "changes")
  public Changes changes;

  public Build() {
  }

  public Build(SBuild build) {
    id = build.getBuildId();
    number = build.getBuildNumber();
    status = build.getStatusDescriptor().getStatus().getText();
    pinned = build.isPinned();
    startDate = (new SimpleDateFormat("yyyyMMdd'T'HHmmssZ")).format(build.getStartDate());
    finishDate = (new SimpleDateFormat("yyyyMMdd'T'HHmmssZ")).format(build.getFinishDate());
    buildType = new BuildTypeRef(build.getBuildType());
    properties = new Properties(build.getBuildPromotion().getBuildParameters());
    tags = build.getTags();
    buildDependencies = getBuildRefs(build.getBuildPromotion().getDependencies());
    revisions = new Revisions(build.getRevisions());
    changes = new Changes(build.getContainingChanges());
  }

  private List<BuildRef> getBuildRefs(Collection<? extends BuildDependency> dependencies) {
    List<BuildRef> result = new ArrayList<BuildRef>(dependencies.size());
    for (BuildDependency dependency : dependencies) {
      result.add(new BuildRef(dependency.getDependOn().getAssociatedBuild()));
    }
    return result;
  }

  /**
   * @author Yegor.Yarko
   *         Date: 16.04.2009
   */
  public static class Revision {
    @XmlAttribute(name = "display-version")
    public String displayRevision;
    @XmlElement(name = "vcs-root")
    public VcsRoot.VcsRootRef vcsRoot;

    public Revision() {
    }

    public Revision(BuildRevision revision) {
      displayRevision = revision.getRevisionDisplayName();
      vcsRoot = new VcsRoot.VcsRootRef(revision.getRoot());
    }
  }
}

class Revisions {
  @XmlElement(name = "revision")
  public List<Build.Revision> revisoins;

  public Revisions() {
  }

  public Revisions(final List<BuildRevision> buildRevisions) {
    revisoins = new ArrayList<Build.Revision>(buildRevisions.size());
    for (BuildRevision revision : buildRevisions) {
      revisoins.add(new Build.Revision(revision));
    }
  }
}

