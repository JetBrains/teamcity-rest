/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import java.util.Collections;
import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.BuildFinder;
import jetbrains.buildServer.server.rest.data.BuildPromotionFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.model.user.User;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.impl.BuildServerImpl;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 13.01.12
 */
@SuppressWarnings("PublicField")
@XmlType(propOrder = {"type", "details", "date", "displayText", "rawValue",
  "user", "build", "buildType", "properties"})
public class TriggeredBy {
  protected static final String TYPE_IDE_PLUGIN_REST = "idePlugin";
  protected static final String CORE_TRIGGERED_BY_TYPE_XML_RPC = "xmlRpc";
  @XmlAttribute
  public String date;

  @XmlAttribute
  public String type;

  @XmlAttribute
  public String details;


  @XmlElement(name = "user")
  public User user;

  @XmlElement(name = "buildType")
  public BuildType buildType;

  @XmlElement(name = "build")
  public Build build;

  /**
   * Experimental
   * The sme text as shown in UI - considering all the registered renderers
   */
  @XmlAttribute
  public String displayText;

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

  public TriggeredBy(final jetbrains.buildServer.serverSide.TriggeredBy triggeredBy, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    date = ValueWithDefault.decideDefault(fields.isIncluded("date"), Util.formatTime(triggeredBy.getTriggeredDate()));
    final SUser user1 = triggeredBy.getUser();
    user = user1 == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("user"), new ValueWithDefault.Value<User>() {
      public User get() {
        return new User(user1, fields.getNestedField("user"), beanContext);
      }
    });

    //TeamCity API issue: would be cool to extract common logic from ServerTriggeredByProcessor.render and provide visitor as a service
    setType(triggeredBy, fields, beanContext);

    displayText = ValueWithDefault.decideDefault(fields.isIncluded("displayText", false, false), () -> triggeredBy.getAsString());

    final boolean includeProp = TeamCityProperties.getBoolean("rest.internalMode");
    rawValue = ValueWithDefault.decideDefault(fields.isIncluded("rawValue", includeProp, includeProp), new ValueWithDefault.Value<String>() {
      public String get() {
        return triggeredBy.getRawTriggeredBy();
      }
    });
    properties = ValueWithDefault.decideDefault(fields.isIncluded("properties", includeProp, includeProp), new ValueWithDefault.Value<Properties>() {
      public Properties get() {
        return new Properties(triggeredBy.getParameters(), null, fields.getNestedField("properties", Fields.NONE, Fields.LONG), beanContext);
      }
    });
  }

  private void setType(final jetbrains.buildServer.serverSide.TriggeredBy triggeredBy, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    //see also jetbrains.buildServer.serverSide.impl.ServerTriggeredByProcessor.render()
    final String rawTriggeredBy = triggeredBy.getRawTriggeredBy();
    if (rawTriggeredBy != null && !rawTriggeredBy.startsWith(TriggeredByBuilder.PARAMETERS_PREFIX)) {
      type = ValueWithDefault.decideDefault(fields.isIncluded("type"), "unknown");
      details = ValueWithDefault.decideDefault(fields.isIncluded("details"), rawTriggeredBy);
    }

    final Map<String, String> triggeredByParams = triggeredBy.getParameters();

    String buildId = triggeredByParams.get(TriggeredByBuilder.BUILD_ID_PARAM_NAME);
    if (buildId != null) {
      build = ValueWithDefault.decideDefault(fields.isIncluded("build"), () -> {
        try {
          final BuildPromotion foundBuild = beanContext.getSingletonService(BuildFinder.class).getBuildByPromotionId(Long.valueOf(buildId));
          return new Build(foundBuild, fields.getNestedField("build"), beanContext);
        } catch (Exception e) {
          return null;
        }
      });
    }

    String typeInParams = triggeredByParams.get(TriggeredByBuilder.TYPE_PARAM_NAME);
    if (typeInParams != null) {
      type = ValueWithDefault.decideDefault(fields.isIncluded("type"), () -> typeInParams);
    }


    String buildTypeId = triggeredByParams.get(TriggeredByBuilder.BUILD_TYPE_ID_PARAM_NAME);
    if (buildTypeId != null) {
      if (typeInParams == null) {
        type = ValueWithDefault.decideDefault(fields.isIncluded("type"), "buildType");
      }
      try {
        final SBuildType foundBuildType = beanContext.getSingletonService(ProjectManager.class).findBuildTypeById(buildTypeId);
        //this mostly duplicates the data from the "build" sub-node, but can be useful (when build is deleted) and this was also part of API before 2017.1
        buildType = foundBuildType == null
                    ? null
                    : ValueWithDefault.decideDefault(fields.isIncluded("buildType"), new ValueWithDefault.Value<BuildType>() {
                      public BuildType get() {
                        return new BuildType(new BuildTypeOrTemplate(foundBuildType), fields.getNestedField("buildType"), beanContext);
                      }
                    });
      } catch (AccessDeniedException e) {
        buildType = null; //ignoring inability to view the triggering build type
      }
      return;
    }

    if (triggeredByParams.get(BuildServerImpl.UNEXPECTED_FINISH) != null ||
        triggeredByParams.get(TriggeredByBuilder.RE_ADDED_AFTER_STOP_NAME) != null) {
      if (typeInParams == null || "unexpectedFinish".equals(typeInParams)) {
        //compatibility with "type" value prior to 2017.1
        type = ValueWithDefault.decideDefault(fields.isIncluded("type"), "restarted");
      }
      return;
    }

    String idePlugin = triggeredByParams.get(TriggeredByBuilder.IDE_PLUGIN_PARAM_NAME);
    if (idePlugin != null) {
      if (typeInParams == null || CORE_TRIGGERED_BY_TYPE_XML_RPC.equals(typeInParams)) {
        //compatibility with "type" value prior to 2017.1
        type = ValueWithDefault.decideDefault(fields.isIncluded("type"), TYPE_IDE_PLUGIN_REST);
      }
      details = ValueWithDefault.decideDefault(fields.isIncluded("details"), idePlugin);
      return;
    }

    String vcsName = triggeredByParams.get(TriggeredByBuilder.VCS_NAME_PARAM_NAME);
    if (vcsName != null) {
      if (typeInParams == null) {
        type = ValueWithDefault.decideDefault(fields.isIncluded("type"), "vcs");
      }
      details = ValueWithDefault.decideDefault(fields.isIncluded("details"), vcsName);
      return;
    }

    String user = triggeredByParams.get(TriggeredByBuilder.USER_PARAM_NAME);
    if (user != null) {
      if (typeInParams == null) {
        type = ValueWithDefault.decideDefault(fields.isIncluded("type"), "user");
      }
      return;
    }

    if (typeInParams == null) {
      type = ValueWithDefault.decideDefault(fields.isIncluded("type"), "unknown");
      details = ValueWithDefault.decideDefault(fields.isIncluded("details"), rawTriggeredBy);
    }
  }

  @NotNull
  TriggeredByBuilder getFromPosted(@NotNull final String defaultType, @NotNull final ServiceLocator serviceLocator) {
    TriggeredByBuilder result = new TriggeredByBuilder();
    //only supporting a subset of options as supporting all will be easy to abuse
    if (TYPE_IDE_PLUGIN_REST.equals(type) && details != null) {
      result.addParameter(TriggeredByBuilder.IDE_PLUGIN_PARAM_NAME, details);
      result.addParameter(TriggeredByBuilder.TYPE_PARAM_NAME, CORE_TRIGGERED_BY_TYPE_XML_RPC); //setting the same type as the core so that it is parsed later as triggered by IDE
    } else {
      result.addParameter(TriggeredByBuilder.TYPE_PARAM_NAME, defaultType);
      if (build != null) {
        try {
          BuildPromotion buildFromPosted = build.getFromPosted(serviceLocator.getSingletonService(BuildPromotionFinder.class), Collections.emptyMap());
          result.addParameter(TriggeredByBuilder.BUILD_ID_PARAM_NAME, String.valueOf(buildFromPosted.getId()));
        } catch (RuntimeException ignore) {
        }
      }
    }
    return result;
  }
}
