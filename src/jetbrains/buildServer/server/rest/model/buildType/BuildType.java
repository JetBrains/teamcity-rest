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

package jetbrains.buildServer.server.rest.model.buildType;

import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.build.BuildsRef;
import jetbrains.buildServer.server.rest.model.change.VcsRootEntries;
import jetbrains.buildServer.server.rest.model.project.ProjectRef;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.SBuildRunnerDescriptor;
import jetbrains.buildServer.serverSide.SBuildType;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "buildType")
@XmlType(propOrder = {"paused", "description", "webUrl", "href", "name", "id",
  "project", "vcsRootEntries", "builds", "parameters", "runParameters"})
public class BuildType {
  protected SBuildType myBuildType;
  private DataProvider myDataProvider;
  private ApiUrlBuilder myApiUrlBuilder;

  private final boolean canViewSettings;

  public BuildType() {
    canViewSettings = true;
  }

  public BuildType(final SBuildType buildType, final DataProvider dataProvider, final ApiUrlBuilder apiUrlBuilder) {
    myBuildType = buildType;
    myDataProvider = dataProvider;
    myApiUrlBuilder = apiUrlBuilder;
    canViewSettings = !shouldRestrictSettingsViewing(buildType, dataProvider);
  }

  @XmlAttribute
  public String getId() {
    return myBuildType.getBuildTypeId();
  }

  @XmlAttribute
  public String getName() {
    return myBuildType.getName();
  }

  @XmlAttribute
  public String getHref() {
    return myApiUrlBuilder.getHref(myBuildType);
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
    return new ProjectRef(myBuildType.getProject(), myApiUrlBuilder);
  }

  @XmlElement(name = "vcs-root")
  public VcsRootEntries getVcsRootEntries() {
    return check(new VcsRootEntries(myBuildType.getVcsRootEntries(), myApiUrlBuilder));
  }

  @XmlElement
  public BuildsRef getBuilds() {
    return new BuildsRef(myBuildType, myApiUrlBuilder);
  }

  @XmlElement
  public Properties getParameters() {
    return check(new Properties(myBuildType.getBuildParameters()));
  }

  @XmlElement
  public Properties getRunParameters() {
    List<SBuildRunnerDescriptor> runners = myBuildType.getBuildRunners();
    return check(new Properties(runners.isEmpty() ? Collections.<String, String>emptyMap() : runners.get(0).getParameters()));
  }

  public static boolean shouldRestrictSettingsViewing(final @NotNull BuildTypeSettings buildType, final @NotNull DataProvider permissionChecker) {
    if (TeamCityProperties.getBooleanOrTrue("rest.beans.buildType.checkPermissions")) {
      return !permissionChecker.isPermissionGranted(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, buildType.getProject().getProjectId());
    }
    return false;
  }

  @Nullable
  private <T> T check(@Nullable T t) {
    if (canViewSettings) {
      return t;
    } else {
      return null;
    }
  }
}
