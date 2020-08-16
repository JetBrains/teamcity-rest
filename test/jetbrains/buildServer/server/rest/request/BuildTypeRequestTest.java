/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import java.util.HashMap;
import java.util.List;
import jetbrains.buildServer.artifacts.RevisionRules;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.model.*;
import jetbrains.buildServer.server.rest.model.buildType.*;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.dependency.DependencyFactory;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.serverSide.impl.MockVcsSupport;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.parameters.ParameterDescriptionFactory;
import jetbrains.buildServer.serverSide.parameters.ParameterFactory;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.util.Util.map;

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
    myBuildTypeRequest.getParametersSubResource(btLocator).setParameter(new Property(new SimpleParameter("a4", "b"), false, Fields.LONG, myFixture), "$long");
    assertEquals(4, myBuildTypeRequest.getParametersSubResource(btLocator).getParameters(null, "$long,property($long)").properties.size());
    myBuildTypeRequest.getParametersSubResource(btLocator).deleteParameter("a3");
    assertEquals(3, myBuildTypeRequest.getParametersSubResource(btLocator).getParameters(null, "$long,property($long)").properties.size());

    {
      Properties submitted = new Properties();
      Property p10 = new Property();
      p10.name = "n1";
      p10.value = null;
      submitted.properties = Arrays.asList(p10);

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
      submitted.properties = Arrays.asList(new Property(new SimpleParameter("n1", "v1"), false, Fields.LONG, myFixture));

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
        myBuildTypeRequest.getParametersSubResource(btLocator).setParameter(new Property(new SimpleParameter("n1", "v1"), false, Fields.LONG, myFixture), "$long");
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

    myBuildTypeRequest.getParametersSubResource(btLocator).setParameter(new Property(new SimpleParameter("a4", "b"), false, Fields.LONG, myFixture), "$long");
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
      submitted.properties = Arrays.asList(new Property(new SimpleParameter("n1", "v1"), false, Fields.LONG, myFixture));

      checkException(BadRequestException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.getParametersSubResource(btLocator).setParameters(submitted, "$long");
        }
      }, null);

      assertEquals(4, buildType1.getParameters().size());
      assertEquals(3, buildType1.getOwnParameters().size());
    }

    buildType1.getSettings().addListener(new BuildTypeSettingsAdapter() {
      private int myTriggerOnCall = 5;  //4 removals and one set

      @Override
      public void textValueChanged() {
        if (--myTriggerOnCall == 0) {
          throw new RuntimeException("I need error here ");
        }
      }
    });

    {
      Properties submitted = new Properties();
      submitted.properties = Arrays.asList(new Property(new SimpleParameter("t1", "new"), false, Fields.LONG, myFixture));

      checkException(BadRequestException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.getParametersSubResource(btLocator).setParameters(submitted, "$long");
        }
      }, null);

      assertEquals(4, buildType1.getParameters().size());
      assertEquals(3, buildType1.getOwnParameters().size());
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
      checkException(BadRequestException.class, new Runnable() {
        public void run() {
          myBuildTypeRequest.getParametersSubResource(btLocator).setParameter(new Property(new SimpleParameter("t1", "new"), false, Fields.LONG, myFixture), "$long");
        }
      }, null);

      assertEquals(4, buildType1.getParameters().size());
      assertEquals(3, buildType1.getOwnParameters().size());
    }

    {
      myBuildTypeRequest.getParametersSubResource(btLocator).setParameter(new Property(new SimpleParameter("t1", "a5"), true, Fields.LONG, myFixture), "$long");

      assertEquals(4, buildType1.getParameters().size());
      assertEquals(3, buildType1.getOwnParameters().size());
    }

    {
      myBuildTypeRequest.getParametersSubResource(btLocator).setParameter(new Property(new SimpleParameter("t1", "a5"), false, Fields.LONG, myFixture), "$long");

      assertEquals(4, buildType1.getParameters().size());
      assertEquals(4, buildType1.getOwnParameters().size());
      buildType1.removeParameter("t1");
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
        myBuildTypeRequest.getParametersSubResource(btLocator).setParameter(new Property(new SimpleParameter("n1", "v1"), false, Fields.LONG, myFixture), "$long");
      }
    }, null);

    assertEquals(4, buildType1.getParameters().size());
    assertEquals(3, buildType1.getOwnParameters().size());

    myBuildTypeRequest.getParametersSubResource(btLocator).setParameters(new Properties(), "$long");
    assertEquals(3, buildType1.getParameters().size());
    assertEquals(0, buildType1.getOwnParameters().size());
  }

  @Test
  public void testParameterTypeResourses() {
    BuildTypeImpl buildType1 = registerTemplateBasedBuildType("buildType1");

    ParameterFactory parameterFactory = myFixture.getSingletonService(ParameterFactory.class);
    ParameterDescriptionFactory parameterDescriptionFactory = myFixture.getSingletonService(ParameterDescriptionFactory.class);
    buildType1.addParameter(parameterFactory.createParameter("a1", "b10", parameterDescriptionFactory.createDescription("cType", CollectionsUtil.asMap("a", "b", "c", "d"))));
    buildType1.addParameter(new SimpleParameter("a2", "b9"));

    final String btLocator = "id:" + buildType1.getExternalId();

    TypedParametersSubResource parametersSubResource = myBuildTypeRequest.getParametersSubResource(btLocator);
    assertEquals(2, parametersSubResource.getParameters(null, "$long,property($long)").properties.size());
    {
      Property parameter = parametersSubResource.getParameter("a1", "$long");
      assertEquals("a1", parameter.name);
      assertEquals("b10", parameter.value);
      assertEquals("cType a='b' c='d'", parameter.type.rawValue);
    }

    {
      ParameterType parameterType = parametersSubResource.getParameterType("a1");
      assertEquals("cType a='b' c='d'", parameterType.rawValue);
    }

    /*
    {
      assertEquals("cType", buildType1.getParameter("a1").getControlDescription().getParameterType());
      assertMap(buildType1.getParameter("a1").getControlDescription().getParameterTypeArguments(), "a", "b", "c", "d");
      ParameterType parameterType = new ParameterType();
      parameterType.rawValue = "cType a='b1' c='d'";
      ParameterType newParameterType = parametersSubResource.setParameterType("a1", parameterType);
      assertEquals("cType a='b1' c='d'", newParameterType.rawValue);
      assertEquals("cType", buildType1.getParameter("a1").getControlDescription().getParameterType());
      assertMap(buildType1.getParameter("a1").getControlDescription().getParameterTypeArguments(), "a", "b1", "c", "d");
    }
    */
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
  public void testUpdatingSteps2() {
    BuildTypeImpl buildType1 = registerBuildType("buildType1", "projectName");

    SBuildRunnerDescriptor buildRunner1 = buildType1.addBuildRunner("name1", "runnerType1", createMap("a", "b"));
    String disabledId = buildType1.addBuildRunner("name2", "runnerType1", createMap("a", "b")).getId();
    buildType1.setEnabled(disabledId, false);
    buildType1.addBuildRunner("name3", "runnerType1", createMap("a", "b"));

    final String btLocator = "id:" + buildType1.getExternalId();

    {
      Properties properties = new Properties();
      Property prop = new Property();
      prop.name = "x";
      //no value
      properties.properties = new ArrayList<>();
      properties.properties.add(prop);
      assertExceptionThrown(() -> myBuildTypeRequest.replaceStepParameters(btLocator, buildRunner1.getId(), properties, "$long"), BadRequestException.class);
    }

    {
      Properties properties = new Properties();
      Property prop = new Property();
      prop.name = "";
      prop.value = "y";
      properties.properties = new ArrayList<>();
      properties.properties.add(prop);
      assertExceptionThrown(() -> myBuildTypeRequest.replaceStepParameters(btLocator, buildRunner1.getId(), properties, "$long"), BadRequestException.class);
    }

    {
      Properties properties = new Properties();
      Property prop = new Property();
      prop.name = "x";
      prop.value = "y";
      properties.properties = new ArrayList<>();
      properties.properties.add(prop);
      assertEquals("b", buildType1.findBuildRunnerById(buildRunner1.getId()).getParameters().get("a"));
      myBuildTypeRequest.replaceStepParameters(btLocator, buildRunner1.getId(), properties, "$long");
      assertEquals("y", buildType1.findBuildRunnerById(buildRunner1.getId()).getParameters().get("x"));
      assertNull(buildType1.findBuildRunnerById(buildRunner1.getId()).getParameters().get("a"));
    }
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
      submitted.properties.properties = Arrays.asList(new Property(new SimpleParameter("property-name", "aaa"), false, Fields.LONG, myFixture));
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
      submitted.properties.properties = Arrays.asList(new Property(new SimpleParameter("revisionName", "aaa"), false, Fields.LONG, myFixture),
                                                      new Property(new SimpleParameter("revisionValue", "aaa"), false, Fields.LONG, myFixture),
                                                      new Property(new SimpleParameter("pathRules", "aaa"), false, Fields.LONG, myFixture));
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
      submitted1.properties.properties = Arrays.asList(new Property(new SimpleParameter("revisionName", "aaa"), false, Fields.LONG, myFixture),
                                                       new Property(new SimpleParameter("revisionValue", "aaa"), false, Fields.LONG, myFixture),
                                                       new Property(new SimpleParameter("pathRules", "aaa"), false, Fields.LONG, myFixture));
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
      submitted.properties.properties = Arrays.asList(new Property(new SimpleParameter("revisionName", "aaa"), false, Fields.LONG, myFixture),
                                                      new Property(new SimpleParameter("revisionValue", "aaa"), false, Fields.LONG, myFixture),
                                                      new Property(new SimpleParameter("pathRules", "aaa"), false, Fields.LONG, myFixture));
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

  @Test
  public void testCreatingWithTemplate() {

    //see also alike setup in BuildTypeTest.testInheritance()
    ProjectEx project10 = createProject("project10", "project 10");
    MockVcsSupport vcs = vcsSupport().withName("vcs").dagBased(true).register();
    final SVcsRoot vcsRoot10 = project10.createVcsRoot("vcs", "extId10", "name10");
    final SVcsRoot vcsRoot20 = project10.createVcsRoot("vcs", "extId20", "name20");
    final SVcsRoot vcsRoot30 = project10.createVcsRoot("vcs", "extId30", "name30");

    project10.addParameter(new SimpleParameter("p", "v"));

    BuildTypeEx bt100 = project10.createBuildType("bt100", "bt 100");
    BuildTypeEx bt110 = project10.createBuildType("bt110", "bt 110");
    BuildTypeEx bt120 = project10.createBuildType("bt120", "bt 120");


    // TEMPLATE
    BuildTypeTemplate t10 = project10.createBuildTypeTemplate("t10", "bt 10");

    t10.setArtifactPaths("aaaaa");
    t10.setBuildNumberPattern("pattern");
    t10.setOption(BuildTypeOptions.BT_ALLOW_EXTERNAL_STATUS, true);
    t10.setOption(BuildTypeOptions.BT_FAIL_IF_TESTS_FAIL, true);
    t10.setOption(BuildTypeOptions.BT_CHECKOUT_DIR, "checkout_t");
    t10.setOption(BuildTypeOptions.BT_CHECKOUT_MODE, "ON_AGENT");
    t10.setOption(BuildTypeOptions.BT_FAIL_ON_ANY_ERROR_MESSAGE, true);
    t10.setOption(BuildTypeOptions.BT_EXECUTION_TIMEOUT, 11);


    t10.addVcsRoot(vcsRoot10);
    t10.addVcsRoot(vcsRoot20);
    t10.setCheckoutRules(vcsRoot20, new CheckoutRules("a=>b"));

    BuildRunnerDescriptorFactory runnerDescriptorFactory = myFixture.getSingletonService(BuildRunnerDescriptorFactory.class);
    t10.addBuildRunner(runnerDescriptorFactory.createBuildRunner(project10, "run10", "name10", "Ant1", map("a", "b")));
    t10.addBuildRunner(runnerDescriptorFactory.createBuildRunner(project10, "run20", "name20", "Ant2", map("a", "b")));

    BuildTriggerDescriptor trigger10 = t10.addBuildTrigger("Type", map("a", "b"));
    BuildTriggerDescriptor trigger20 = t10.addBuildTrigger("Type", map("a", "b"));

    t10.addBuildFeature(myFixture.getBuildFeatureDescriptorFactory().createBuildFeature("f10", "type", map("a", "b")));
    t10.addBuildFeature(myFixture.getBuildFeatureDescriptorFactory().createBuildFeature("f20", "type", map("a", "b")));
    t10.addBuildFeature(myFixture.getBuildFeatureDescriptorFactory().createBuildFeature("f30", "type", map("a", "b")));

    ArtifactDependencyFactory artifactDependencyFactory = myFixture.getSingletonService(ArtifactDependencyFactory.class);
    ArrayList<SArtifactDependency> artifactDeps = new ArrayList<>();
    artifactDeps.add(artifactDependencyFactory.createArtifactDependency("art10", bt100.getExternalId(), "path1", RevisionRules.LAST_PINNED_RULE));
    artifactDeps.add(artifactDependencyFactory.createArtifactDependency("art20", bt100.getExternalId(), "path2", RevisionRules.LAST_PINNED_RULE));
    artifactDeps.add(artifactDependencyFactory.createArtifactDependency("art30", bt100.getExternalId(), "path3", RevisionRules.LAST_PINNED_RULE));
    t10.setArtifactDependencies(artifactDeps);

    t10.addDependency(myFixture.getSingletonService(DependencyFactory.class).createDependency(bt100));
    t10.addDependency(myFixture.getSingletonService(DependencyFactory.class).createDependency(bt110));

    t10.addParameter(new SimpleParameter("a10", "b"));
    t10.addParameter(new SimpleParameter("a20", "b"));
    t10.addParameter(new SimpleParameter("a30", "b"));

    t10.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("req10", "a", null, RequirementType.EXISTS));
    t10.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("req20", "b", null, RequirementType.EXISTS));
    t10.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("req30", "c", null, RequirementType.EXISTS));

    // BUILD TYPE
    BuildTypeEx bt10 = project10.createBuildType("bt10", "bt 10");
    bt10.attachToTemplate(t10);

    bt10.setArtifactPaths("bbbb"); //todo: test w/o override
    bt10.setOption(BuildTypeOptions.BT_ALLOW_EXTERNAL_STATUS, false);
    bt10.setOption(BuildTypeOptions.BT_FAIL_IF_TESTS_FAIL, false);
    { //hack to reproduce case related to https://youtrack.jetbrains.com/issue/TW-45273
//comment until TW-45273 is fixed      t10.setOption(BuildTypeOptions.BT_FAIL_IF_TESTS_FAIL, false);
    }
    bt10.setOption(BuildTypeOptions.BT_CHECKOUT_DIR, "checkout_bt");
    bt10.setOption(BuildTypeOptions.BT_CHECKOUT_MODE, "ON_SERVER");
    bt10.setOption(BuildTypeOptions.BT_EXECUTION_TIMEOUT, 17);

    bt10.addVcsRoot(vcsRoot20);
    bt10.setCheckoutRules(vcsRoot20, new CheckoutRules("x=>y"));
    bt10.addVcsRoot(vcsRoot30);

    bt10.setEnabled("run20", false);
    bt10.addBuildRunner(runnerDescriptorFactory.createBuildRunner(project10, "run30", "name30", "Ant30", map("a", "b")));

    bt10.setEnabled(trigger20.getId(), false);
    BuildTriggerDescriptor trigger30 = bt10.addBuildTrigger("Type", map("a", "b"));

    bt10.setEnabled("f20", false);
    bt10.addBuildFeature(myFixture.getBuildFeatureDescriptorFactory().createBuildFeature("f30", "type_bt", map("a", "b")));
    bt10.addBuildFeature(myFixture.getBuildFeatureDescriptorFactory().createBuildFeature("f40", "type", map("a", "b")));

    ArrayList<SArtifactDependency> artifactDepsBt = new ArrayList<>();
    artifactDepsBt.add(artifactDependencyFactory.createArtifactDependency("art30", bt100.getExternalId(), "path30", RevisionRules.LAST_FINISHED_RULE));
    artifactDepsBt.add(artifactDependencyFactory.createArtifactDependency("art40", bt100.getExternalId(), "path4", RevisionRules.LAST_PINNED_RULE));
    bt10.setArtifactDependencies(artifactDepsBt);
    bt10.setEnabled("art20", false);
    bt10.addDependency(myFixture.getSingletonService(DependencyFactory.class).createDependency(bt110));
    bt10.addDependency(myFixture.getSingletonService(DependencyFactory.class).createDependency(bt120));

    bt10.addParameter(new SimpleParameter("a20", "x"));
    bt10.addParameter(new SimpleParameter("a30", "b"));
    bt10.addParameter(new SimpleParameter("a40", "x"));

    bt10.setEnabled("req20", false);
    bt10.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("req30", "x", null, RequirementType.EQUALS));
    bt10.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("req40", "y", null, RequirementType.EXISTS));

    // NOW, TEST TIME!

    // get buildType
    BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt10), new Fields("$long"), getBeanContext(myServer));

    // post buildType to create new one
    buildType.initializeSubmittedFromUsual();
    buildType.setId("bt10_copy");
    buildType.setName("bt 10 - copy");
    BuildType buildType_copy = myBuildTypeRequest.addBuildType(buildType, Fields.LONG.getFieldsSpec());

    // compare initial and new buildType
    BuildTypeImpl bt10_copy = myFixture.getProjectManager().findBuildTypeByExternalId("bt10_copy");
    assertNotNull(bt10_copy);
    assertNull(BuildTypeUtil.compareBuildTypes(bt10.getSettings(), bt10_copy.getSettings(), true, false));

    //todo:
    //check different settings in submitted from the inherited one, but with inherited flag
    //check submitting with different enabled state
    //check submitting with different id
  }

  @Test
  public void testCreatingWithInheritedParams() {

    //see also alike setup in BuildTypeTest.testInheritance()
    ProjectEx project10 = createProject("project10", "project 10");

    final ParameterFactory parameterFactory = myFixture.getSingletonService(ParameterFactory.class);
    project10.addParameter(parameterFactory.createTypedParameter("a_pwd", "secret", "password"));
    project10.addParameter(new SimpleParameter("b_normal", "value"));

    BuildTypeEx bt10 = project10.createBuildType("bt10", "bt 10");


    // get buildType
    BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt10), new Fields("$long"), getBeanContext(myServer));

    // post buildType to create new one
    buildType.initializeSubmittedFromUsual();
    buildType.setId("bt10_copy");
    buildType.setName("bt 10 - copy");

    {
      BuildType buildType_copy = myBuildTypeRequest.addBuildType(buildType, Fields.LONG.getFieldsSpec());
      BuildTypeImpl bt10_copy = myFixture.getProjectManager().findBuildTypeByExternalId("bt10_copy");
      assertNotNull(bt10_copy);
      assertNull(BuildTypeUtil.compareBuildTypes(bt10.getSettings(), bt10_copy.getSettings(), true, false));

      bt10_copy.remove();
    }

    {
      Properties parameters = buildType.getParameters();
      parameters.properties.get(1).value = null;
      buildType.setParameters(parameters);

      BuildType buildType_copy = myBuildTypeRequest.addBuildType(buildType, Fields.LONG.getFieldsSpec());
      BuildTypeImpl bt10_copy = myFixture.getProjectManager().findBuildTypeByExternalId("bt10_copy");
      assertNotNull(bt10_copy);
      assertNull(BuildTypeUtil.compareBuildTypes(bt10.getSettings(), bt10_copy.getSettings(), true, false));

      bt10_copy.remove();
      buildType.setParameters(buildType.getParameters()); //reset params
    }

    {
      Properties parameters = buildType.getParameters();
      parameters.properties.get(0).type.rawValue = "text";
      buildType.setParameters(parameters);
      checkException(BadRequestException.class, () -> myBuildTypeRequest.addBuildType(buildType, Fields.LONG.getFieldsSpec()), null);
      buildType.setParameters(buildType.getParameters()); //reset params
    }

    {
      {
        Properties parameters = buildType.getParameters();
        parameters.properties.get(0).inherited = null;
        buildType.setParameters(parameters);
      }
      checkException(BadRequestException.class, () -> myBuildTypeRequest.addBuildType(buildType, Fields.LONG.getFieldsSpec()), null);
      {
        Properties parameters = buildType.getParameters();
        parameters.properties.get(0).inherited = false;
        buildType.setParameters(parameters);
      }
      checkException(BadRequestException.class, () -> myBuildTypeRequest.addBuildType(buildType, Fields.LONG.getFieldsSpec()), null);
      buildType.setParameters(buildType.getParameters()); //reset params
    }

    {
      Properties parameters = buildType.getParameters();
      parameters.properties.get(0).value = "secret";
      buildType.setParameters(parameters);

      BuildType buildType_copy = myBuildTypeRequest.addBuildType(buildType, Fields.LONG.getFieldsSpec());
      BuildTypeImpl bt10_copy = myFixture.getProjectManager().findBuildTypeByExternalId("bt10_copy");
      assertNotNull(bt10_copy);
      assertNull(BuildTypeUtil.compareBuildTypes(bt10.getSettings(), bt10_copy.getSettings(), true, false));

      bt10_copy.remove();
      buildType.setParameters(buildType.getParameters()); //reset params
    }

    {
      Properties parameters = buildType.getParameters();
      parameters.properties.get(0).value = "secret2";
      buildType.setParameters(parameters);

      BuildType buildType_copy = myBuildTypeRequest.addBuildType(buildType, Fields.LONG.getFieldsSpec());
      BuildTypeImpl bt10_copy = myFixture.getProjectManager().findBuildTypeByExternalId("bt10_copy");
      assertNotNull(bt10_copy);
      assertNotNull(bt10_copy.getOwnParameter("a_pwd")); // present - another value
      assertEquals("secret2", parameterFactory.getRawValue(bt10_copy.getOwnParameter("a_pwd")));

      bt10_copy.remove();
      buildType.setParameters(buildType.getParameters()); //reset params
    }
  }

  @Test
  public void testCreatingWithDefaultTemplate() {
    ProjectEx project10 = getRootProject().createProject("project10", "project10");
    project10.addParameter(new SimpleParameter("e", "1"));

    BuildTypeTemplate t10 = project10.createBuildTypeTemplate("t10", "t10");
    t10.addParameter(new SimpleParameter("d", "1"));
    t10.addParameter(new SimpleParameter("a", "1"));

    BuildTypeTemplate t20 = project10.createBuildTypeTemplate("t20", "t20");
    t20.addParameter(new SimpleParameter("a", "2"));
    t20.addParameter(new SimpleParameter("b", "1"));

    BuildTypeEx bt10 = project10.createBuildType("bt10", "bt10");
    bt10.addParameter(new SimpleParameter("a", "3"));
    bt10.addParameter(new SimpleParameter("c", "1"));

    project10.setDefaultTemplate(t10);
    bt10.setTemplates(Arrays.asList(t20), false);


    {
      BuildType buildType = myBuildTypeRequest.serveBuildTypeXML("id:" + bt10.getExternalId(), "$long");
      buildType.initializeSubmittedFromUsual();
      buildType.setId("bt20");
      buildType.setName("bt20");
      myBuildTypeRequest.addBuildType(buildType, "$long");
    }

    BuildTypeImpl bt20 = myFixture.getProjectManager().findBuildTypeByExternalId("bt20");

    assertEquals(bt10.getTemplates(), bt20.getTemplates());
    assertEquals(bt10.getOwnTemplates(), bt20.getOwnTemplates());
    assertEquals(bt10.getOwnParameters(), bt20.getOwnParameters());


    bt10.setTemplates(Arrays.asList(t10, t20), false);
    {
      BuildType buildType = myBuildTypeRequest.serveBuildTypeXML("id:" + bt10.getExternalId(), "$long");
      buildType.initializeSubmittedFromUsual();
      buildType.setId("bt30");
      buildType.setName("bt30");
      myBuildTypeRequest.addBuildType(buildType, "$long");
    }
    BuildTypeImpl bt30 = myFixture.getProjectManager().findBuildTypeByExternalId("bt30");
    assertEquals(bt10.getTemplates(), bt30.getTemplates());
    assertEquals(bt10.getOwnTemplates(), bt30.getOwnTemplates());
    assertEquals(bt10.getOwnParameters(), bt30.getOwnParameters());
  }

  @Test
  public void testPasswordParams() {

    //see also alike setup in BuildTypeTest.testInheritance()
    ProjectEx project10 = createProject("project10", "project 10");

    final ParameterFactory parameterFactory = myFixture.getSingletonService(ParameterFactory.class);
    project10.addParameter(parameterFactory.createTypedParameter("a_pwd", "secret", "password"));
    project10.addParameter(new SimpleParameter("b_normal", "value"));

    BuildTypeEx bt10 = project10.createBuildType("bt10", "bt 10");


    // get buildType
    BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt10), new Fields("$long"), getBeanContext(myServer));

    assertNull(buildType.getParameters().properties.get(0).value);

    project10.addParameter(parameterFactory.createTypedParameter("a_pwd", "", "password"));
    assertEquals("", buildType.getParameters().properties.get(0).value);

   }

  @Test
  void testBuildTypeSettings() {
    BuildTypeImpl bt10 = registerBuildType("bt10", "projectName");

    bt10.setArtifactPaths("bbbb");
    bt10.setOption(BuildTypeOptions.BT_ALLOW_EXTERNAL_STATUS, false);
    bt10.setOption(BuildTypeOptions.BT_FAIL_IF_TESTS_FAIL, false);
    bt10.setOption(BuildTypeOptions.BT_CHECKOUT_DIR, "checkout_bt");
    bt10.setOption(BuildTypeOptions.BT_CHECKOUT_MODE, "ON_SERVER");
    bt10.setOption(BuildTypeOptions.BT_EXECUTION_TIMEOUT, 17);

    final String btLocator = "id:" + bt10.getExternalId();

    ParametersSubResource settingsSubResource = myBuildTypeRequest.getSettingsSubResource(btLocator);
    String fields = "$long,settings($long)";
    assertCollectionEquals("", settingsSubResource.getParameters(null, fields), p("artifactRules", "bbbb"), p("buildNumberCounter", "1"),
                           p("checkoutDirectory", "checkout_bt"), p("checkoutMode", "ON_SERVER"),
                           p("executionTimeoutMin", "17"), p("shouldFailBuildIfTestsFailed", "false"));

    assertEquals("0", settingsSubResource.getParameter("maximumNumberOfBuilds", "$long").value);  // default value for direct get

    settingsSubResource.setParameter(new Property(new SimpleParameter("maximumNumberOfBuilds", "4"), false, Fields.LONG, myFixture), "$long");
    assertEquals("4", settingsSubResource.getParameter("maximumNumberOfBuilds", "$long").value);
    assertCollectionEquals("", settingsSubResource.getParameters(null, fields), p("artifactRules", "bbbb"), p("buildNumberCounter", "1"),
                           p("checkoutDirectory", "checkout_bt"), p("checkoutMode", "ON_SERVER"),
                           p("executionTimeoutMin", "17"), p("maximumNumberOfBuilds", "4"), p("shouldFailBuildIfTestsFailed", "false"));

    assertEquals("5", settingsSubResource.setParameter("maximumNumberOfBuilds",
                                                       new Property(new SimpleParameter("maximumNumberOfBuilds", "5"), false, Fields.LONG, myFixture), "$long").value);
    assertEquals("5", settingsSubResource.getParameter("maximumNumberOfBuilds", "$long").value);

    assertEquals("6", settingsSubResource.setParameterValue("maximumNumberOfBuilds", "6"));
    assertEquals("6", settingsSubResource.getParameter("maximumNumberOfBuilds", "$long").value);

    assertEquals("7", settingsSubResource.setParameterValueLong("maximumNumberOfBuilds", "7"));
    assertEquals("7", settingsSubResource.getParameter("maximumNumberOfBuilds", "$long").value);

    settingsSubResource.setParameter(new Property(new SimpleParameter("maximumNumberOfBuilds", "0"), false, Fields.LONG, myFixture), "$long"); //set to default value
    assertCollectionEquals("", settingsSubResource.getParameters(null, fields), p("artifactRules", "bbbb"), p("buildNumberCounter", "1"),
                           p("checkoutDirectory", "checkout_bt"), p("checkoutMode", "ON_SERVER"),
                           p("executionTimeoutMin", "17"), p("shouldFailBuildIfTestsFailed", "false"));

    settingsSubResource.deleteParameter("checkoutDirectory");
    assertCollectionEquals("", settingsSubResource.getParameters(null, fields), p("artifactRules", "bbbb"), p("buildNumberCounter", "1"),
                           p("checkoutMode", "ON_SERVER"),
                           p("executionTimeoutMin", "17"), p("shouldFailBuildIfTestsFailed", "false"));

    assertCollectionEquals("", settingsSubResource.getParameters(new Locator("defaults:any"), fields),
                           p("allowExternalStatus", "false", true, null),
                           p("allowPersonalBuildTriggering", "true", true, null),
                           p("artifactRules", "bbbb", null, null),
                           p("buildNumberCounter", "1", null, null),
                           p("buildNumberPattern", "%build.counter%", true, null),
                           p("checkoutDirectory", "", true, null),
                           p("checkoutMode", "ON_SERVER", null, null),
                           p("cleanBuild", "false", true, null),
                           p("enableHangingBuildsDetection", "true", true, null),
                           p("executionTimeoutMin", "17", null, null),
                           p("maximumNumberOfBuilds", "0", true, null),
                           p("publishArtifactCondition", "NORMALLY_FINISHED", true, null),
                           p("shouldFailBuildIfTestsFailed", "false", null, null),
                           p("shouldFailBuildOnAnyErrorMessage", "false", true, null),
                           p("shouldFailBuildOnBadExitCode", "true", true, null),
                           p("shouldFailBuildOnOOMEOrCrash", "true", true, null),
                           p("showDependenciesChanges", "false", true, null),
                           p("supportTestRetry", "false", true, null),
                           p("vcsLabelingBranchFilter", "+:<default>", true, null),
                           p("excludeDefaultBranchChanges", "false", true, null),
                           p("buildDefaultBranch", "true", true, null),
                           p("branchFilter", "+:*", true, null),
                           p("buildConfigurationType", "REGULAR", true, null));

    assertCollectionEquals("", settingsSubResource.getParameters(new Locator("defaults:true"), fields),
                           p("allowExternalStatus", "false", true, null),
                           p("allowPersonalBuildTriggering", "true", true, null),
                           p("buildNumberPattern", "%build.counter%", true, null),
                           p("checkoutDirectory", "", true, null),
                           p("cleanBuild", "false", true, null),
                           p("enableHangingBuildsDetection", "true", true, null),
                           p("maximumNumberOfBuilds", "0", true, null),
                           p("publishArtifactCondition", "NORMALLY_FINISHED", true, null),
                           p("shouldFailBuildOnAnyErrorMessage", "false", true, null),
                           p("shouldFailBuildOnBadExitCode", "true", true, null),
                           p("shouldFailBuildOnOOMEOrCrash", "true", true, null),
                           p("showDependenciesChanges", "false", true, null),
                           p("supportTestRetry", "false", true, null),
                           p("vcsLabelingBranchFilter", "+:<default>", true, null),
                           p("excludeDefaultBranchChanges", "false", true, null),
                           p("buildDefaultBranch", "true", true, null),
                           p("branchFilter", "+:*", true, null),
                           p("buildConfigurationType", "REGULAR", true, null));

    assertCollectionEquals("", settingsSubResource.getParameters(new Locator("defaults:false"), fields),
                           p("artifactRules", "bbbb", null, null),
                           p("buildNumberCounter", "1", null, null),
                           p("checkoutMode", "ON_SERVER", null, null),
                           p("executionTimeoutMin", "17", null, null),
                           p("shouldFailBuildIfTestsFailed", "false", null, null));

    assertCollectionEquals("", settingsSubResource.getParameters(new Locator("defaults:false,name:buildNumberCounter"), fields),
                           p("buildNumberCounter", "1", null, null));


    HashMap<String, String> newParametersMap = new HashMap<>();
    // all the same
    newParametersMap.put("artifactRules", "bbbb");
    newParametersMap.put("buildNumberCounter", "1");
    newParametersMap.put("checkoutMode", "ON_SERVER");
    newParametersMap.put("executionTimeoutMin", "17");
    newParametersMap.put("shouldFailBuildIfTestsFailed", "false");
    settingsSubResource.setParameters(new Properties(newParametersMap, null, new Fields("**"), getBeanContext(myFixture)), "$long");
    assertCollectionEquals("", settingsSubResource.getParameters(null, fields), p("artifactRules", "bbbb"), p("buildNumberCounter", "1"),
                           p("checkoutMode", "ON_SERVER"),
                           p("executionTimeoutMin", "17"), p("shouldFailBuildIfTestsFailed", "false"));

    newParametersMap.remove("artifactRules");
    newParametersMap.put("buildNumberCounter", "2");
    settingsSubResource.setParameters(new Properties(newParametersMap, null, new Fields("**"), getBeanContext(myFixture)), "$long");
    assertCollectionEquals("", settingsSubResource.getParameters(null, fields), p("buildNumberCounter", "2"),
                           p("checkoutMode", "ON_SERVER"),
                           p("executionTimeoutMin", "17"), p("shouldFailBuildIfTestsFailed", "false"));

    newParametersMap.remove("buildNumberCounter");
    settingsSubResource.setParameters(new Properties(newParametersMap, null, new Fields("**"), getBeanContext(myFixture)), "$long");
    assertCollectionEquals("", settingsSubResource.getParameters(null, fields), p("buildNumberCounter", "1"), //is reset to "1"
                           p("checkoutMode", "ON_SERVER"),
                           p("executionTimeoutMin", "17"), p("shouldFailBuildIfTestsFailed", "false"));

    checkException(LocatorProcessException.class, () -> settingsSubResource.getParameters(new Locator("a:b"), "$long"), null);
    checkException(BadRequestException.class, () -> settingsSubResource.setParameter(new Property(new SimpleParameter("aaa", "b"), false, Fields.LONG, myFixture), "$long"), null);
  }

  @NotNull
  private Property p(final String name, final String value) {
    return p(name, value, null, null);
  }

  @NotNull
  private Property p(final String name, final String value, final Boolean inherited, final String type) {
    if (type == null) {
      return new Property(new SimpleParameter(name, value), inherited, new Fields("$long"), myFixture);
    }
    final ParameterFactory parameterFactory = myFixture.getSingletonService(ParameterFactory.class);
    return new Property(parameterFactory.createTypedParameter(name, value, type), inherited, new Fields("$long"), myFixture);
  }

  public static void assertCollectionEquals(final String description, @Nullable final Properties actual, final Property... expected) {
    BuildTest.assertEquals(description, actual == null ? null : actual.properties, PROPERTY_EQUALS, Property::toString,
                           Property::toString, expected);
  }

  protected static final BuildTest.EqualsTest<Property, Property> PROPERTY_EQUALS = (o1, o2) -> o1.equals(o2);
}
