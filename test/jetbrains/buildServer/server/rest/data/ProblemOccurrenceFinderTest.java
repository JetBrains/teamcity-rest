/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 16/12/2015
 */
public class ProblemOccurrenceFinderTest extends BaseFinderTest<BuildProblem> {

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myProject.remove();

    setFinder(myProblemOccurrenceFinder);
  }

  @Test
  public void testBasic() throws Exception {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType).withProblem(createBuildProblem()).finish();

    checkExceptionOnItemSearch(BadRequestException.class, "No_match");
    checkExceptionOnItemsSearch(BadRequestException.class, "No_match");
    check("build:(id:" + build10.getBuildId() + ")", ((BuildPromotionEx)build10.getBuildPromotion()).getBuildProblems().get(0));
  }
}

