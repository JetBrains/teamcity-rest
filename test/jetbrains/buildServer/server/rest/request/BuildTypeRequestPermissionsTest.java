/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.request;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.artifacts.RevisionRules;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Property;
import jetbrains.buildServer.server.rest.model.buildType.*;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class BuildTypeRequestPermissionsTest extends BaseFinderTest<BuildTypeOrTemplate> {
  private BuildTypeRequest myBuildTypeRequest;
  private ProjectEx myTopProject;
  private SUser myProjectAdmin;
  private SUser myProjectViewer;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myBuildTypeRequest = new BuildTypeRequest();
    myBuildTypeRequest.initForTests(BaseFinderTest.getBeanContext(myFixture));

    myFixture.getServerSettings().setPerProjectPermissionsEnabled(true);
    myTopProject = createProject("top_project");

    myProjectViewer = createUser("project_viewer");
    myProjectViewer.addRole(RoleScope.projectScope(myTopProject.getProjectId()), getTestRoles().getProjectViewerRole());

    myProjectAdmin = createUser("project_admin");
    myProjectAdmin.addRole(RoleScope.projectScope(myTopProject.getProjectId()), getTestRoles().getProjectAdminRole());
  }

  @Test
  public void updateParamsRequiresPermission() throws Throwable {
    BuildTypeEx buildType = myTopProject.createBuildType("bt1"); buildType.persist();

    buildType.addParameter(new SimpleParameter("a1", "b"));
    buildType.addParameter(new SimpleParameter("a2", "b"));
    buildType.addParameter(new SimpleParameter("a3", "b"));

    String btLocator = "id:" + buildType.getExternalId();

    assertThrowsIfNoPermissions(
      "Parameter creation should require permissions to edit project",
      () -> myBuildTypeRequest.getParametersSubResource(btLocator)
                              .setParameter(new Property(new SimpleParameter("a4", "b"), false, Fields.LONG, myFixture), "$long")
    );

    assertThrowsIfNoPermissions(
      "Parameter removal should require permissions to edit project",
      () -> myBuildTypeRequest.getParametersSubResource(btLocator)
                              .deleteParameter("a3")
    );

    assertThrowsIfNoPermissions(
      "Parameter removal should require permissions to edit project",
      () -> myBuildTypeRequest.getParametersSubResource(btLocator)
                              .deleteAllParameters()
    );
  }

  @Test
  public void updateParamsWorksForAdmin() throws Throwable {
    BuildTypeEx buildType = myTopProject.createBuildType("bt1"); buildType.persist();

    buildType.addParameter(new SimpleParameter("a1", "b"));
    buildType.addParameter(new SimpleParameter("a2", "b"));
    buildType.addParameter(new SimpleParameter("a3", "b"));

    String btLocator = "id:" + buildType.getExternalId();

    assertWorksForAdmin(
      "Project admin should be able to create a parameter",
      () -> myBuildTypeRequest.getParametersSubResource(btLocator)
                              .setParameter(new Property(new SimpleParameter("a4", "b"), false, Fields.LONG, myFixture), "$long")
    );

    assertWorksForAdmin(
      "Project admin should be able to remove a parameter",
      () -> myBuildTypeRequest.getParametersSubResource(btLocator)
                              .deleteParameter("a3")
    );

    assertWorksForAdmin(
      "Project admin should be able to remove all parameters",
      () -> myBuildTypeRequest.getParametersSubResource(btLocator)
                              .deleteAllParameters()
    );
  }

  @Test
  public void updateStepsRequiresPermission() throws Throwable {
    BuildTypeEx buildType = myTopProject.createBuildType("bt1"); buildType.persist();

    buildType.addBuildRunner("runner1", "runner1", new HashMap<>());
    buildType.addBuildRunner("runner2", "runner2", new HashMap<>());
    SBuildRunnerDescriptor descriptor = buildType.addBuildRunner("runner3", "runner3", new HashMap<>());

    String btLocator = "id:" + buildType.getExternalId();

    PropEntityStep newStep = new PropEntityStep();
    newStep.id = "myuniqueid";
    newStep.name = "New Step";
    newStep.type = "newstep";

    assertThrowsIfNoPermissions(
      "Build step creation should require permissions to edit project",
      () -> myBuildTypeRequest.addStep(btLocator, "$long", newStep)
    );

    assertThrowsIfNoPermissions(
      "Build step removal should require permissions to edit project",
      () -> myBuildTypeRequest.deleteStep(btLocator, descriptor.getId())
    );


    PropEntitiesStep noSteps = new PropEntitiesStep();
    noSteps.count = 0;
    noSteps.propEntities = new ArrayList<>();

    assertThrowsIfNoPermissions(
      "replacing build steps should require permissions to edit project",
      () -> myBuildTypeRequest.replaceSteps(btLocator, "$long", noSteps)
    );
  }

  @Test
  public void updateStepsWorksForAdmin() throws Throwable {
    BuildTypeEx buildType = myTopProject.createBuildType("bt1"); buildType.persist();

    buildType.addBuildRunner("runner1", "runner1", new HashMap<>());
    buildType.addBuildRunner("runner2", "runner2", new HashMap<>());
    SBuildRunnerDescriptor descriptor = buildType.addBuildRunner("runner3", "runner3", new HashMap<>());

    String btLocator = "id:" + buildType.getExternalId();

    PropEntityStep newStep = new PropEntityStep();
    newStep.id = "myuniqueid";
    newStep.name = "New Step";
    newStep.type = "newstep";

    assertWorksForAdmin(
      "Project admin should be able to create a build step",
      () -> myBuildTypeRequest.addStep(btLocator, "$long", newStep)
    );

    assertWorksForAdmin(
      "Project admin should be able to remove a build step",
      () -> myBuildTypeRequest.deleteStep(btLocator, descriptor.getId())
    );


    PropEntitiesStep noSteps = new PropEntitiesStep();
    noSteps.count = 0;
    noSteps.propEntities = new ArrayList<>();

    assertWorksForAdmin(
      "Project admin should be able to replace all build steps",
      () -> myBuildTypeRequest.replaceSteps(btLocator, "$long", noSteps)
    );
  }

  @Test
  public void updateTriggersRequiresPermission() throws Throwable {
    BuildTypeEx buildType = myTopProject.createBuildType("bt1"); buildType.persist();
    String btLocator = "id:" + buildType.getExternalId();

    buildType.addBuildTrigger(createTriggerDescriptor("tr1", Collections.singletonMap("p1", "v1")));
    buildType.addBuildTrigger(createTriggerDescriptor("tr2", Collections.singletonMap("p2", "v2")));

    BuildTriggerDescriptor descriptor = createTriggerDescriptor("tr3", Collections.singletonMap("p3", "v3"));
    buildType.addBuildTrigger(descriptor);

    PropEntityTrigger newTrigger = new PropEntityTrigger();
    newTrigger.name = "newtrigger";
    newTrigger.type = "newtrigger";

    assertThrowsIfNoPermissions(
      "Trigger creation should require permissions to edit project",
      () -> myBuildTypeRequest.addTrigger(btLocator, "$long", newTrigger)
    );

    assertThrowsIfNoPermissions(
      "Trigger removal should require permissions to edit project",
      () -> myBuildTypeRequest.deleteTrigger(btLocator, descriptor.getId())
    );

    PropEntitiesTrigger noTriggers = new PropEntitiesTrigger();
    noTriggers.propEntities = new ArrayList<>();
    assertThrowsIfNoPermissions(
      "Trigger replacement should require permissions to edit project",
      () -> myBuildTypeRequest.replaceTriggers(btLocator, "$long", noTriggers)
    );
  }

  @Test
  public void updateTriggersWorksForAdmin() throws Throwable {
    BuildTypeEx buildType = myTopProject.createBuildType("bt1"); buildType.persist();
    String btLocator = "id:" + buildType.getExternalId();

    buildType.addBuildTrigger(createTriggerDescriptor("tr1", Collections.singletonMap("p1", "v1")));
    buildType.addBuildTrigger(createTriggerDescriptor("tr2", Collections.singletonMap("p2", "v2")));

    BuildTriggerDescriptor descriptor = createTriggerDescriptor("tr3", Collections.singletonMap("p3", "v3"));
    buildType.addBuildTrigger(descriptor);

    PropEntityTrigger newTrigger = new PropEntityTrigger();
    newTrigger.name = "newtrigger";
    newTrigger.type = "newtrigger";

    assertWorksForAdmin(
      "Project admin should be able to create trigger",
      () -> myBuildTypeRequest.addTrigger(btLocator, "$long", newTrigger)
    );

    assertThrowsIfNoPermissions(
      "Project admin should be able to remove trigger",
      () -> myBuildTypeRequest.deleteTrigger(btLocator, descriptor.getId())
    );

    PropEntitiesTrigger noTriggers = new PropEntitiesTrigger();
    noTriggers.propEntities = new ArrayList<>();
    assertThrowsIfNoPermissions(
      "Project admin should be able to replace triggers",
      () -> myBuildTypeRequest.replaceTriggers(btLocator, "$long", noTriggers)
    );
  }

  @Test
  public void updateArtifactDepsRequiresPermission() throws Throwable {
    BuildTypeEx buildType = myTopProject.createBuildType("bt1"); buildType.persist();
    String btLocator = "id:" + buildType.getExternalId();


    PropEntityArtifactDep newDep = new PropEntityArtifactDep();
    newDep.sourceBuildType = new BuildType();
    newDep.sourceBuildType.setId(buildType.getExternalId());
    newDep.type = "artifact_dependency";
    newDep.properties = new Properties();
    newDep.properties.properties = Arrays.asList(new Property(new SimpleParameter("revisionName", "aaa"), false, Fields.LONG, myFixture),
                                                 new Property(new SimpleParameter("revisionValue", "aaa"), false, Fields.LONG, myFixture),
                                                 new Property(new SimpleParameter("pathRules", "aaa"), false, Fields.LONG, myFixture));


    assertThrowsIfNoPermissions(
      "Artifact dep creation should require permissions to edit project",
      () -> myBuildTypeRequest.addArtifactDep(btLocator, "$long", newDep)
    );

    SArtifactDependency dependency = createArtifactDependency(myTopProject.createBuildType("dep"));
    buildType.addArtifactDependency(dependency);
    assertThrowsIfNoPermissions(
      "Artifact dep removal should require permissions to edit project",
      () -> myBuildTypeRequest.deleteArtifactDep(btLocator, dependency.getId())
    );

    PropEntitiesArtifactDep noDeps = new PropEntitiesArtifactDep();
    noDeps.propEntities = new ArrayList<>();
    assertThrowsIfNoPermissions(
      "Modifying artifact deps should require permissions to edit project",
      () -> myBuildTypeRequest.replaceArtifactDeps(btLocator, "$long", noDeps)
    );
  }

  @Test
  public void updateArtifactDepsWorksForAdmin() throws Throwable {
    BuildTypeEx buildType = myTopProject.createBuildType("bt1"); buildType.persist();
    String btLocator = "id:" + buildType.getExternalId();


    PropEntityArtifactDep newDep = new PropEntityArtifactDep();
    newDep.sourceBuildType = new BuildType();
    newDep.sourceBuildType.setId(buildType.getExternalId());
    newDep.type = "artifact_dependency";
    newDep.properties = new Properties();
    newDep.properties.properties = Arrays.asList(new Property(new SimpleParameter("revisionName", "aaa"), false, Fields.LONG, myFixture),
                                                 new Property(new SimpleParameter("revisionValue", "aaa"), false, Fields.LONG, myFixture),
                                                 new Property(new SimpleParameter("pathRules", "aaa"), false, Fields.LONG, myFixture));


    assertWorksForAdmin(
      "Project admin should be able to create an artifact dep",
      () -> myBuildTypeRequest.addArtifactDep(btLocator, "$long", newDep)
    );

    SArtifactDependency dependency = createArtifactDependency(myTopProject.createBuildType("dep"));
    buildType.addArtifactDependency(dependency);
    assertThrowsIfNoPermissions(
      "Project admin should be able to  remove an artifact dep",
      () -> myBuildTypeRequest.deleteArtifactDep(btLocator, dependency.getId())
    );

    PropEntitiesArtifactDep noDeps = new PropEntitiesArtifactDep();
    noDeps.propEntities = new ArrayList<>();
    assertThrowsIfNoPermissions(
      "Project admin should be able to replace artifact deps",
      () -> myBuildTypeRequest.replaceArtifactDeps(btLocator, "$long", noDeps)
    );
  }

  @Test
  public void updateAgentRequirementsRequiresPermission() throws Throwable {
    BuildTypeEx buildType = myTopProject.createBuildType("bt1"); buildType.persist();
    String btLocator = "id:" + buildType.getExternalId();

    buildType.addRequirement(new Requirement("prop1", null, RequirementType.EXISTS));
    buildType.addRequirement(new Requirement("req2", "prop2", null, RequirementType.EXISTS));

    PropEntityAgentRequirement newRequirement = new PropEntityAgentRequirement();
    newRequirement.type = "not-exists";
    newRequirement.disabled = true;
    newRequirement.properties = new Properties();
    newRequirement.properties.properties = Collections.singletonList(new Property(new SimpleParameter("property-name", "aaa"), false, Fields.LONG, myFixture));

    assertThrowsIfNoPermissions(
      "Adding agent requirement should require permissions to edit project",
      () -> myBuildTypeRequest.addAgentRequirement(btLocator, "$long", newRequirement)
    );

    assertThrowsIfNoPermissions(
      "Removing agetnt requirement should require permissions to edit project",
      () -> myBuildTypeRequest.deleteAgentRequirement(btLocator, "req2")
    );

    PropEntitiesAgentRequirement noReqs = new PropEntitiesAgentRequirement();
    noReqs.propEntities = new ArrayList<>();
    assertThrowsIfNoPermissions(
      "Replacing agent requirements should require permissions to edit project",
      () -> myBuildTypeRequest.replaceAgentRequirements(btLocator, "$long", noReqs)
    );
  }

  @Test
  public void updateAgentRequirementsWorksForAdmin() throws Throwable {
    BuildTypeEx buildType = myTopProject.createBuildType("bt1"); buildType.persist();
    String btLocator = "id:" + buildType.getExternalId();

    buildType.addRequirement(new Requirement("prop1", null, RequirementType.EXISTS));
    buildType.addRequirement(new Requirement("req2", "prop2", null, RequirementType.EXISTS));

    PropEntityAgentRequirement newRequirement = new PropEntityAgentRequirement();
    newRequirement.type = "not-exists";
    newRequirement.disabled = true;
    newRequirement.properties = new Properties();
    newRequirement.properties.properties = Collections.singletonList(new Property(new SimpleParameter("property-name", "aaa"), false, Fields.LONG, myFixture));

    assertWorksForAdmin(
      "Project admin should be able to add an agent requirement",
      () -> myBuildTypeRequest.addAgentRequirement(btLocator, "$long", newRequirement)
    );

    assertWorksForAdmin(
      "Project admin should be able to remove an agent requirement",
      () -> myBuildTypeRequest.deleteAgentRequirement(btLocator, "req2")
    );

    PropEntitiesAgentRequirement noReqs = new PropEntitiesAgentRequirement();
    noReqs.propEntities = new ArrayList<>();
    assertWorksForAdmin(
      "Project admin should be able to replace agent requirements",
      () -> myBuildTypeRequest.replaceAgentRequirements(btLocator, "$long", noReqs)
    );
  }

  private SArtifactDependency createArtifactDependency(final SBuildType bt1) {
    return myFixture.getSingletonService(ArtifactDependencyFactory.class).createArtifactDependency(bt1, "aa=>dest1", RevisionRules.LAST_FINISHED_RULE);
  }

  private void assertThrowsIfNoPermissions(@NotNull String message, @NotNull Runnable action) throws Throwable {
    AtomicBoolean thrown = new AtomicBoolean(false);
    myFixture.getSecurityContext().runAs(myProjectViewer, () -> {
      try {
        action.run();
      } catch (AuthorizationFailedException ade) {
        thrown.set(true);
      }
    });

    assertTrue(message, thrown.get());
  }

  private void assertWorksForAdmin(@NotNull String message, @NotNull Runnable action) throws Throwable {
    try {
      myFixture.getSecurityContext().runAs(myProjectAdmin, () -> action.run());
    } catch (AuthorizationFailedException afe) {
      fail(message + " " + afe.getMessage());
    }
  }
}
