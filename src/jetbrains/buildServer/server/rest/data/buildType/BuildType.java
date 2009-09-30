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

package jetbrains.buildServer.server.rest.data.buildType;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.DataProvider;
import jetbrains.buildServer.server.rest.data.Properties;
import jetbrains.buildServer.server.rest.data.build.BuildsRef;
import jetbrains.buildServer.server.rest.data.change.VcsRootEntries;
import jetbrains.buildServer.server.rest.data.project.ProjectRef;
import jetbrains.buildServer.serverSide.SBuildType;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "buildType")
public class BuildType extends BuildTypeRef {
  private DataProvider myDataProvider;

  public BuildType() {
  }

  public BuildType(final SBuildType buildType, final DataProvider dataProvider) {
    super(buildType);
    myDataProvider = dataProvider;
  }

  @XmlAttribute
  public String getDescription() {
    return myBuildType.getDescription();
  }

  @XmlAttribute
  public boolean isPaused() {
    return myBuildType.isPaused();
  }

  @XmlAttribute
  public String getWebUrl() {
    return myDataProvider.getBuildTypeUrl(myBuildType);
  }

  @XmlElement
  public ProjectRef getProject() {
    return new ProjectRef(myBuildType.getProject());
  }

  @XmlElement(name = "vcs-root")
  public VcsRootEntries getVcsRootEntries() {
    return new VcsRootEntries(myBuildType.getVcsRootEntries());
  }

  @XmlElement
  public BuildsRef getBuilds() {
    return new BuildsRef(myBuildType);
  }

  @XmlElement
  public Properties getParameters() {
    return new Properties(myBuildType.getBuildParameters());
  }

  @XmlElement
  public Properties getRunParameters() {
    return new Properties(myBuildType.getRunParameters());
  }
}
