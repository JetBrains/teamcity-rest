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

package jetbrains.buildServer.server.rest.request;

import java.util.Arrays;
import java.util.List;
import jetbrains.buildServer.artifacts.RevisionRules;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Property;
import jetbrains.buildServer.server.rest.model.buildType.*;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.ArtifactDependencyFactory;
import jetbrains.buildServer.serverSide.BuildFeature;
import jetbrains.buildServer.serverSide.BuildTypeSettingsAdapter;
import jetbrains.buildServer.serverSide.SimpleParameter;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.dependency.DependencyFactory;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 01/04/2016
 */
public class BuildTypeRequestTest extends  BaseFinderTest<BuildTypeOrTemplate> {
  private BuildTypeRequest myBuildTypeRequest;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myBuildTypeRequest = new BuildTypeRequest();
    myBuildTypeRequest.initForTests(BaseFinderTest.getBeanContext(myFixture));
  }

  @Test
  public void testUpdatingParameters() {
    BuildTypeImpl buildType1 = registerBuildType("buildType1", "projectName");

    buildType1.addParameter(new SimpleParameter("a1", "b"));
    buildType1.addParameter(new SimpleParameter("a2", "b"));
    buildType1.addParameter(new SimpleParameter("a3", "b"));

    final String btLocator = "id:" + buildType1.getExternalId();

    assertEquals(3, myBuildTypeRequest.getParametersSubResource(btLocator).getParameters(null, "$long,property($long)").properties.size());
    myBuildTypeRequest.getParametersSubResource(btLocator).setParameter(new Property("a4", "b", Fields.LONG), "$long");
    assertEquals(4, myBuildTypeRequest.getParametersSubResource(btLocator).getParameters(null, "$long,property($long)").properties.size());
    myBuildTypeRequest.getParametersSubResource(btLocator).deleteParameter("a3");
    assertEquals(3, myBuildTypeRequest.getParametersSubResource(btLocator).getParameters(null, "$long,property($long)").properties.size());

    {
      Properties submitted = new Properties();
      submitted.properties = Arrays.asList(new Property("n1", null, Fields.LONG));

      checkException(BadRequestException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.getParametersSubResource(btLocator).setParameters(submitted, "$long");
        }
      }, null);

      assertEquals(3, myBuildTypeRequest.getParametersSubResource(btLocator).getParameters(null, "$long,property($long)").properties.size());
      assertEquals(3, buildType1.getParameters().size());
    }

    buildType1.getSettings().addListener(new BuildTypeSettingsAdapter() {
      private int myTriggerOnCall = 1;

      @Override
      public void textValueChanged() {
        if (--myTriggerOnCall == 0) {
          throw new RuntimeException("I need error here ");
        }
      }
    });

    {
      Properties submitted = new Properties();
      submitted.properties = Arrays.asList(new Property("n1", "v1", Fields.LONG));

      checkException(BadRequestException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.getParametersSubResource(btLocator).setParameters(submitted, "$long");
        }
      }, null);

      assertEquals(3, myBuildTypeRequest.getParametersSubResource(btLocator).getParameters(null, "$long,property($long)").properties.size());
      assertEquals(3, buildType1.getParameters().size());
    }

    buildType1.getSettings().addListener(new BuildTypeSettingsAdapter() {
      private int myTriggerOnCall = 1;

      @Override
      public void textValueChanged() {
        if (--myTriggerOnCall == 0) {
          throw new RuntimeException("I need error here ");
        }
      }
    });

    checkException(RuntimeException.class, new Runnable() {
      public void run() {
        myBuildTypeRequest.getParametersSubResource(btLocator).setParameter(new Property("n1", "v1", Fields.LONG), "$long");
      }
    }, null);

    assertEquals(3, myBuildTypeRequest.getParametersSubResource(btLocator).getParameters(null, "$long,property($long)").properties.size());
    assertEquals(3, buildType1.getParameters().size());

    myBuildTypeRequest.getParametersSubResource(btLocator).setParameters(new Properties(), "$long");
    assertEquals(0, buildType1.getParameters().size());
  }

  @Test
  public void testUpdatingParametersWithTemplate1() {
    BuildTypeImpl buildType1 = registerTemplateBasedBuildType("buildType1");

    buildType1.addParameter(new SimpleParameter("a1", "b10"));
    buildType1.addParameter(new SimpleParameter("a2", "b9"));
    buildType1.addParameter(new SimpleParameter("a3", "b8"));
    buildType1.getTemplate().addParameter(new SimpleParameter("a1", "a7"));
    buildType1.getTemplate().addParameter(new SimpleParameter("a2", "b9"));
    buildType1.getTemplate().addParameter(new SimpleParameter("t1", "a5"));

    final String btLocator = "id:" + buildType1.getExternalId();
    final String templateLocator = "id:" + buildType1.getTemplate().getExternalId() + ",templateFlag:true";

    assertEquals(4, myBuildTypeRequest.getParametersSubResource(btLocator).getParameters(null, "$long,property($long)").properties.size());
    assertEquals(3, myBuildTypeRequest.getParametersSubResource(templateLocator).getParameters(null, "$long,property($long)").properties.size());

    myBuildTypeRequest.getParametersSubResource(btLocator).setParameter(new Property("a4", "b", Fields.LONG), "$long");
    assertEquals(5, myBuildTypeRequest.getParametersSubResource(btLocator).getParameters(null, "$long,property($long)").properties.size());
    assertEquals(3, myBuildTypeRequest.getParametersSubResource(templateLocator).getParameters(null, "$long,property($long)").properties.size());

    myBuildTypeRequest.getParametersSubResource(btLocator).deleteParameter("a4");
    assertEquals(4, myBuildTypeRequest.getParametersSubResource(btLocator).getParameters(null, "$long,property($long)").properties.size());
    assertEquals("b10", myBuildTypeRequest.getParametersSubResource(btLocator).getParameters(null, "$long,property($long)").properties.get(0).value);

    myBuildTypeRequest.getParametersSubResource(btLocator).deleteParameter("a1"); //param from template is not deleted, but reset
    assertEquals(4, myBuildTypeRequest.getParametersSubResource(btLocator).getParameters(null, "$long,property($long)").properties.size());
    assertEquals("a7", myBuildTypeRequest.getParametersSubResource(btLocator).getParameters(null, "$long,property($long)").properties.get(0).value);
    assertEquals(3, myBuildTypeRequest.getParametersSubResource(templateLocator).getParameters(null, "$long,property($long)").properties.size());
  }

  @Test
  public void testUpdatingParametersWithTemplate2() {
    BuildTypeImpl buildType1 = registerTemplateBasedBuildType("buildType1");

    buildType1.addParameter(new SimpleParameter("a1", "b10"));
    buildType1.addParameter(new SimpleParameter("a2", "b9"));
    buildType1.addParameter(new SimpleParameter("a3", "b8"));
    buildType1.getTemplate().addParameter(new SimpleParameter("a1", "a7"));
    buildType1.getTemplate().addParameter(new SimpleParameter("a2", "b9"));
    buildType1.getTemplate().addParameter(new SimpleParameter("t1", "a5"));

    final String btLocator = "id:" + buildType1.getExternalId();
    final String templateLocator = "id:" + buildType1.getTemplate().getExternalId() + ",templateFlag:true";

    assertEquals(4, myBuildTypeRequest.getParametersSubResource(btLocator).getParameters(null, "$long,property($long)").properties.size());
    assertEquals(3, myBuildTypeRequest.getParametersSubResource(templateLocator).getParameters(null, "$long,property($long)").properties.size());

    buildType1.getSettings().addListener(new BuildTypeSettingsAdapter() {
      private int myTriggerOnCall = 1;

      @Override
      public void textValueChanged() {
        if (--myTriggerOnCall == 0) {
          throw new RuntimeException("I need error here ");
        }
      }
    });

    assertEquals(3, buildType1.getOwnParameters().size());

    {
      Properties submitted = new Properties();
      submitted.properties = Arrays.asList(new Property("n1", "v1", Fields.LONG));

      checkException(BadRequestException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.getParametersSubResource(btLocator).setParameters(submitted, "$long");
        }
      }, null);

      assertEquals(4, buildType1.getParameters().size());
      assertEquals(2, buildType1.getOwnParameters().size()); //should be 3, but one is inlined on restore...
    }

    buildType1.getSettings().addListener(new BuildTypeSettingsAdapter() {
      private int myTriggerOnCall = 1;

      @Override
      public void textValueChanged() {
        if (--myTriggerOnCall == 0) {
          throw new RuntimeException("I need error here ");
        }
      }
    });

    checkException(RuntimeException.class, new Runnable() {
      public void run() {
        myBuildTypeRequest.getParametersSubResource(btLocator).setParameter(new Property("n1", "v1", Fields.LONG), "$long");
      }
    }, null);

    assertEquals(4, buildType1.getParameters().size());
    assertEquals(2, buildType1.getOwnParameters().size());   //should be 3, but one is inlined on restore...

    myBuildTypeRequest.getParametersSubResource(btLocator).setParameters(new Properties(), "$long");
    assertEquals(3, buildType1.getParameters().size());
    assertEquals(0, buildType1.getOwnParameters().size());
  }

  @Test
  public void testUpdatingSteps() {
    BuildTypeImpl buildType1 = registerBuildType("buildType1", "projectName");

    buildType1.addBuildRunner("name1", "runnerType1", createMap("a", "b"));
    String disabledId = buildType1.addBuildRunner("name2", "runnerType1", createMap("a", "b")).getId();
    buildType1.setEnabled(disabledId, false);
    buildType1.addBuildRunner("name3", "runnerType1", createMap("a", "b"));

    final String btLocator = "id:" + buildType1.getExternalId();

    assertEquals(3, myBuildTypeRequest.getSteps(btLocator, "$long,step($long)").propEntities.size());
    {
      PropEntityStep submitted = new PropEntityStep();
      submitted.name = "name4";
      submitted.type = "runnerType1";
      submitted.disabled = true;
      String newId = myBuildTypeRequest.addStep(btLocator, "$long,step($long)", submitted).id;
      List<PropEntityStep> steps = myBuildTypeRequest.getSteps(btLocator, "$long,step($long)").propEntities;
      assertEquals(4, steps.size());
      assertTrue(steps.get(3).disabled);
      myBuildTypeRequest.deleteStep(btLocator, newId);
      assertEquals(3, myBuildTypeRequest.getSteps(btLocator, "$long,step($long)").propEntities.size());
    }

    buildType1.getSettings().addListener(new BuildTypeSettingsAdapter() {
      private int myTriggerOnCall = 4;

      @Override
      public void newBuildRunnersOrderApplied(@NotNull final String[] ids) {
        if (--myTriggerOnCall == 0) {
          throw new RuntimeException("I need error here ");
        }
      }
    });

    {
      PropEntitiesStep submitted = new PropEntitiesStep();
      PropEntityStep submitted1 = new PropEntityStep();
      submitted1.type = "a";
      PropEntityStep submitted2 = new PropEntityStep();
      submitted2.type = "b";
      submitted.propEntities = Arrays.asList(submitted1, submitted2);

      checkException(BadRequestException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.replaceSteps(btLocator, "$long", submitted);
        }
      }, null);

      assertEquals(3, myBuildTypeRequest.getSteps(btLocator, "$long,step($long)").propEntities.size());
      assertEquals(3, buildType1.getBuildRunners().size());
      assertFalse(buildType1.isEnabled(disabledId));
    }

    buildType1.getSettings().addListener(new BuildTypeSettingsAdapter() {
      private int myTriggerOnCall = 1;

      @Override
      public void newBuildRunnersOrderApplied(@NotNull final String[] ids) {
        if (--myTriggerOnCall == 0) {
          throw new RuntimeException("I need error here ");
        }
      }
    });

    {
      PropEntityStep submitted = new PropEntityStep();
      submitted.type = "a";

      checkException(RuntimeException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.addStep(btLocator, "$long", submitted);
        }
      }, null);

      assertEquals(3, myBuildTypeRequest.getSteps(btLocator, "$long,step($long)").propEntities.size());
      assertEquals(3, buildType1.getBuildRunners().size());
      assertFalse(buildType1.isEnabled(disabledId));
    }

    myBuildTypeRequest.replaceSteps(btLocator, "$long", new PropEntitiesStep());
    assertEquals(0, buildType1.getBuildRunners().size());

  }


  @Test
  public void testUpdatingVcsRoots() {
    BuildTypeImpl buildType1 = registerBuildType("buildType1", "projectName");

    buildType1.addVcsRoot(createVcsRoot("name1", null));
    buildType1.addVcsRoot(createVcsRoot("name2", null));
    buildType1.addVcsRoot(createVcsRoot("name3", null));

    String newRootId = createVcsRoot("name4", null).getExternalId();

    final String btLocator = "id:" + buildType1.getExternalId();

    assertEquals(3, myBuildTypeRequest.getVcsRootEntries(btLocator, "$long,vcs-root-entry($long)").vcsRootAssignments.size());
    {
      VcsRootEntry submitted = new VcsRootEntry();
      submitted.vcsRoot = new VcsRoot();
      submitted.vcsRoot.id = newRootId;
      myBuildTypeRequest.addVcsRootEntry(btLocator, submitted, "$long");
      assertEquals(4, myBuildTypeRequest.getVcsRootEntries(btLocator, "$long,vcs-root-entry($long)").vcsRootAssignments.size());
      myBuildTypeRequest.deleteVcsRootEntry(btLocator, newRootId);
      assertEquals(3, myBuildTypeRequest.getVcsRootEntries(btLocator, "$long,vcs-root-entry($long)").vcsRootAssignments.size());
    }


    buildType1.getSettings().addListener(new BuildTypeSettingsAdapter() {
      private int myTriggerOnCall = 1;

      @Override
      public void afterAddVcsRoot(@NotNull final SVcsRoot vcsRoot) {
        if (--myTriggerOnCall == 0) {
          throw new RuntimeException("I need error here ");
        }
      }
    });

    {
      VcsRootEntries submitted = new VcsRootEntries();
      VcsRootEntry submitted1 = new VcsRootEntry();
      submitted1.vcsRoot = new VcsRoot();
      submitted1.vcsRoot.id = newRootId;
      submitted.vcsRootAssignments = Arrays.asList(submitted1);

      checkException(RuntimeException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.replaceVcsRootEntries(btLocator, submitted, "$long");
        }
      }, null);

      assertEquals(3, myBuildTypeRequest.getVcsRootEntries(btLocator, "$long,vcs-root-entry($long)").vcsRootAssignments.size());
      assertEquals(3, buildType1.getVcsRootEntries().size());
    }

    buildType1.getSettings().addListener(new BuildTypeSettingsAdapter() {
      private int myTriggerOnCall = 1;

      @Override
      public void afterAddVcsRoot(@NotNull final SVcsRoot vcsRoot) {
        if (--myTriggerOnCall == 0) {
          throw new RuntimeException("I need error here ");
        }
      }
    });

    {
      VcsRootEntry submitted = new VcsRootEntry();
      submitted.vcsRoot = new VcsRoot();
      submitted.vcsRoot.id = newRootId;

      checkException(RuntimeException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.addVcsRootEntry(btLocator, submitted, "$long");
        }
      }, null);

      assertEquals(3, myBuildTypeRequest.getVcsRootEntries(btLocator, "$long,vcs-root-entry($long)").vcsRootAssignments.size());
      assertEquals(3, buildType1.getVcsRootEntries().size());
    }

    {
      myBuildTypeRequest.replaceVcsRootEntries(btLocator, new VcsRootEntries(), "$long");
      assertEquals(0, buildType1.getVcsRootEntries().size());
    }
  }

  @Test
  public void testUpdatingFeatures() {
    BuildTypeImpl buildType1 = registerBuildType("buildType1", "projectName");

    buildType1.addBuildFeature("featureType1", createMap("a", "b"));
    String disabledFeatureId = buildType1.addBuildFeature("featureType2", createMap("a", "b")).getId();
    buildType1.setEnabled(disabledFeatureId, false);
    buildType1.addBuildFeature("featureType3", createMap("a", "b"));

    BuildFeature buildFeature = singletonBuildFeature();
    myServer.registerExtension(BuildFeature.class, "", buildFeature);

    final String btLocator = "id:" + buildType1.getExternalId();
    assertEquals(3, myBuildTypeRequest.getFeatures(btLocator, "$long,feature($long)").propEntities.size());
    {
      PropEntityFeature submitted = new PropEntityFeature();
      submitted.type = buildFeature.getType();
      submitted.disabled = true;
      String newId = myBuildTypeRequest.addFeature(btLocator, "$long", submitted).id;
      assertEquals(4, myBuildTypeRequest.getFeatures(btLocator, "$long,feature($long)").propEntities.size());
      assertTrue(myBuildTypeRequest.getFeatures(btLocator, "$long,feature($long)").propEntities.get(3).disabled);
      myBuildTypeRequest.deleteFeature(btLocator, newId);
      assertEquals(3, myBuildTypeRequest.getFeatures(btLocator, "$long,feature($long)").propEntities.size());
    }

    {
      PropEntitiesFeature submitted = new PropEntitiesFeature();
      PropEntityFeature submitted1 = new PropEntityFeature();
      submitted1.type = buildFeature.getType();
      PropEntityFeature submitted2 = new PropEntityFeature();
      submitted2.type = buildFeature.getType();
      submitted.propEntities = Arrays.asList(submitted1, submitted2); // two features of the type with isMultipleFeaturesPerBuildTypeAllowed==false will produce error on adding

      checkException(BadRequestException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.replaceFeatures(btLocator, "$long", submitted);
        }
      }, null);

      assertEquals(3, myBuildTypeRequest.getFeatures(btLocator, "$long,feature($long)").propEntities.size());
      assertEquals(3, buildType1.getBuildFeatures().size());
      assertFalse(buildType1.isEnabled(disabledFeatureId));
    }

    buildType1.getSettings().addListener(new BuildTypeSettingsAdapter() {
      private int myTriggerOnCall = 1;

      @Override
      public void textValueChanged() {
        if (--myTriggerOnCall == 0) {
          throw new RuntimeException("I need error here ");
        }
      }
    });

    {
      PropEntityFeature submitted = new PropEntityFeature();
      submitted.type = buildFeature.getType();

      checkException(BadRequestException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.addFeature(btLocator, "$long", submitted);
        }
      }, null);

      assertEquals(3, myBuildTypeRequest.getFeatures(btLocator, "$long,feature($long)").propEntities.size());
      assertEquals(3, buildType1.getBuildFeatures().size());
      assertFalse(buildType1.isEnabled(disabledFeatureId));
    }

    myBuildTypeRequest.replaceFeatures(btLocator, "$long", new PropEntitiesFeature());
    assertEquals(0, buildType1.getBuildFeatures().size());
  }

  @Test
  public void testUpdatingTriggers() {
    BuildTypeImpl buildType1 = registerBuildType("buildType1", "projectName");

    buildType1.addBuildTrigger("trigger1", createMap("a", "b"));
    String disabledTriggerId = buildType1.addBuildTrigger("trigger2", createMap("a", "b")).getId();
    buildType1.setEnabled(disabledTriggerId, false);
    buildType1.addBuildTrigger("trigger3", createMap("a", "b"));

    final String btLocator = "id:" + buildType1.getExternalId();
    assertEquals(3, myBuildTypeRequest.getTriggers(btLocator, "$long,trigger($long)").propEntities.size());
    {
      PropEntityTrigger submitted = new PropEntityTrigger();
      submitted.type = "triggerType1";
      String newId = myBuildTypeRequest.addTrigger(btLocator, "$long", submitted).id;
      assertEquals(4, myBuildTypeRequest.getTriggers(btLocator, "$long,trigger($long)").propEntities.size());
      myBuildTypeRequest.deleteTrigger(btLocator, newId);
      assertEquals(3, myBuildTypeRequest.getTriggers(btLocator, "$long,trigger($long)").propEntities.size());
    }

    buildType1.getSettings().addListener(new BuildTypeSettingsAdapter() {
      private boolean myAlreadyThrown = false;

      @Override
      public void afterAddBuildTrigger(@NotNull final BuildTriggerDescriptor btd) {
        if (!myAlreadyThrown) {
          myAlreadyThrown = true;
          throw new RuntimeException("I need error here ");
        }
      }
    });

    {
      PropEntitiesTrigger submitted = new PropEntitiesTrigger();
      PropEntityTrigger submitted1 = new PropEntityTrigger();
      submitted1.type = "triggerType1";
      PropEntityTrigger submitted2 = new PropEntityTrigger();
      submitted2.type = "triggerType1";
      submitted.propEntities = Arrays.asList(submitted1, submitted2);

      checkException(BadRequestException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.replaceTriggers(btLocator, "$long", submitted);
        }
      }, null);

      assertEquals(3, myBuildTypeRequest.getTriggers(btLocator, "$long,trigger($long)").propEntities.size());
      assertEquals(3, buildType1.getBuildTriggersCollection().size());
      assertFalse(buildType1.isEnabled(disabledTriggerId));
    }

    buildType1.getSettings().addListener(new BuildTypeSettingsAdapter() {
      private boolean myAlreadyThrown = false;

      @Override
      public void afterAddBuildTrigger(@NotNull final BuildTriggerDescriptor btd) {
        if (!myAlreadyThrown) {
          myAlreadyThrown = true;
          throw new RuntimeException("I need error here ");
        }
      }
    });

    {
      PropEntityTrigger submitted = new PropEntityTrigger();
      submitted.type = "triggerType1";

      checkException(RuntimeException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.addTrigger(btLocator, "$long", submitted);
        }
      }, null);

      assertEquals(3, myBuildTypeRequest.getTriggers(btLocator, "$long,trigger($long)").propEntities.size());
      assertEquals(3, buildType1.getBuildTriggersCollection().size());
      assertFalse(buildType1.isEnabled(disabledTriggerId));
    }

    myBuildTypeRequest.replaceTriggers(btLocator, "$long", new PropEntitiesTrigger());
    assertEquals(0, buildType1.getBuildTriggersCollection().size());
  }

  @Test
  public void testUpdatingAgentRequirements() {
    BuildTypeImpl buildType1 = registerBuildType("buildType1", "projectName");

    buildType1.addRequirement(new Requirement("prop1", null, RequirementType.EXISTS));
    String disabledId = "id2";
    buildType1.addRequirement(new Requirement(disabledId, "prop2", null, RequirementType.EXISTS));
    buildType1.setEnabled(disabledId, false);
    buildType1.addRequirement(new Requirement("prop3", null, RequirementType.EXISTS));

    final String btLocator = "id:" + buildType1.getExternalId();
    assertEquals(3, myBuildTypeRequest.getAgentRequirements(btLocator, "$long,agent-requirement($long)").propEntities.size());
    {
      PropEntityAgentRequirement submitted = new PropEntityAgentRequirement();
      submitted.type = "not-exists";
      submitted.disabled = true;
      submitted.properties = new Properties();
      submitted.properties.properties = Arrays.asList(new Property("property-name", "aaa", Fields.LONG));
      String newId = myBuildTypeRequest.addAgentRequirement(btLocator, "$long", submitted).id;
      assertEquals(4, myBuildTypeRequest.getAgentRequirements(btLocator, "$long,agent-requirement($long)").propEntities.size());
      assertTrue(myBuildTypeRequest.getAgentRequirements(btLocator, "$long,agent-requirement($long)").propEntities.get(3).disabled);
      myBuildTypeRequest.deleteAgentRequirement(btLocator, newId);
      assertEquals(3, myBuildTypeRequest.getAgentRequirements(btLocator, "$long,agent-requirement($long)").propEntities.size());
    }

    {
      PropEntitiesAgentRequirement submitted = new PropEntitiesAgentRequirement();
      PropEntityAgentRequirement submitted1 = new PropEntityAgentRequirement();
      submitted1.type = "agentRequirementType1";
      PropEntityAgentRequirement submitted2 = new PropEntityAgentRequirement();
      submitted2.type = "agentRequirementType1";
      submitted.propEntities = Arrays.asList(submitted1, submitted2);

      checkException(BadRequestException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.replaceAgentRequirements(btLocator, "$long",
                                                    submitted); // error will be reported: BadRequestException: No name is specified. Make sure 'property-name' property is present and has not empty value
        }
      }, null);

      assertEquals(3, myBuildTypeRequest.getAgentRequirements(btLocator, "$long,agent-requirement($long)").propEntities.size());
      assertEquals(3, buildType1.getRequirements().size());
      assertFalse(buildType1.isEnabled(disabledId));
    }

    {
      PropEntityAgentRequirement submitted = new PropEntityAgentRequirement();
      submitted.type = "agentRequirementType1";
      checkException(BadRequestException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.addAgentRequirement(btLocator, "$long", submitted);
        }
      }, null);

      assertEquals(3, myBuildTypeRequest.getAgentRequirements(btLocator, "$long,agent-requirement($long)").propEntities.size());
      assertEquals(3, buildType1.getRequirements().size());
      assertFalse(buildType1.isEnabled(disabledId));
    }

    myBuildTypeRequest.replaceAgentRequirements(btLocator, "$long", new PropEntitiesAgentRequirement());
    assertEquals(0, buildType1.getRequirements().size());
  }

  @Test
  public void testUpdatingArtifactDependencies() {
    BuildTypeImpl buildType1 = registerBuildType("buildType1", "projectName");
    BuildTypeImpl buildType2 = registerBuildType("buildType2", "projectName");

    ArtifactDependencyFactory factory = myFixture.findSingletonService(ArtifactDependencyFactory.class);
    assert factory != null;
    SArtifactDependency dep1 = factory.createArtifactDependency(buildType2, "paths1", RevisionRules.LAST_FINISHED_RULE);
    String disabledId = "id2";
    SArtifactDependency dep2 = factory.createArtifactDependency(disabledId, buildType2.getExternalId(), "paths2", RevisionRules.LAST_FINISHED_RULE);
    buildType1.setEnabled(disabledId, false);
    SArtifactDependency dep3 = factory.createArtifactDependency(buildType2, "paths3", RevisionRules.LAST_FINISHED_RULE);
    buildType1.setArtifactDependencies(Arrays.asList(dep1, dep2, dep3));

    final String btLocator = "id:" + buildType1.getExternalId();
    assertEquals(3, myBuildTypeRequest.getArtifactDeps(btLocator, "$long,artifact-dependencies($long)").propEntities.size());
    {
      PropEntityArtifactDep submitted = new PropEntityArtifactDep();
      submitted.type = "artifact_dependency";
      submitted.sourceBuildType = new BuildType();
      submitted.sourceBuildType.setId(buildType2.getExternalId());
      submitted.properties = new Properties();
      submitted.properties.properties = Arrays.asList(new Property("revisionName", "aaa", Fields.LONG),
                                                      new Property("revisionValue", "aaa", Fields.LONG),
                                                      new Property("pathRules", "aaa", Fields.LONG));
      submitted.disabled = true;
      String newId = myBuildTypeRequest.addArtifactDep(btLocator, "$long", submitted).id;
      assertEquals(4, myBuildTypeRequest.getArtifactDeps(btLocator, "$long,artifact-dependencies($long)").propEntities.size());
      assertTrue(myBuildTypeRequest.getArtifactDeps(btLocator, "$long,artifact-dependencies($long)").propEntities.get(3).disabled);
      myBuildTypeRequest.deleteArtifactDep(btLocator, newId);
      assertEquals(3, myBuildTypeRequest.getArtifactDeps(btLocator, "$long,artifact-dependencies($long)").propEntities.size());
    }

    buildType1.getSettings().addListener(new BuildTypeSettingsAdapter() {
      private boolean myAlreadyThrown = false;

      @Override
      public void textValueChanged() {
        if (!myAlreadyThrown) {
          myAlreadyThrown = true;
          throw new RuntimeException("I need error here ");
        }
      }
    });

    {
      PropEntitiesArtifactDep submitted = new PropEntitiesArtifactDep();
      PropEntityArtifactDep submitted1 = new PropEntityArtifactDep();
      submitted1.type = "artifact_dependency";
      submitted1.sourceBuildType = new BuildType();
      submitted1.sourceBuildType.setId(buildType2.getExternalId());
      submitted1.properties = new Properties();
      submitted1.properties.properties = Arrays.asList(new Property("revisionName", "aaa", Fields.LONG),
                                                       new Property("revisionValue", "aaa", Fields.LONG),
                                                       new Property("pathRules", "aaa", Fields.LONG));
      submitted.propEntities = Arrays.asList(submitted1);

      checkException(BadRequestException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.replaceArtifactDeps(btLocator, "$long", submitted);
        }
      }, null);

      assertEquals(3, myBuildTypeRequest.getArtifactDeps(btLocator, "$long,artifact-dependencies($long)").propEntities.size());
      assertEquals(3, buildType1.getArtifactDependencies().size());
      assertFalse(buildType1.isEnabled(disabledId));
    }

    buildType1.getSettings().addListener(new BuildTypeSettingsAdapter() {
      private boolean myAlreadyThrown = false;

      @Override
      public void textValueChanged() {
        if (!myAlreadyThrown) {
          myAlreadyThrown = true;
          throw new RuntimeException("I need error here ");
        }
      }
    });

    {
      PropEntityArtifactDep submitted = new PropEntityArtifactDep();
      submitted.type = "artifact_dependency";
      submitted.sourceBuildType = new BuildType();
      submitted.sourceBuildType.setId(buildType2.getExternalId());
      submitted.properties = new Properties();
      submitted.properties.properties = Arrays.asList(new Property("revisionName", "aaa", Fields.LONG),
                                                      new Property("revisionValue", "aaa", Fields.LONG),
                                                      new Property("pathRules", "aaa", Fields.LONG));
      checkException(BadRequestException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.addArtifactDep(btLocator, "$long", submitted);
        }
      }, null);

      assertEquals(3, myBuildTypeRequest.getArtifactDeps(btLocator, "$long,artifact-dependencies($long)").propEntities.size());
      assertEquals(3, buildType1.getArtifactDependencies().size());
      assertFalse(buildType1.isEnabled(disabledId));
    }

    myBuildTypeRequest.replaceArtifactDeps(btLocator, "$long", new PropEntitiesArtifactDep());
    assertEquals(0, buildType1.getArtifactDependencies().size());
  }

  @Test
  public void testUpdatingSnapshotDependencies() {
    BuildTypeImpl buildType1 = registerBuildType("buildType1", "projectName");
    BuildTypeImpl buildType2 = registerBuildType("buildType2", "projectName");
    BuildTypeImpl buildType3 = registerBuildType("buildType3", "projectName");
    BuildTypeImpl buildType4 = registerBuildType("buildType4", "projectName");
    BuildTypeImpl buildType5 = registerBuildType("buildType5", "projectName");

    DependencyFactory factory = myFixture.findSingletonService(DependencyFactory.class);
    assert factory != null;
    buildType1.addDependency(factory.createDependency(buildType2));
    buildType1.addDependency(factory.createDependency(buildType3));
    buildType1.addDependency(factory.createDependency(buildType4));

    final String btLocator = "id:" + buildType1.getExternalId();
    assertEquals(3, myBuildTypeRequest.getSnapshotDeps(btLocator, "$long,snapshot-dependencies($long)").propEntities.size());
    {
      PropEntitySnapshotDep submitted = new PropEntitySnapshotDep();
      submitted.type = "snapshot_dependency";
      submitted.sourceBuildType = new BuildType();
      submitted.sourceBuildType.setId(buildType5.getExternalId());
      String newId = myBuildTypeRequest.addSnapshotDep(btLocator, "$long", submitted).id;
      assertEquals(4, myBuildTypeRequest.getSnapshotDeps(btLocator, "$long,snapshot-dependencies($long)").propEntities.size());
      myBuildTypeRequest.deleteSnapshotDep(btLocator, newId);
      assertEquals(3, myBuildTypeRequest.getSnapshotDeps(btLocator, "$long,snapshot-dependencies($long)").propEntities.size());
    }

    {
      PropEntitiesSnapshotDep submitted = new PropEntitiesSnapshotDep();
      PropEntitySnapshotDep submitted1 = new PropEntitySnapshotDep();
      submitted1.type = "snapshot_dependency";
      submitted1.sourceBuildType = new BuildType();
      submitted1.sourceBuildType.setId(buildType1.getExternalId());
      submitted.propEntities = Arrays.asList(submitted1);

      checkException(BadRequestException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.replaceSnapshotDeps(btLocator, "$long", submitted);
        }
      }, null);

      assertNotNull(myBuildTypeRequest.getSnapshotDeps(btLocator, "$long,snapshot-dependencies($long)").propEntities);
      assertEquals(3, myBuildTypeRequest.getSnapshotDeps(btLocator, "$long,snapshot-dependencies($long)").propEntities.size());
      assertEquals(3, buildType1.getDependencies().size());
    }

    {
      PropEntitySnapshotDep submitted = new PropEntitySnapshotDep();
      submitted.type = "snapshot_dependency";
      submitted.sourceBuildType = new BuildType();
      submitted.sourceBuildType.setId(buildType1.getExternalId());

      checkException(BadRequestException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.addSnapshotDep(btLocator, "$long", submitted);
        }
      }, null);

      assertNotNull(myBuildTypeRequest.getSnapshotDeps(btLocator, "$long,snapshot-dependencies($long)").propEntities);
      assertEquals(3, myBuildTypeRequest.getSnapshotDeps(btLocator, "$long,snapshot-dependencies($long)").propEntities.size());
      assertEquals(3, buildType1.getDependencies().size());
    }

    myBuildTypeRequest.replaceSnapshotDeps(btLocator, "$long", new PropEntitiesSnapshotDep());
    assertEquals(0, buildType1.getDependencies().size());
  }

  
}
