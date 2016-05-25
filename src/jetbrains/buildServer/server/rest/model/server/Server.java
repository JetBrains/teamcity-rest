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

package jetbrains.buildServer.server.rest.model.server;

import java.util.Date;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.RootUrlHolder;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.maintenance.StartupContext;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.request.*;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.ServerSettings;
import jetbrains.buildServer.serverSide.auth.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 17.11.2009
 */
@XmlRootElement(name = "server")
@XmlType(name = "server", propOrder={"version", "versionMajor", "versionMinor", "startTime", "currentTime", "buildNumber", "buildDate", "internalId", "webUrl",
"projects", "vcsRoots", "builds", "users", "userGroups", "agents", "buildQueue", "agentPools"})
public class Server {
  private SBuildServer myServer;
  private ServerSettings myServerSettings;

  private BeanContext myBeanContext;
  private ApiUrlBuilder myApiUrlBuilder;

  public Server() {
  }

  public Server(final BeanContext beanContext) {
    myBeanContext = beanContext;
    myServer = beanContext.getSingletonService(SBuildServer.class);
    myServerSettings = beanContext.getSingletonService(ServerSettings.class);
    myApiUrlBuilder = beanContext.getContextService(ApiUrlBuilder.class);
  }

  @XmlAttribute
  public String getVersion() {
    return myServer.getFullServerVersion();
  }

  @XmlAttribute
  public byte getVersionMajor() {
    return myServer.getServerMajorVersion();
  }

  @XmlAttribute
  public byte getVersionMinor() {
    return myServer.getServerMinorVersion();
  }

  @XmlAttribute
  public String getBuildNumber() {
    return myServer.getBuildNumber();
  }

  @XmlAttribute
  public String getStartTime() {
    try {
      //workaround for https://youtrack.jetbrains.com/issue/TW-25260
      return Util.formatTime(myBeanContext.getSingletonService(DataProvider.class).getBean(StartupContext.class).getServerStartupTimestamp());
    } catch (Exception e) {
      return null;
    }
  }

  @XmlAttribute
  public String getCurrentTime() {
    return Util.formatTime(new Date());
  }

  @XmlAttribute
  public String getBuildDate() {
    return Util.formatTime(myServer.getBuildDate());
  }

  @XmlAttribute
  public String getInternalId() {
    return myServerSettings.getServerUUID();
  }

  @XmlAttribute
  public String getWebUrl() {
    return myServer.getRootUrl();
  }

  @XmlElement
  public Href getProjects() {
    return new Href(ProjectRequest.API_PROJECTS_URL,myApiUrlBuilder);
  }

  @XmlElement
  public Href getVcsRoots() {
    return new Href(VcsRootRequest.API_VCS_ROOTS_URL, myApiUrlBuilder);
  }

  @XmlElement
  public Href getBuilds() {
    return new Href(BuildRequest.API_BUILDS_URL, myApiUrlBuilder);
  }

  @XmlElement
  public Href getUsers() {
    return new Href(UserRequest.API_USERS_URL, myApiUrlBuilder);
  }

  @XmlElement
  public Href getUserGroups() {
    return new Href(GroupRequest.API_USER_GROUPS_URL, myApiUrlBuilder);
  }

  @XmlElement
  public Href getAgents() {
    return new Href(AgentRequest.API_AGENTS_URL, myApiUrlBuilder);
  }

  @XmlElement
  public Href getBuildQueue() {
    return new Href(BuildQueueRequest.getHref(), myApiUrlBuilder);
  }

  @XmlElement
  public Href getAgentPools() {
    return new Href(AgentPoolRequest.getHref(), myApiUrlBuilder);
  }

  @Nullable
  public static String getFieldValue(@Nullable final String field, @NotNull final ServiceLocator serviceLocator) {
    // Note: "build", "majorVersion" and "minorVersion" for backward compatibility.
    if (ServerRequest.SERVER_VERSION_RQUEST_PATH.equals(field)) {
      return serviceLocator.getSingletonService(SBuildServer.class).getFullServerVersion();
    } else if ("buildNumber".equals(field) || "build".equals(field)) {
      return serviceLocator.getSingletonService(SBuildServer.class).getBuildNumber();
    } else if ("versionMajor".equals(field) || "majorVersion".equals(field)) {
      return Byte.toString(serviceLocator.getSingletonService(SBuildServer.class).getServerMajorVersion());
    } else if ("versionMinor".equals(field) || "minorVersion".equals(field)) {
      return Byte.toString(serviceLocator.getSingletonService(SBuildServer.class).getServerMinorVersion());
    } else if ("startTime".equals(field)) {
      return Util.formatTime(serviceLocator.getSingletonService(DataProvider.class).getServerStartTime());
    } else if ("currentTime".equals(field)) {
      return Util.formatTime(new Date());
    } else if ("internalId".equals(field)) {
      return serviceLocator.getSingletonService(ServerSettings.class).getServerUUID();
    } else if ("superUserToken".equals(field)) {
      serviceLocator.getSingletonService(DataProvider.class).checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
      return serviceLocator.getSingletonService(DataProvider.class).getBean(StartupContext.class).getMaintenanceAuthenticationToken();
    } else if ("dataDirectoryPath".equals(field)) { //experimental
      serviceLocator.getSingletonService(DataProvider.class).checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
      return serviceLocator.getSingletonService(DataProvider.class).getBean(ServerPaths.class).getDataDirectory().getAbsolutePath();
    } else if ("webUrl".equals(field) || "url".equals(field)) {
      return serviceLocator.getSingletonService(RootUrlHolder.class).getRootUrl();
    }
    throw new NotFoundException("Field '" + field + "' is not supported. Supported are: version, versionMajor, versionMinor, buildNumber, startTime, currentTime, internalId.");
  }
}
