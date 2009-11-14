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

package jetbrains.buildServer.server.rest.data.project;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.DataProvider;
import jetbrains.buildServer.server.rest.data.buildType.BuildTypes;
import jetbrains.buildServer.serverSide.SProject;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "project")
public class Project extends ProjectRef {
  private DataProvider myDataProvider;

  public Project() {
  }

  public Project(final SProject project, final DataProvider dataProvider) {
    super(project, dataProvider.getApiUrlBuilder());
    myDataProvider = dataProvider;
  }

  @XmlAttribute
  public String getDescription() {
    return myProject.getDescription();
  }

  @XmlAttribute
  public boolean isArchived() {
    return myProject.isArchived();
  }

  @XmlAttribute
  public String getWebUrl() {
    return myDataProvider.getProjectUrl(myProject);
  }

  @XmlElement
  public BuildTypes getBuildTypes() {
    return new BuildTypes(myProject.getBuildTypes(), myDataProvider);
  }
}
