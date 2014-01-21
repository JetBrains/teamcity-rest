/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.build;

import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.model.user.UserRef;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.TriggeredByBuilder;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.impl.BuildServerImpl;
import jetbrains.buildServer.users.SUser;

/**
 * @author Yegor.Yarko
 *         Date: 13.01.12
 */
@SuppressWarnings("PublicField")
@XmlType(propOrder = {"type", "details", "date", "rawValue",
"user", "buildType", "properties"})
public class TriggeredBy {
  @XmlAttribute
  public String date;

  @XmlAttribute
  public String type;

  @XmlAttribute
  public String details;


  @XmlElement(name = "user")
  public UserRef user;

  @XmlElement(name = "buildType")
  public BuildType buildType;

  /**
   * Internal use only
   */
  @XmlAttribute
  public String rawValue;

  /**
   * Internal use only
   */
  @XmlElement
  public Properties properties;

  public TriggeredBy() {
  }

  public TriggeredBy(final jetbrains.buildServer.serverSide.TriggeredBy triggeredBy,
                     final DataProvider dataProvider,
                     final ApiUrlBuilder apiUrlBuilder) {
    date = Util.formatTime(triggeredBy.getTriggeredDate());
    final SUser user1 = triggeredBy.getUser();
    user = user1 != null ? new UserRef(user1, apiUrlBuilder) : null;

    //todo: (TeamCity) would be cool to extract common logic from ServerTriggeredByProcessor.render and provide visitor as a service
    setType(triggeredBy, dataProvider, apiUrlBuilder);

    if (TeamCityProperties.getBoolean("rest.internalMode")) {
      rawValue = triggeredBy.getRawTriggeredBy();
      properties = new Properties(triggeredBy.getParameters());
    }
  }

  private void setType(final jetbrains.buildServer.serverSide.TriggeredBy triggeredBy,
                       final DataProvider dataProvider,
                       final ApiUrlBuilder apiUrlBuilder) {
    final String rawTriggeredBy = triggeredBy.getRawTriggeredBy();
    if (rawTriggeredBy != null && !rawTriggeredBy.startsWith(TriggeredByBuilder.PARAMETERS_PREFIX)) {
      type = "unknown";
      details = rawTriggeredBy;
    }

    final Map<String, String> triggeredByParams = triggeredBy.getParameters();

    String buildTypeId = triggeredByParams.get(TriggeredByBuilder.BUILD_TYPE_ID_PARAM_NAME);
    if (buildTypeId != null) {
      type = "buildType";
      try {
        final SBuildType foundBuildType = dataProvider.getServer().getProjectManager().findBuildTypeById(buildTypeId);
        buildType = foundBuildType == null
                    ? null
                    : new BuildType(new BuildTypeOrTemplate(foundBuildType), Fields.SHORT, new BeanContext(dataProvider.getBeanFactory(), dataProvider.getServer(), apiUrlBuilder));
      } catch (AccessDeniedException e) {
        buildType = null; //ignoring inability to view the triggering build type
      }
      return;
    }

    if (triggeredByParams.get(BuildServerImpl.UNEXPECTED_FINISH) != null ||
        triggeredByParams.get(TriggeredByBuilder.RE_ADDED_AFTER_STOP_NAME) != null) {
      type = "restarted";
      return;
    }

    String idePlugin = triggeredByParams.get(TriggeredByBuilder.IDE_PLUGIN_PARAM_NAME);
    if (idePlugin != null) {
      type = "idePlugin";
      details = idePlugin;
      return;
    }

    String vcsName = triggeredByParams.get(TriggeredByBuilder.VCS_NAME_PARAM_NAME);
    if (vcsName != null) {
      type = "vcs";
      details = vcsName;
      return;
    }

    String user = triggeredByParams.get(TriggeredByBuilder.USER_PARAM_NAME);
    if (user != null) {
      type = "user";
      return;
    }

    type = "unknown";
    details = rawTriggeredBy;
  }
}
