/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.jersey;

import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.api.json.JSONJAXBContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import jetbrains.buildServer.server.rest.model.agent.Agent;
import jetbrains.buildServer.server.rest.model.agent.Agents;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.model.change.*;
import jetbrains.buildServer.server.rest.model.group.Group;
import jetbrains.buildServer.server.rest.model.group.Groups;
import jetbrains.buildServer.server.rest.model.issue.Issue;
import jetbrains.buildServer.server.rest.model.issue.IssueUsage;
import jetbrains.buildServer.server.rest.model.issue.IssueUsages;
import jetbrains.buildServer.server.rest.model.issue.Issues;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.model.project.Projects;
import jetbrains.buildServer.server.rest.model.user.User;
import jetbrains.buildServer.server.rest.model.user.UserData;
import jetbrains.buildServer.server.rest.model.user.Users;
import jetbrains.buildServer.util.FuncThrow;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@Provider
//todo: add to spring to make it work:   <bean id="jAXBContextResolver" class="jetbrains.buildServer.server.rest.JAXBContextResolver"/>
public class JAXBContextResolver implements ContextResolver<JAXBContext> {
  private JAXBContext context;

  private Set<Class> types;

  //Root entities should be listed here 
  private final Class[] cTypes = {
    Agent.class, Agents.class,
    Build.class, Builds.class,
    BuildType.class, BuildTypes.class,
    Change.class, FileChange.class, Revision.class, Revisions.class,
    VcsRoot.class, VcsRoots.class, VcsRootEntry.class, VcsRootEntries.class,
    Group.class, Groups.class,
    Issue.class, Issues.class, IssueUsage.class, IssueUsages.class,
    Project.class, Projects.class,
    User.class, UserData.class, Users.class,
  };

  public JAXBContextResolver() throws Exception {
    // necessary until http://youtrack.jetbrains.net/issue/TW-10204 is fixed
    jetbrains.buildServer.util.Util.doUnderContextClassLoader(getClass().getClassLoader(), new FuncThrow<Void, JAXBException>() {
      public Void apply() throws JAXBException {
        types = new HashSet<Class>(Arrays.asList(cTypes));
        context = new JSONJAXBContext(JSONConfiguration.natural().build(), cTypes);
        return null;
      }
    });
  }

  public JAXBContext getContext(Class<?> objectType) {
    return (types.contains(objectType)) ? context : null;
  }
}
