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

import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.agent.AgentRef;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeRef;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.model.buildType.PropEntitiesArtifactDep;
import jetbrains.buildServer.server.rest.model.change.ChangeRef;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.dependency.BuildDependency;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.VcsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
//todo: add changes
//todo: reuse fields code from DataProvider
@XmlRootElement(name = "buildTask")
@XmlType(name = "buildTask", propOrder = {"branchName", "personal",
  "buildType", "agent", "commentText", "properties", "change", "personalChange", "buildDependencies", "buildArtifactDependencies", "rebuildDependencies"})
//"buildArtifactDependencies"
@SuppressWarnings("PublicField")
public class BuildTask {
  @XmlAttribute public String branchName;
  @XmlAttribute public Boolean personal;
  @XmlAttribute public Boolean cleanSources;
  @XmlAttribute public Boolean rebuildAllDependencies;

  @XmlAttribute public Boolean queueAtTop;
  @XmlElement public AgentRef agent;

  @XmlElement public BuildTypeRef buildType;
  @XmlElement public String commentText;
  @XmlElement public Properties properties;
  @XmlElement private ChangeRef change;
  @XmlElement(name = "snapshot-dependencies") public Builds buildDependencies;
  /**
   * Specifies which of the snapshot dependnecies to rebuild. Build types of direct or indirect dependencies can be specified.
   * Makes sence only if "rebuildAllDependencies" is not set to "true"
   */
  @XmlElement(name = "rebuild-dependencies") public BuildTypes rebuildDependencies;
  /**
   * Specifies artifact dependencies to use in the build _instead_ of those specified in the build type.
   */
  @XmlElement(name = "artifact-dependencies") public PropEntitiesArtifactDep buildArtifactDependencies;

  /**
   * Experimental only!
   */
  @XmlElement private ChangeRef personalChange;

  public BuildTask() {
  }

  //todo: support the same for queued build
  public static BuildTask getExampleBuildTask(@NotNull final SBuild build, @NotNull final BeanContext context) {
    final BuildTask buildTask = new BuildTask();
    if (build.getBranch() != null) buildTask.branchName = build.getBranch().getName();
    buildTask.personal = build.isPersonal();

    final Object cleanSourcesAttribute = ((BuildPromotionEx)build.getBuildPromotion()).getAttribute(BuildAttributes.CLEAN_SOURCES);
    if (cleanSourcesAttribute != null) buildTask.cleanSources = Boolean.valueOf((String)cleanSourcesAttribute);

//    buildTask.rebuildAllDependencies =
    //noinspection ConstantConditions
    buildTask.buildType = new BuildTypeRef(build.getBuildType(), context);
    buildTask.agent = new AgentRef(build.getAgent(), context.getApiUrlBuilder());
    if (build.getBuildComment() != null) buildTask.commentText = build.getBuildComment().getComment();
    buildTask.properties = new Properties(build.getBuildPromotion().getCustomParameters());
    final Long lastModificationId = build.getBuildPromotion().getLastModificationId();
    if (lastModificationId != null && lastModificationId > 0) {
      SVcsModification modification = context.getSingletonService(VcsManager.class).findModificationById(lastModificationId, false);
      if (modification != null) {
        buildTask.change = new ChangeRef(modification, context.getApiUrlBuilder(), context.getSingletonService(BeanFactory.class));
      }
    }
    if (build.isPersonal()) {
      final SVcsModification vcsModification = getPersonalChange(build);
      if (vcsModification != null) {
        buildTask.personalChange = new ChangeRef(vcsModification, context.getApiUrlBuilder(), context.getSingletonService(BeanFactory.class));
      }
    }

    final Collection<? extends BuildDependency> dependencies = build.getBuildPromotion().getDependencies();
    if (dependencies.size() > 0) {
      buildTask.buildDependencies = new Builds(Build.getBuilds(dependencies), context.getServiceLocator(), null, context.getApiUrlBuilder());
    }

    final List<SArtifactDependency> artifactDependencies = build.getArtifactDependencies();
    if (artifactDependencies.size() > 0) {
      buildTask.buildArtifactDependencies = new PropEntitiesArtifactDep(artifactDependencies, context);
    }

    return buildTask;
  }

  @Nullable
  private static SVcsModification getPersonalChange(@NotNull final SBuild build) {
    for (SVcsModification modification : build.getContainingChanges()) {
      if (modification.isPersonal()) return modification;
    }
    return null;
  }

  @Nullable
  public SBuildAgent getAgent(@NotNull final AgentFinder agentFinder) {
    if (agent == null) {
      return null;
    }
    return agent.getAgentFromPosted(agentFinder);
  }

  private SBuildType getBuildType(@NotNull final BuildTypeFinder buildTypeFinder, @NotNull ServiceLocator serviceLocator) {
    if (buildType == null) {
      throw new BadRequestException("No 'buildType' element in the posted entiry.");
    }
    final BuildTypeOrTemplate buildTypeFromPosted = buildType.getBuildTypeFromPosted(buildTypeFinder);
    if (!buildTypeFromPosted.isBuildType()) {
      throw new BadRequestException("Found template instead on build type. Only build types can run builds.");
    }
    final SBuildType regularBuildType = buildTypeFromPosted.getBuildType();
    if (personalChange == null) {
      return regularBuildType;
    }
    final SVcsModification personalChangeFromPosted = personalChange.getChangeFromPosted(serviceLocator.getSingletonService(ChangeFinder.class));
    final SUser currentUser = DataProvider.getCurrentUser(serviceLocator);
    if (currentUser == null) {
      throw new BadRequestException("Cannot trigger a personal build while no current user is present. Please specify credentials of a valid and non-special user.");
    }
    return ((BuildTypeEx)regularBuildType).createPersonalBuildType(currentUser, personalChangeFromPosted.getId());
  }

  public BuildPromotion getBuildToTrigger(@Nullable final SUser user, @NotNull final BuildTypeFinder buildTypeFinder, @NotNull final ServiceLocator serviceLocator) {
    BuildCustomizer customizer = serviceLocator.getSingletonService(BuildCustomizerFactory.class).createBuildCustomizer(getBuildType(buildTypeFinder, serviceLocator), user);
    if (commentText != null) customizer.setBuildComment(commentText);
    if (properties != null) customizer.setParameters(properties.getMap());

    if (branchName != null) customizer.setDesiredBranchName(branchName);
    if (personal != null) customizer.setPersonal(personal);
    if (cleanSources != null) customizer.setCleanSources(cleanSources);
    if (rebuildAllDependencies != null) customizer.setRebuildDependencies(rebuildAllDependencies);
    if (change != null) {
      customizer.setChangesUpTo(change.getChangeFromPosted(serviceLocator.getSingletonService(ChangeFinder.class)));
    }
    if (buildDependencies != null) {
      try {
        customizer.setSnapshotDependencyNodes(CollectionsUtil.convertCollection(buildDependencies.builds, new Converter<BuildPromotion, Build>() {
          public BuildPromotion createFrom(@NotNull final Build source) {
            return source.getFromPosted(serviceLocator.getSingletonService(BuildFinder.class), serviceLocator.getSingletonService(QueuedBuildFinder.class));
          }
        }));
      } catch (IllegalArgumentException e) {
        throw new BadRequestException("Erorr trying to use specified snapshot dependencies: " + e.getMessage());
      }
    }
    if (rebuildDependencies != null) {
      customizer.setRebuildDependencies(CollectionsUtil.convertCollection(rebuildDependencies.getFromPosted(buildTypeFinder), new Converter<String, BuildTypeOrTemplate>() {
        public String createFrom(@NotNull final BuildTypeOrTemplate source) {
          if (!source.isBuildType()) {
            throw new BadRequestException("Template is specified instead of a build type. Template id: '" + source.getTemplate().getExternalId() + "'");
          }
          return source.getBuildType().getInternalId();
        }
      }));
    }
    if (buildArtifactDependencies != null){
      customizer.setArtifactDependencies(buildArtifactDependencies.getFromPosted(serviceLocator));
    }

    return customizer.createPromotion();
  }
}