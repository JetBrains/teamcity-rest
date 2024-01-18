/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.agent.Agent;
import jetbrains.buildServer.server.rest.model.agent.AgentPool;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.model.change.Change;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;
import jetbrains.buildServer.server.rest.model.group.Group;
import jetbrains.buildServer.server.rest.model.problem.Problem;
import jetbrains.buildServer.server.rest.model.problem.Test;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.model.user.User;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.audit.ObjectType;
import jetbrains.buildServer.serverSide.audit.ObjectWrapper;
import jetbrains.buildServer.serverSide.problems.BuildProblemInfo;
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
@ModelDescription("Represents a related entity.")
public class RelatedEntity { //see also Related
  @XmlAttribute(name = "type")
  private String type;

  @XmlAttribute(name = "unknown")
  public Boolean unknown;

  /**
   * experimental.
   * Internal id of the entity
   */
  @XmlAttribute(name = "internalId")
  private String internalId;


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

  @XmlElement(name = "agentPool")
  public AgentPool agentPool;

  @SuppressWarnings("unused")
  public RelatedEntity() {
  }

  public RelatedEntity(@NotNull final Entity entity, @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    type = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("type"), entity.type);
    internalId = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("internalId", false, entity.unknown), entity.internalId);
    unknown = ValueWithDefault.decideDefault(fields.isIncluded("unknown"), entity.unknown);

    if (entity.text != null) {
      text = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("text"), entity.text);
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
      agent = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("agent"), () -> new Agent(entity.agent, fields.getNestedField("agent", Fields.SHORT, Fields.SHORT), beanContext));
    } else if (entity.vcsRoot != null) {
      vcsRoot = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("vcsRoot"), () -> new VcsRoot(entity.vcsRoot, fields.getNestedField("vcsRoot", Fields.SHORT, Fields.SHORT), beanContext));
    } else if (entity.change != null) {
      change = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("change"), () -> new Change(entity.change, fields.getNestedField("change", Fields.SHORT, Fields.SHORT), beanContext));
    } else if (entity.agentPool != null) {
      agentPool = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("agentPool"), () -> new AgentPool(entity.agentPool, fields.getNestedField("agentPool", Fields.SHORT, Fields.SHORT), beanContext));
    }
  }

  public static class Entity {
    @Nullable private String type;
    @Nullable private String internalId; //type-specific internalId
    @Nullable private boolean unknown; //true if the types of the entity is unknown

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
    @Nullable private jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool;

    public Entity(@NotNull final Object build) {
      if (build instanceof BuildPromotion) {
        this.build = (BuildPromotion)build;
      }
      else {
        throw new BadRequestException("Unsupported entity type \"" + build.getClass().getName() + "\"");
      }
    }

    private Entity() {}

    @NotNull
    public static Entity getFrom(@NotNull final ObjectWrapper objectWrapper, @NotNull final ServiceLocator serviceLocator) {
      Entity result = new Entity();

      Object object = objectWrapper.getObject();
      ObjectType objectType = objectWrapper.getObjectType();

      result.internalId = "NO_ID".equals(objectWrapper.getObjectId()) ? null : objectWrapper.getObjectId();
      result.type = getType(objectType);
      result.unknown = false;

      if (!ObjectType.STRING.equals(objectType) && object instanceof String) {
        //todo: investigate when this can happen
        result.text = (String)object; //todo: is this a due approach?
        result.type = "text";
      } else if (object != null) {
        switch (objectType) {
          case STRING:              result.text = (String)object; break;  //todo: check where it is used
          case BUILD_PROMOTION:     result.build = (BuildPromotion)object; break;
          case BUILD:               result.build = ((SBuild)object).getBuildPromotion();  break;
          case BUILD_TYPE:          result.buildType = new BuildTypeOrTemplate((SBuildType)object);  break;
          case BUILD_TYPE_TEMPLATE: result.buildType = new BuildTypeOrTemplate((BuildTypeTemplate)object); break;
          case PROJECT:             result.project = (SProject)object; break;
          case USER:                result.user = (jetbrains.buildServer.users.User)object; break;
          case USER_GROUP:          result.userGroup = (SUserGroup)object; break;
          case TEST:                result.test = (STest)object; break;
          case BUILD_PROBLEM:       result.problem = new ProblemWrapper(((BuildProblemInfo)object).getId(), serviceLocator); break;
          case AGENT:               result.agent = (SBuildAgent)object; break;
          case VCS_ROOT:            result.vcsRoot = (SVcsRoot)object; break;
          case VCS_MODIFICATION:    result.change = (SVcsModification)object; break;
          case AGENT_POOL:          result.agentPool = (jetbrains.buildServer.serverSide.agentPools.AgentPool)object; break;

          case SERVER:              //this is usually used as "nop" and if present, affects the indexes in the pattern, so cannot be ignored
                                    result.internalId = null; break;
          case UNKNOWN_OBJECT:  //todo: check usages
          default:                  result.unknown = true;

          /*
          still unsupported:
          case USER_ROLE: result.role = (new UserRoleHelper()).getObject(object);  break;
          case AGENT_TYPE: result.agent = (new AgentTypeHelper()).getObject(object);  break;
          case CONFIG_MODIFICATION: result.config_modification = (new ConfigModificationHelper()).getObject(object);  break;
          case HEALTH_STATUS_ITEM: result.healthItem = (new HealthStatusItemHelper()).getObject(object);  break;
          case RUN_TYPE: result.run_type = (new RunTypeItemHelper()).getObject(object);  break;
          case TOOL: result.tool = (new ToolHelper()).getObject(object);  break;
          */

          //todo: add test that we support here all the types from ObjectType
          //todo: special case when object is String (can have any type)
          //todo: handle exceptions from helpers / on casts
          //todo: need to check permissions?
        }
      }
      return result;
    }
  }

  /**
   * should correspond to getObjectType, getSupportedObjectTypes
   */
  @NotNull
  private static String getType(@NotNull ObjectType objectType) {
    switch (objectType) { //todo: add a test to assert the full set of the types
       case STRING:              return "text";
       case BUILD_PROMOTION:     return "build";
       case BUILD:               return "build";
       case BUILD_TYPE:          return "buildType";
       case BUILD_TYPE_TEMPLATE: return "buildType";
       case PROJECT:             return "project";
       case USER:                return "user";
       case USER_GROUP:          return "userGroup";
       case TEST:                return "test";
       case BUILD_PROBLEM:       return "problem";
       case AGENT:               return "agent";
       case VCS_ROOT:            return "vcsRoot";
       case VCS_MODIFICATION:    return "change";
       case SERVER:              return "empty";//used as "nop"

       case USER_ROLE:           return "role";
       case AGENT_TYPE:          return "agentType";
       case AGENT_POOL:          return "agentPool";
       case CONFIG_MODIFICATION: return "settingsChange";
       case HEALTH_STATUS_ITEM:  return "healthItem";
       case RUN_TYPE:            return "metaRunner";
       case TOOL:                return "agentTool";

       case UNKNOWN_OBJECT:
       default:                  return "unknown";
     }
  }

  /**
   * reverse for getType, considering expandTypes method
   * should correspond to getType, getSupportedObjectTypes
   */
  @NotNull
  public static ObjectType getObjectType(@NotNull String type) {
    switch (type) {
       case "text":           return ObjectType.STRING;
       case "build":          return ObjectType.BUILD_PROMOTION;
       case "buildType":      return ObjectType.BUILD_TYPE;
       case "project":        return ObjectType.PROJECT;
       case "user":           return ObjectType.USER;
       case "userGroup":      return ObjectType.USER_GROUP;
       case "test":           return ObjectType.TEST;
       case "problem":        return ObjectType.BUILD_PROBLEM;
       case "agent":          return ObjectType.AGENT;
       case "vcsRoot":        return ObjectType.VCS_ROOT;
       case "change":         return ObjectType.VCS_MODIFICATION;
       case "empty":          return ObjectType.SERVER;
       case "role":           return ObjectType.USER_ROLE;
       case "agentType":      return ObjectType.AGENT_TYPE;
       case "agentPool":      return ObjectType.AGENT_POOL;
       case "settingsChange": return ObjectType.CONFIG_MODIFICATION;
       case "healthItem":     return ObjectType.HEALTH_STATUS_ITEM;
       case "metaRunner":     return ObjectType.RUN_TYPE;
       case "agentTool":      return ObjectType.TOOL;
       //UNKNOWN_OBJECT:
       default:               throw new BadRequestException("Unknown entity type \"" + type + "\". Supported are: " + getSupportedObjectTypes());
     }
  }


  @NotNull
  public static Set<ObjectType> expandTypes(@NotNull final Set<ObjectType> set) {
    if (set.contains(ObjectType.BUILD_PROMOTION)) set.add(ObjectType.BUILD);
    if (set.contains(ObjectType.BUILD_TYPE)) set.add(ObjectType.BUILD_TYPE_TEMPLATE);
    return set;
  }

  /**
   * should correspond to getType, getObjectType
   */
  public static List<String> getSupportedObjectTypes() {
    return Arrays.asList("text",
                         "build",
                         "buildType",
                         "project",
                         "user",
                         "userGroup",
                         "test",
                         "problem",
                         "agent",
                         "vcsRoot",
                         "change",
                         "empty",
                         "role",
                         "agentType",
                         "agentPool",
                         "settingsChange",
                         "healthItem",
                         "metaRunner",
                         "agentTool");
  }
}