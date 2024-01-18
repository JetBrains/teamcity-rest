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
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.testng.annotations.Test;

@Test
public class SplitBuildsFeatureUtilTest extends BaseServerTestCase {
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
