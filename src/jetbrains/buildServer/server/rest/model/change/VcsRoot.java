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

package jetbrains.buildServer.server.rest.model.change;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.project.ProjectRef;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRootStatus;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "vcs-root")
@XmlType(name = "vcs-root", propOrder = {"lastChecked", "status", "shared", "vcsName", "name", "id",
  "project", "properties"})
@SuppressWarnings("PublicField")
public class VcsRoot {
  @XmlAttribute
  public Long id;

  @XmlAttribute
  public String name;

  @XmlAttribute
  public String vcsName;

  @XmlAttribute
  public Boolean shared;


  @XmlAttribute
  public String projectLocator; // used only when creating new VCS roots


  @XmlAttribute
  public  String status;

  @XmlAttribute
  public  String lastChecked;


  @XmlElement
  public Properties properties;

  @XmlElement
  public ProjectRef project;


  /*
  @XmlAttribute
  private String currentVersion;
  */

  public VcsRoot() {
  }

  public VcsRoot(final SVcsRoot root, final DataProvider dataProvider, final ApiUrlBuilder apiUrlBuilder) {
    id = root.getId();
    name = root.getName();
    vcsName = root.getVcsName();
    shared = root.getScope().isGlobal();
    if (!shared){
      project = new ProjectRef(dataProvider.getProjectById(root.getScope().getOwnerProjectId()), apiUrlBuilder);
    }
    properties = new Properties(root.getProperties());
    final VcsRootStatus rootStatus = dataProvider.getVcsManager().getStatus(root);
    status = rootStatus.getType().toString();
    lastChecked = Util.formatTime(rootStatus.getTimestamp());
    /*
    final RepositoryVersion revision = ((VcsRootInstance)root).getLastUsedRevision();
    currentVersion = revision != null ? revision.getDisplayVersion() : null; //todo: consider using smth like "NONE" ?
    */
  }
}

