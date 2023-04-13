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

package jetbrains.buildServer.server.rest.util;

import java.util.*;
import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.dependency.DependencyOptions;
import jetbrains.buildServer.util.Option;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.testng.annotations.Test;

@Test
public class SplitBuildsFeatureUtilTest extends BaseFinderTest {
  public void testParallelBuildDetection() {
    myBuildType.addBuildFeature(new FakeFeatureDescriptor("parallelTests"));

    BuildPromotionEx parallelBuild = myBuildType.createBuildPromotion();
    parallelBuild.setAttribute("teamcity.build.composite", "true");

    Assert.assertTrue(
      "If this failed, this may mean two things:\n" +
      " - there was a breaking change in parallel builds plugin, which breaks the assumptions in rest and those need to be adjusted;\n" +
      " - or, rest itself was broken and needs to be fixed.",
      SplitBuildsFeatureUtil.isParallelizedBuild(parallelBuild)
    );
  }

  public void testMatrixBuildDetection() {
    myBuildType.addBuildFeature(new FakeFeatureDescriptor("matrix"));

    BuildPromotionEx matrixBuild = myBuildType.createBuildPromotion();
    matrixBuild.setAttribute("teamcity.build.composite", "true");

    Assert.assertTrue(
      "If this failed, this may mean two things:\n" +
      " - there was a breaking change in matrix builds plugin, which breaks the assumptions in rest and those need to be adjusted;\n" +
      " - or, rest itself was broken and needs to be fixed.",
      SplitBuildsFeatureUtil.isMatrixBuild(matrixBuild)
    );
  }

  public void testMatrixDepsDetection() {
    myBuildType.addBuildFeature(new FakeFeatureDescriptor("matrix"));

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
      SplitBuildsFeatureUtil.getMatrixDependencies(matrixBuild),
      Arrays.asList(m1, m2)
    );
  }

  public void testMatrixParameters() {
    FakeFeatureDescriptor matrixFeature = new FakeFeatureDescriptor("matrix");
    matrixFeature.getParameters().put("matrix.param.PARAM0", "V0,V1,V2");
    matrixFeature.getParameters().put("matrix.param.PARAM1", "W0,W1");
    matrixFeature.getParameters().put("not_a_name", "XXX");


    myBuildType.addBuildFeature(matrixFeature);

    BuildPromotionEx matrixBuild = myBuildType.createBuildPromotion();
    matrixBuild.setAttribute("teamcity.build.composite", "true");


    List<SplitBuildsFeatureUtil.MatrixParameter> params = SplitBuildsFeatureUtil.getMatrixParameters(matrixBuild);

    Assert.assertEquals(2, params.size());

    Map<String, List<String>> expectedValues = new HashMap<>();
    expectedValues.put("PARAM0", Arrays.asList("V0", "V1", "V2"));
    expectedValues.put("PARAM1", Arrays.asList("W0", "W1"));

    params.forEach(param -> {
      Assert.assertEquals(expectedValues.get(param.getName()), param.getValues());
    });
  }

  public void testMatrixParameterValues() {
    FakeFeatureDescriptor matrixFeature = new FakeFeatureDescriptor("matrix");
    matrixFeature.getParameters().put("matrix.param.PARAM0", "V0,V1");

    myBuildType.addBuildFeature(matrixFeature);

    BuildTypeEx matrixDep1 = myProject.createBuildType("MatrixDep1");
    matrixDep1.addParameter(new SimpleParameter("teamcity.internal.original.link.id", "bt:" + myBuildType.getExternalId()));
    matrixDep1.addParameter(new SimpleParameter("PARAM0", "V0"));
    matrixDep1.addParameter(new SimpleParameter("other", "param"));

    BuildPromotionEx matrixBuild = myBuildType.createBuildPromotion();
    matrixBuild.setAttribute("teamcity.build.composite", "true");

    BuildPromotionEx depBuild = matrixDep1.createBuildPromotion();
    matrixBuild.addDependency(depBuild, FAKE_OPTIONS);

    Map<String, String> resolvedParams = SplitBuildsFeatureUtil.getMatrixParameterResolvedValues(depBuild);

    Assert.assertEquals(1, resolvedParams.size());

    Map.Entry<String, String> param = resolvedParams.entrySet().iterator().next();
    Assert.assertEquals("PARAM0", param.getKey());
    Assert.assertEquals("V0", param.getValue());
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
}
