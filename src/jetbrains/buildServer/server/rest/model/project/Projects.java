/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "projects")
@XmlType(name = "projects")
public class Projects {
  @XmlAttribute
  public Integer count;

  @XmlAttribute
  @Nullable
  public String href;

  @XmlAttribute(required = false)
  @Nullable
  public String nextHref;

  @XmlAttribute(required = false)
  @Nullable
  public String prevHref;

  @XmlElement(name = "project")
  public List<Project> projects;

  public Projects() {
  }

  public Projects(@NotNull final List<SProject> projectObjects, @Nullable final PagerData pagerData, final @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    if (fields.isIncluded("project", false, true)){
      projects = ValueWithDefault.decideDefault(fields.isIncluded("project"), new ValueWithDefault.Value<List<Project>>() {
        public List<Project> get() {
          final ArrayList<Project> result = new ArrayList<Project>(projectObjects.size());
          final Fields nestedField = fields.getNestedField("project");
          for (SProject project : projectObjects) {
            result.add(new Project(project, nestedField, beanContext));
          }
          return result;
        }
      });
    }else{
      projects = null;
    }
    if (pagerData != null) {
      href = ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getHref()));
      nextHref = ValueWithDefault
        .decideDefault(fields.isIncluded("nextHref"), pagerData.getNextHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null);
      prevHref = ValueWithDefault
        .decideDefault(fields.isIncluded("prevHref"), pagerData.getPrevHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null);
    }
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), projectObjects.size());
  }

  @NotNull
  public List<SProject> getProjectsFromPosted(@NotNull ProjectFinder projectFinder) {
    if (projects == null) {
      return Collections.emptyList();
    }
    final ArrayList<SProject> result = new ArrayList<SProject>(projects.size());
    for (Project project : projects) {
      result.add(project.getProjectFromPosted(projectFinder));
    }
    return result;
  }
}