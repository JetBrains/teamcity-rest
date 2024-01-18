package jetbrains.buildServer.server.rest.data.pages.matrix;

import java.util.*;
import jetbrains.buildServer.server.matrixBuild.MatrixParamsBuildFeature;
import jetbrains.buildServer.server.rest.model.pages.ErrorDescriptor;
import jetbrains.buildServer.server.rest.model.pages.matrix.MatrixParameterDescriptor;
import jetbrains.buildServer.server.rest.model.pages.matrix.MatrixBuildFeatureDescriptor;
import jetbrains.buildServer.server.rest.model.project.LabeledValue;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.dependency.DependencyOptions;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.versionedSettings.ProjectSettingsGeneratorRegistry;
import jetbrains.buildServer.util.Option;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;

@Test
public class MatrixBuildFeatureServiceTest extends BaseServerTestCase {
  private MatrixBuildFeatureService myService;
  private BuildTypeTemplateEx myTemplate;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myService = new MatrixBuildFeatureService(
      myFixture.getBuildFeatureDescriptorFactory(),
      myFixture.getServer(),
      myFixture.getSingletonService(ProjectSettingsGeneratorRegistry.class),
      myFixture.getServer()
    );
    myTemplate = createBuildTypeTemplate("template");
  }

  public void testMatrixBuildDetection() {
    myBuildType.addBuildFeature(new FakeFeatureDescriptor(MatrixParamsBuildFeature.TYPE));

    BuildPromotionEx matrixBuild = myBuildType.createBuildPromotion();
    matrixBuild.setAttribute("teamcity.build.composite", "true");

    Assert.assertTrue("Matrix build detection failed.", MatrixBuildFeatureService.isMatrixBuild(matrixBuild));
  }

  public void testMatrixDepsDetection() {
    myBuildType.addBuildFeature(new FakeFeatureDescriptor(MatrixParamsBuildFeature.TYPE));

    BuildTypeEx matrixDep1 = myProject.createBuildType("MatrixDep1");
    matrixDep1.addParameter(new SimpleParameter("teamcity.internal.original.link.id", "bt:" + myBuildType.getExternalId()));
    BuildTypeEx matrixDep2 = myProject.createBuildType("MatrixDep2");
    matrixDep2.addParameter(new SimpleParameter("teamcity.internal.original.link.id", "bt:" + myBuildType.getExternalId()));

    BuildTypeEx sideDep = myProject.createBuildType("SideDep");

    BuildPromotionEx m1 = matrixDep1.createBuildPromotion();
    BuildPromotionEx m2 = matrixDep2.createBuildPromotion();
    BuildPromotionEx side = sideDep.createBuildPromotion();

    BuildPromotionEx matrixBuild = myBuildType.createBuildPromotion();
    matrixBuild.setAttribute("teamcity.build.composite", "true");
    matrixBuild.addDependency(m1, FAKE_OPTIONS);
    matrixBuild.addDependency(m2, FAKE_OPTIONS);
    matrixBuild.addDependency(side, FAKE_OPTIONS);

    Assert.assertEquals(
      MatrixBuildFeatureService.getDependencies(matrixBuild),
      Arrays.asList(m1, m2)
    );
  }

  public void createsMatrixFeature() {
    List<MatrixParameterDescriptor> parameters = asList(
      new MatrixParameterDescriptor(
        "param",
        asList(new LabeledValue("value1", null), new LabeledValue("value2", null))
      )
    );

    myService.createFeature(new BuildTypeOrTemplate(myBuildType), parameters, true);

    assertFalse("Matrix build feature should be present.", myBuildType.getBuildFeaturesOfType("matrix").isEmpty());
  }

  public void retrievesMatrixFeature() {
    List<MatrixParameterDescriptor> parameters = asList(
      new MatrixParameterDescriptor(
        "param",
        asList(new LabeledValue("value1", null), new LabeledValue("value2", null))
      )
    );

    String featureId = myService.createFeature(new BuildTypeOrTemplate(myBuildType), parameters, true);

    MatrixBuildFeatureDescriptor retrievedParams = myService.resolveParameters(new BuildTypeOrTemplate(myBuildType), featureId);

    assertEquals("Newly created feature should have submitted parameters.", parameters, retrievedParams.getParameters());
  }

  public void updatesMatrixFeature() {
    List<MatrixParameterDescriptor> parameters = asList(
      new MatrixParameterDescriptor(
        "param",
        asList(new LabeledValue("value1", null), new LabeledValue("value2", null))
      )
    );
    String featureId = myService.createFeature(new BuildTypeOrTemplate(myBuildType), parameters, true);

    List<MatrixParameterDescriptor> updatedParameters = asList(
      new MatrixParameterDescriptor(
        "updated_param",
        asList(new LabeledValue("value1", null), new LabeledValue("value2", null))
      ),
      new MatrixParameterDescriptor(
        "updated_param2",
        asList(new LabeledValue("v1", "label1"), new LabeledValue("value2", "label2"))
      )
    );
    myService.updateExistingFeature(new BuildTypeOrTemplate(myBuildType), featureId, updatedParameters, true);

    MatrixBuildFeatureDescriptor retrievedParams = myService.resolveParameters(new BuildTypeOrTemplate(myBuildType), featureId);

    assertEquals("Updated feature should have updated parameters.", updatedParameters, retrievedParams.getParameters());
  }

  public void removesMatrixFeature() {
    List<MatrixParameterDescriptor> parameters = asList(
      new MatrixParameterDescriptor(
        "param",
        asList(new LabeledValue("value1", null), new LabeledValue("value2", null))
      )
    );

    String featureId = myService.createFeature(new BuildTypeOrTemplate(myBuildType), parameters, true);

    assertFalse("Test setup failure: matrix build feature should be present.", myBuildType.getBuildFeaturesOfType("matrix").isEmpty());

    myService.removeFeature(new BuildTypeOrTemplate(myBuildType), featureId, true);
    assertTrue("Matrix build feature should be removed.", myBuildType.getBuildFeaturesOfType("matrix").isEmpty());
  }

  public void createsMatrixFeatureInTemplate() {
    List<MatrixParameterDescriptor> parameters = asList(
      new MatrixParameterDescriptor(
        "param",
        asList(new LabeledValue("value1", null), new LabeledValue("value2", null))
      )
    );

    myService.createFeature(new BuildTypeOrTemplate(myTemplate), parameters, true);

    assertFalse("Matrix build feature should be present.", myTemplate.getBuildFeaturesOfType("matrix").isEmpty());
  }

  public void retrievesMatrixFeatureFromTemplate() {
    List<MatrixParameterDescriptor> parameters = asList(
      new MatrixParameterDescriptor(
        "param",
        asList(new LabeledValue("value1", null), new LabeledValue("value2", null))
      )
    );

    String featureId = myService.createFeature(new BuildTypeOrTemplate(myTemplate), parameters, true);

    MatrixBuildFeatureDescriptor retrievedParams = myService.resolveParameters(new BuildTypeOrTemplate(myTemplate), featureId);

    assertEquals("Newly created feature should have submitted parameters.", parameters, retrievedParams.getParameters());
  }

  public void updatesMatrixFeatureInTemplate() {
    List<MatrixParameterDescriptor> parameters = asList(
      new MatrixParameterDescriptor(
        "param",
        asList(new LabeledValue("value1", null), new LabeledValue("value2", null))
      )
    );
    String featureId = myService.createFeature(new BuildTypeOrTemplate(myTemplate), parameters, true);

    List<MatrixParameterDescriptor> updatedParameters = asList(
      new MatrixParameterDescriptor(
        "updated_param",
        asList(new LabeledValue("value1", null), new LabeledValue("value2", null))
      ),
      new MatrixParameterDescriptor(
        "updated_param2",
        asList(new LabeledValue("v1", "label1"), new LabeledValue("value2", "label2"))
      )
    );
    myService.updateExistingFeature(new BuildTypeOrTemplate(myTemplate), featureId, updatedParameters, true);

    MatrixBuildFeatureDescriptor retrievedParams = myService.resolveParameters(new BuildTypeOrTemplate(myTemplate), featureId);

    assertEquals("Updated feature should have updated parameters.", updatedParameters, retrievedParams.getParameters());
  }

  public void overridesInheritedFeature() {
    List<MatrixParameterDescriptor> parameters = asList(
      new MatrixParameterDescriptor(
        "param",
        asList(new LabeledValue("value1", null), new LabeledValue("value2", null))
      )
    );
    String featureId = myService.createFeature(new BuildTypeOrTemplate(myTemplate), parameters, true);

    SBuildType bt = myProject.createBuildTypeFromTemplate(myTemplate, "BT_from_tmeplate", new CopyOptions());
    bt.persist();

    MatrixBuildFeatureDescriptor inheritedParams = myService.resolveParameters(new BuildTypeOrTemplate(bt), featureId);
    assertEquals("Test setup failure: inherited feature should have params from template", parameters, inheritedParams.getParameters());

    List<MatrixParameterDescriptor> updatedParameters = asList(
      new MatrixParameterDescriptor(
        "updated_param",
        asList(new LabeledValue("value1", null), new LabeledValue("value2", null))
      )
    );
    myService.updateExistingFeature(new BuildTypeOrTemplate(bt), featureId, updatedParameters, true);

    MatrixBuildFeatureDescriptor paramsAfterUpdate = myService.resolveParameters(new BuildTypeOrTemplate(bt), featureId);
    assertEquals("Inherited feature should have updated parameters.", updatedParameters, paramsAfterUpdate.getParameters());
    assertEquals("Inherited feature should have same id as in template.", featureId, paramsAfterUpdate.getId());
  }

  public void removesMatrixFeatureFromTemplate() {
    List<MatrixParameterDescriptor> parameters = asList(
      new MatrixParameterDescriptor(
        "param",
        asList(new LabeledValue("value1", null), new LabeledValue("value2", null))
      )
    );

    String featureId = myService.createFeature(new BuildTypeOrTemplate(myTemplate), parameters, true);

    assertFalse("Test setup failure: matrix build feature should be present.", myTemplate.getBuildFeaturesOfType("matrix").isEmpty());

    myService.removeFeature(new BuildTypeOrTemplate(myTemplate), featureId, true);
    assertTrue("Matrix build feature should be removed.", myTemplate.getBuildFeaturesOfType("matrix").isEmpty());
  }

  public void mustValidateParameterName() {
    List<MatrixParameterDescriptor> invalidParameters = asList(
      new MatrixParameterDescriptor(
        "Invalid param with a space",
        asList(new LabeledValue("value1", null), new LabeledValue("value2", null))
      )
    );

    List<ErrorDescriptor> errors = myService.validateParameters(invalidParameters);

    assertEquals("Invalid parameter name must be reported.", 1, errors.size());
    assertEquals("Correct error location must be reported.", "parameter[0].name", errors.get(0).getLocation());
  }

  public void mustValidateParametersList() {
    List<MatrixParameterDescriptor> invalidParameters = Collections.emptyList();

    List<ErrorDescriptor> errors = myService.validateParameters(invalidParameters);

    assertEquals("Invalid parameter list must be reported.", 1, errors.size());
    assertEquals("Correct error location (empty string) must be reported.", "", errors.get(0).getLocation());
  }

  public void mustValidateParameterValueUniqueness() {
    List<MatrixParameterDescriptor> invalidParameters = asList(
      new MatrixParameterDescriptor(
        "valid_name",
        asList(new LabeledValue("value", null), new LabeledValue("value", null))
      )
    );

    List<ErrorDescriptor> errors = myService.validateParameters(invalidParameters);

    assertEquals("Invalid value must be reported.", 1, errors.size());
    assertEquals("Correct error location must be reported.", "parameter[0].value[1]", errors.get(0).getLocation());
  }

  public void mustValidateParameterLabelUniqueness() {
    List<MatrixParameterDescriptor> invalidParameters = asList(
      new MatrixParameterDescriptor(
        "valid_name",
        asList(new LabeledValue("value1", "label"), new LabeledValue("value2", "label"))
      )
    );

    List<ErrorDescriptor> errors = myService.validateParameters(invalidParameters);

    assertEquals("Invalid label must be reported.", 1, errors.size());
    assertEquals("Correct error location must be reported.", "parameter[0].value[1]", errors.get(0).getLocation());
  }

  public void emptyParameterValueMustHaveLabel() {
    List<MatrixParameterDescriptor> invalidParameters = asList(
      new MatrixParameterDescriptor(
        "valid_name",
        asList(new LabeledValue("", ""))
      )
    );

    List<ErrorDescriptor> errors = myService.validateParameters(invalidParameters);

    assertEquals("Missing label must be reported.", 1, errors.size());
    assertEquals("Correct error location must be reported.", "parameter[0].value[0]", errors.get(0).getLocation());
  }

  public void mustValidateParameterValueCount() {
    List<MatrixParameterDescriptor> invalidParameters = asList(
      new MatrixParameterDescriptor(
        "valid_name",
        Collections.emptyList()
      )
    );

    List<ErrorDescriptor> errors = myService.validateParameters(invalidParameters);

    assertEquals("Invalid value count error must be reported.", 1, errors.size());
    assertEquals("Correct error location must be reported.", "parameter[0]", errors.get(0).getLocation());
  }

  private final class FakeFeatureDescriptor implements SBuildFeatureDescriptor {
    private final String myType;
    private final Map<String, String> myParameters = new HashMap<>();
    public FakeFeatureDescriptor(@NotNull String type) {
      myType = type;
    }

    @NotNull
    @Override
    public String getId() {
      return myType;
    }

    @NotNull
    @Override
    public String getType() {
      return myType;
    }

    @NotNull
    @Override
    public Map<String, String> getParameters() {
      return myParameters;
    }

    @NotNull
    @Override
    public BuildFeature getBuildFeature() {
      return new BuildFeature() {
        @NotNull
        @Override
        public String getType() {
          return myType;
        }

        @NotNull
        @Override
        public String getDisplayName() {
          return "Fake Feature";
        }

        @Nullable
        @Override
        public String getEditParametersUrl() {
          return null;
        }
      };
    }
  }

  private final DependencyOptions FAKE_OPTIONS = new DependencyOptions() {
    @Override
    @NotNull
    public Object getOption(@NotNull final Option option) {
      return new Object();
    }

    @Override
    public <T> void setOption(@NotNull final Option<T> option, @NotNull final T value) {
    }

    @Override
    @NotNull
    public Collection<Option> getOwnOptions() {
      return Collections.emptyList();
    }

    @Override
    @NotNull
    public Collection<Option> getOptions() {
      return Collections.emptyList();
    }

    @Override
    @NotNull
    public Option[] getChangedOptions() {
      return new Option[0];
    }

    @Override
    @NotNull
    public <T> T getOptionDefaultValue(@NotNull final Option<T> option) {
      return option.getDefaultValue();
    }

    @Nullable
    @Override
    public <T> T getDeclaredOption(final Option<T> option) {
      return null;
    }
  };
}
