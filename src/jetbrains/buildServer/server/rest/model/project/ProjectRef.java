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

package jetbrains.buildServer.server.rest.model.project;

import javax.xml.bind.annotation.XmlAttribute;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
public class ProjectRef {
  @NotNull protected SProject myProject;
  protected ApiUrlBuilder myApiUrlBuilder;

  public ProjectRef() {
  }

  public ProjectRef(SProject project, final ApiUrlBuilder apiUrlBuilder) {
    myProject = project;
    myApiUrlBuilder = apiUrlBuilder;
  }

  @XmlAttribute
  public String getHref() {
    return myApiUrlBuilder.getHref(myProject);
  }

  @XmlAttribute
  public String getId() {
    return myProject.getProjectId();
  }

  @XmlAttribute
  public String getName() {
    return myProject.getName();
  }
}
