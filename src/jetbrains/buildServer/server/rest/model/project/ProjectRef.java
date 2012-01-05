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
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.serverSide.SProject;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlType(propOrder = {"href", "name", "id"})
public class ProjectRef {
  @XmlAttribute
  public String id;

  @XmlAttribute
  public String name;

  @XmlAttribute
  public String href;

  public ProjectRef() {
  }

  public ProjectRef(SProject project, final ApiUrlBuilder apiUrlBuilder) {
    id = project.getProjectId();
    name = project.getName();
    href = apiUrlBuilder.getHref(project);
  }
}