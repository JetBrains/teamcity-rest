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

package jetbrains.buildServer.server.rest.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.server.rest.data.AgentPoolFinder;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.agent.Agent;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.model.change.Change;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;
import jetbrains.buildServer.server.rest.model.group.Group;
import jetbrains.buildServer.server.rest.model.problem.Problem;
import jetbrains.buildServer.server.rest.model.problem.Test;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.model.user.User;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.audit.ObjectType;
import jetbrains.buildServer.serverSide.audit.ObjectWrapper;
import jetbrains.buildServer.serverSide.audit.helpers.*;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 * Date: 20/09/2018
 */
@SuppressWarnings({"PublicField", "WeakerAccess"})
@XmlRootElement(name = "relatedEntity")
public class RelatedEntity {

  @XmlAttribute(name = "type")
  private String type;


  @XmlElement(name = "unknown")
  public UnknownEntity unknown;

  @XmlElement(name = "text")
  public String text;

  @XmlElement(name = "build")
  public Build build;

  @XmlElement(name = "buildType")
  public BuildType buildType;

  @XmlElement(name = "project")
  public Project project;

  @XmlElement(name = "user")
  public User user;

  @XmlElement(name = "group")
  public Group group;

  @XmlElement(name = "test")
  public Test test;

  @XmlElement(name = "problem")
  public Problem problem;

  @XmlElement(name = "agent")
  public Agent agent;

  @XmlElement(name = "vcsRoot")
  public VcsRoot vcsRoot;

  @XmlElement(name = "change")
  public Change change;

  @SuppressWarnings("unused")
  public RelatedEntity() {
  }

  public RelatedEntity(@NotNull final Entity entity, @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    type = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("type"), entity.type);
    if (entity.text != null) {
      text = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("text"), entity.text);
    } if (entity.unknown != null) {
      unknown = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("unknown"), () -> new UnknownEntity(entity.unknown.id, entity.unknown.type, fields.getNestedField("unknown", Fields.SHORT, Fields.SHORT), beanContext));
    } else if (entity.build != null) {
      build = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("build"), () -> new Build(entity.build, fields.getNestedField("build", Fields.SHORT, Fields.SHORT), beanContext));
    } else if (entity.buildType != null) {
      buildType = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("buildType"), () -> new BuildType(entity.buildType, fields.getNestedField("buildType", Fields.SHORT, Fields.SHORT), beanContext));
    } else if (entity.project != null) {
      project = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("project"), () -> new Project(entity.project, fields.getNestedField("project", Fields.SHORT, Fields.SHORT), beanContext));
    } else if (entity.user != null) {
      user = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("user"), () -> new User(entity.user, fields.getNestedField("user", Fields.SHORT, Fields.SHORT), beanContext));
    } else if (entity.userGroup != null) {
      group = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("group"), () -> new Group(entity.userGroup, fields.getNestedField("group", Fields.SHORT, Fields.SHORT), beanContext));
    } else if (entity.test != null) {
      test = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("test"), () -> new Test(entity.test, beanContext, fields.getNestedField("test", Fields.SHORT, Fields.SHORT)));
    } else if (entity.problem != null) {
      problem = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("problem"), () -> new Problem(entity.problem, fields.getNestedField("problem", Fields.SHORT, Fields.SHORT), beanContext));
    } else if (entity.agent != null) {
      agent = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("agent"), () -> new Agent(entity.agent, beanContext.getSingletonService(AgentPoolFinder.class), fields.getNestedField("agent", Fields.SHORT, Fields.SHORT), beanContext));
    } else if (entity.vcsRoot != null) {
      vcsRoot = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("vcsRoot"), () -> new VcsRoot(entity.vcsRoot, fields.getNestedField("vcsRoot", Fields.SHORT, Fields.SHORT), beanContext));
    } else if (entity.change != null) {
      change = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("change"), () -> new Change(entity.change, fields.getNestedField("change", Fields.SHORT, Fields.SHORT), beanContext));
    }
  }

  public static class Entity {
    @Nullable private String type;

    @Nullable private Unknown unknown;
    @Nullable private String text;
    @Nullable private BuildPromotion build;
    @Nullable private BuildTypeOrTemplate buildType;
    @Nullable private SProject project;
    @Nullable private jetbrains.buildServer.users.User user;
    @Nullable private SUserGroup userGroup;
    @Nullable private STest test;
    @Nullable private ProblemWrapper problem;
    @Nullable private SBuildAgent agent;
    @Nullable private SVcsRoot vcsRoot;
    @Nullable private SVcsModification change;

    public Entity(@NotNull final Object build) {
      if (build instanceof BuildPromotion) {
        this.build = (BuildPromotion)build;
      }
      else {
        throw new BadRequestException("Unsupported entity type \"" + build.getClass().getName() + "\"");
      }
    }

    private Entity() {}

    @Nullable
    public static Entity getFrom(@NotNull final ObjectWrapper objectWrapper, @NotNull final ServiceLocator serviceLocator) {
      Entity result = new Entity();

      Object object = objectWrapper.getObject();
      ObjectType objectType = objectWrapper.getObjectType();

      if (!ObjectType.STRING.equals(objectType) && object instanceof String) {
        result.text = (String)object;
        result.type = "text";
      } else  if (object != null) {
        switch (objectType) {
          case STRING:              result.type = "text"; result.text = (new StringHelper()).getObject(object); break;
          case BUILD_PROMOTION:     result.type = "build"; result.build = (new BuildPromotionHelper()).getObject(object); break;
          case BUILD:               result.type = "build"; result.build = (new BuildHelper()).getObject(object).getBuildPromotion();  break;
          case BUILD_TYPE:          result.type = "buildType"; result.buildType = new BuildTypeOrTemplate((SBuildType)(new BuildTypeHelper()).getObject(object));  break;
          case BUILD_TYPE_TEMPLATE: result.type = "buildType"; result.buildType = new BuildTypeOrTemplate((new BuildTypeTemplateHelper()).getObject(object)); break;
          case PROJECT:             result.type = "project"; result.project = (new ProjectHelper()).getObject(object); break;
          case USER:                result.type = "user"; result.user = (new UserHelper()).getObject(object); break;
          case USER_GROUP:          result.type = "userGroup"; result.userGroup = (new UserGroupHelper()).getObject(object); break;
          case TEST:                result.type = "test"; result.test = (new TestHelper()).getObject(object); break;
          case BUILD_PROBLEM:       result.type = "problem";  result.problem = new ProblemWrapper((new BuildProblemHelper()).getObject(object).getId(), serviceLocator); break;
          case AGENT:               result.type = "agent"; result.agent = (new AgentHelper()).getObject(object); break;
          case VCS_ROOT:            result.type = "vcsRoot"; result.vcsRoot = (new VcsRootHelper()).getObject(object); break;
          case VCS_MODIFICATION:    result.type = "change"; result.change = (SVcsModification)(new VcsModificationHelper()).getObject(object); break;
          case SERVER:              return null;

          case UNKNOWN_OBJECT:
          default:
            result.type = "unknown";
            result.unknown = new Unknown(objectWrapper.getObjectId(), objectType.name().toLowerCase());
            break;
        }
      }
      return result;
    }
  }

  private static class Unknown {
    @Nullable private String id;
    @Nullable private String type;

    public Unknown(@Nullable final String id, @Nullable final String type) {
      this.id = "NO_ID".equals(id) ? null : id;
      this.type = type;
    }
  }
}